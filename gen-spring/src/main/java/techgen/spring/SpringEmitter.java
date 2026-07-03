package techgen.spring;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import techgen.core.errors.UnsupportedConstruct;
import techgen.core.gm.GenerationModel;
import techgen.core.gm.GmOperation;
import techgen.core.model.Abac;
import techgen.core.model.BoundaryOpJson;
import techgen.core.model.CallEdgeJson;
import techgen.core.model.Deployable;
import techgen.core.model.EntityFieldJson;
import techgen.core.model.EntityJson;
import techgen.core.model.ErrorJson;
import techgen.core.model.EventJson;
import techgen.core.model.ExprNode;
import techgen.core.model.ExternalJson;
import techgen.core.model.ExtJson;
import techgen.core.model.FieldJson;
import techgen.core.model.GuardedExpr;
import techgen.core.model.ModuleDecl;
import techgen.core.model.OperationJson;
import techgen.core.model.ParamJson;
import techgen.core.model.ServingArg;
import techgen.core.model.ServingJson;
import techgen.core.model.SubscriptionJson;
import techgen.core.model.TypeJson;
import techgen.core.model.UnchartedEntity;
import techgen.core.model.UnchartedJson;
import techgen.core.model.UnchartedType;
import techgen.core.predicate.ExprWalk;
import techgen.core.report.BuildReport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * gen-spring emisyon orkestrasyonu (davranış sözleşmesi §6.1-6.2; referans
 * {@code docs/referans/gen-dotnet-emisyon-sozlesmesi.md} §1/§6; CoreTemplate1
 * {@code DotnetEmitter.cs} karşılığı, Java/Maven hedefine uyarlanmış).
 *
 * <p>T3.2 iskeleti: kök HumanShell'ler (pom.xml/Application.java/application.yml), üreteç-sahibi
 * parent POM ({@code gen/parent/pom.xml}), {@code GeneratedBootstrap} + modül başına {@code Wiring}.
 * Entity/type/op/endpoint emisyonu sonraki task'larda (T3.3+) modül döngüsüne eklenir.</p>
 */
public final class SpringEmitter {

    private SpringEmitter() {
    }

    /**
     * Emit sırası (davranış sözleşmesi §6.1): kök HumanShell dosyaları (writeIfAbsent) → global
     * üreteç-sahibi dosyalar (writeAlways) → modül döngüsü (writeAlways; sonraki task'lar bölüm
     * ekler) → {@link EmitWriter#finishAndPrune()}.
     */
    public static void emit(GenerationModel gm, Path outDir, BuildReport report, GenConfig config) throws IOException {
        EmitWriter writer = new EmitWriter(outDir, Versions.APP_VERSION);

        // ── kök HumanShell'ler (writeIfAbsent — insan-sahipli, asla ezilmez) ──
        writer.writeIfAbsent("pom.xml", humanPom());
        writer.writeIfAbsent("src/main/java/app/Application.java", applicationJava());
        writer.writeIfAbsent("src/main/resources/application.yml", applicationYml(config));

        // ── global üreteç-sahibi dosyalar (writeAlways) ──
        writer.writeAlways("gen/parent/pom.xml", parentPom(config, report));
        writer.writeAlways("gen/java/app/GeneratedBootstrap.java", generatedBootstrap(gm, report));

        // ── Result taksonomisi + ResultHttp (SPEC §6.3 satırı; INV-5 kapalı taksonomi; T3.3) ──
        writer.writeAlways("gen/java/app/Result.java", resultInterfaceJava());
        writer.writeAlways("gen/java/app/Success.java", successRecordJava());
        writer.writeAlways("gen/java/app/NotAuthenticated.java", notAuthenticatedRecordJava());
        writer.writeAlways("gen/java/app/NotAuthorized.java", notAuthorizedRecordJava());
        writer.writeAlways("gen/java/app/NotValid.java", notValidRecordJava());
        writer.writeAlways("gen/java/app/NotProcessable.java", notProcessableRecordJava());
        writer.writeAlways("gen/java/app/ServerError.java", serverErrorRecordJava());
        writer.writeAlways("gen/java/app/Page.java", pageRecordJava());
        writer.writeAlways("gen/java/app/Unit.java", unitRecordJava());
        writer.writeAlways("gen/java/app/ResultHttp.java", resultHttpJava());
        if (!gm.events().isEmpty()) {
            writer.writeAlways("gen/java/app/EventBus.java", eventBusJava());
        }
        // T4.1 — herhangi op idempotent!=null ise IdempotencyStore seam'i (referans §1 Idempotency.g.cs).
        boolean anyIdempotent = gm.operations().stream().anyMatch(o -> o.op().idempotent() != null);
        if (anyIdempotent) {
            writer.writeAlways("gen/java/app/IdempotencyStore.java", idempotencyStoreJava());
        }

        // ── PersistenceConfig (T3.4 §5.2; SPEC §6.3 AppDbContext.g.cs satırı) — yalnız entities>0 ──
        if (!gm.entities().isEmpty()) {
            writer.writeAlways("gen/java/app/PersistenceConfig.java", persistenceConfigJava());
        }

        // ── DeploymentTopology (T4.5 Step 5.2; referans §1 HostFile `:789-824`) — yalnız deployables>0.
        // modular-monolith host: her deployable, units (modüller) TEK host process'te barındırır. ──
        if (!gm.deployables().isEmpty()) {
            writer.writeAlways("gen/java/app/DeploymentTopology.java", deploymentTopologyJava(gm, report));
        }

        // ── Boundary externals (T4.4; SPEC §6.3 Boundary.g.cs satırı) — external başına {Ext}
        // arayüzü (gen-owned) + human {Ext}Client seam (writeIfAbsent) + (validation varsa)
        // caller-side {Ext}{Op}Validation. calls/compensate saga yorumları + policy'leri T4.5. ──
        for (ExternalJson ext : gm.externals()) {
            writer.writeAlways("gen/java/app/boundary/" + ext.name() + ".java",
                    boundaryFile(ext, report, gm.callEdges()));
            writer.writeIfAbsent("src/main/java/app/boundary/" + ext.name() + "Client.java",
                    boundaryClientJava(ext));
            for (BoundaryOpJson b : ext.operations()) {
                if (b.validation() != null && !b.validation().isEmpty()) {
                    writer.writeAlways(
                            "gen/java/app/boundary/" + ext.name() + Naming.pascal(b.id()) + "Validation.java",
                            boundaryValidationJava("app.boundary", ext.name(), b, report));
                }
            }
        }

        // ── Uncharted (T4.4; SPEC §6.3 Uncharted/{Name}.g.cs satırı) — gen-owned stub interface +
        // {Name}StubClient (human seam DEĞİL) + OWNED entity/type POJO'ları (JPA'ya bağlanmaz) +
        // (validation varsa) caller-side {Name}{Op}Validation. ──
        for (UnchartedJson u : gm.uncharted()) {
            writer.writeAlways("gen/java/app/uncharted/" + u.name() + ".java",
                    unchartedFile(u, report, gm.callEdges()));
            for (UnchartedEntity e : u.entities()) {
                writer.writeAlways("gen/java/app/uncharted/" + u.name() + e.id() + ".java",
                        unchartedEntityJava(u, e, report));
            }
            for (UnchartedType t : u.types()) {
                writer.writeAlways("gen/java/app/uncharted/" + u.name() + t.id() + ".java",
                        unchartedTypeJava(u, t));
            }
            for (BoundaryOpJson b : u.operations()) {
                if (b.validation() != null && !b.validation().isEmpty()) {
                    writer.writeAlways(
                            "gen/java/app/uncharted/" + u.name() + Naming.pascal(b.id()) + "Validation.java",
                            boundaryValidationJava("app.uncharted", u.name(), b, report));
                }
            }
        }

        // ── modül döngüsü (sonraki task'lar bu döngüye op/entity emisyonu ekleyecek) ──
        for (ModuleDecl module : gm.modules()) {
            String pkg = Naming.packageOf(module.name());
            String cls = Naming.pascal(module.name()) + "Wiring";

            // ── ModulePrelude (T4.5 Step 5.4; module-ext, .NET Module.g.cs paritesi) — yalnız module.ext()>0 ──
            if (module.ext() != null && !module.ext().isEmpty()) {
                writer.writeAlways("gen/java/app/" + pkg + "/ModulePrelude.java", modulePreludeJava(module, report));
            }

            // ── Errors katalogu (T3.3 §5.3) ──
            List<ErrorJson> moduleErrors = gm.errors().stream()
                    .filter(e -> e.module().equals(module.name()))
                    .toList();
            if (!moduleErrors.isEmpty()) {
                writer.writeAlways("gen/java/app/" + pkg + "/Errors.java",
                        errorsJava(module.name(), moduleErrors, report));
            }

            // ── Types (enum/composite, T3.3 §5.4) ──
            for (TypeJson type : gm.types()) {
                if (!type.module().equals(module.name())) {
                    continue;
                }
                writer.writeAlways("gen/java/app/" + pkg + "/" + type.id() + ".java", typeJava(type, report));
            }

            // ── Events (T3.3 §5.4) ──
            for (EventJson event : gm.events()) {
                if (!event.module().equals(module.name())) {
                    continue;
                }
                writer.writeAlways("gen/java/app/" + pkg + "/" + event.id() + ".java", eventJava(event, report));
            }

            // ── Entities → @Entity + JpaRepository (T3.4 §5.1-5.2) + Invariants (T3.5 §5.3) ──
            for (EntityJson entity : gm.entities()) {
                if (!entity.module().equals(module.name())) {
                    continue;
                }
                writer.writeAlways("gen/java/app/" + pkg + "/" + entity.id() + ".java",
                        entityJava(entity, gm, report));
                writer.writeAlways("gen/java/app/" + pkg + "/" + entity.id() + "Repository.java",
                        repositoryJava(entity));

                // ── Invariants (T3.5 §5.3) — invariants varsa {Entity}Invariants.java ──
                String invariants = invariantsJava(entity, gm, report);
                if (invariants != null) {
                    writer.writeAlways("gen/java/app/" + pkg + "/" + entity.id() + "Invariants.java", invariants);
                }
            }

            // ── Guards (T3.5 §5.2) — validation/rule/permit varsa {op}/{Op}Guards.java ──
            for (GmOperation op : gm.operations()) {
                if (!op.module().equals(module.name())) {
                    continue;
                }
                String guards = guardsJava(op, gm, report);
                if (guards != null) {
                    String slice = op.id().toLowerCase(java.util.Locale.ROOT);
                    writer.writeAlways("gen/java/app/" + pkg + "/" + slice + "/" + op.id() + "Guards.java", guards);
                }
            }

            // ── Operation slice (T3.6 §5.1-5.5; SPEC §6.2-6.4) — request record + HandlerBase
            // (Generation Gap, gen-owned) + human seam ({Op}Handler, writeIfAbsent) + Endpoint
            // (visibility!=internal ∧ rest serving var) — Wiring bean kayıtları bu döngüde
            // biriktirilir, dosya döngü SONUNDA yazılır (bean içerikleri önceden bilinmeli). ──
            Set<String> wiringImports = new TreeSet<>();
            StringBuilder wiringBeans = new StringBuilder();
            boolean anyBean = false;
            for (GmOperation op : gm.operations()) {
                if (!op.module().equals(module.name())) {
                    continue;
                }
                String opId = op.id();
                String opSlug = opId.toLowerCase(Locale.ROOT);
                String slicePkg = Naming.slicePackage(op.module(), opId);
                String genSliceDir = "gen/java/app/" + pkg + "/" + opSlug;
                String humanSliceDir = "src/main/java/app/" + pkg + "/" + opSlug;
                String reqName = opId + (op.isCommand() ? "Command" : "Query");
                String baseRetType = Naming.javaType(op.op().signature().returns(), false);
                boolean paginated = op.op().pagination() != null;
                // pagination varsa dönüş tipi Result<Page<{Ret}>> (T4.1; referans §1/§7 "pagination").
                String retType = paginated ? "Page<" + baseRetType + ">" : baseRetType;
                boolean idempotent = op.op().idempotent() != null;
                List<RepoDep> deps = repoDeps(op, gm);

                // Step 5.1 — request record.
                writer.writeAlways(genSliceDir + "/" + reqName + ".java",
                        requestRecordJava(op, gm, slicePkg, reqName, report));

                // Step 5.2 — HandlerBase (abstract, gen-owned) + T3.7 Auth/Throws/Consistency/Ext.
                writer.writeAlways(genSliceDir + "/" + opId + "HandlerBase.java",
                        handlerBaseJava(op, gm, slicePkg, reqName, retType, deps, report));

                // Step 5.3 — human seam (writeIfAbsent); brownfield migrasyon önce.
                Path oldFlat = outDir.resolve("src/main/java/app/" + pkg + "/" + opId + "Handler.java");
                Path newSlice = outDir.resolve(humanSliceDir + "/" + opId + "Handler.java");
                writer.migrateSeamIfFlat(oldFlat, newSlice);
                writer.writeIfAbsent(humanSliceDir + "/" + opId + "Handler.java",
                        humanHandlerJava(op, gm, slicePkg, reqName, retType, deps));

                // Step 5.4 — Endpoint: internal görünürlük VEYA rest-serving yoksa emit edilmez.
                boolean hasRest = op.op().serving().stream().anyMatch(s -> "rest".equals(s.protocol()));
                boolean emitEndpoint = !"internal".equals(op.op().visibility()) && hasRest;
                if (emitEndpoint) {
                    writer.writeAlways(genSliceDir + "/" + opId + "Endpoint.java",
                            endpointJava(op, slicePkg, reqName));
                }
                // T4.5 Step 5.3 — serving süpürmesi (dosya emisyonu kapsamından BAĞIMSIZ): rest ->
                // realized; non-rest (grpc/queue/...) -> explicit report.unsupported (DROP DEĞİL, INV-7
                // unsupported≠drop). Fixture'da CreateInvoice @grpc() bunun canlı örneği.
                for (ServingJson s : op.op().serving()) {
                    if ("rest".equals(s.protocol())) {
                        report.realized("serving", opId + ":" + s.protocol());
                    } else {
                        report.unsupported("serving", opId + ":" + s.protocol(), "REST-only binding");
                        report.policy("serving-" + s.protocol(), "unsupported: REST-only binding (generator-policy)");
                    }
                }

                // Step 5.5 — Wiring bean kayıtları (TAM-AÇIK KAYIT, SPEC §12/4) biriktir.
                List<String> depParamList = new ArrayList<>();
                List<String> depArgList = new ArrayList<>();
                for (RepoDep d : deps) {
                    depParamList.add(d.entityId() + "Repository " + d.fieldName());
                    depArgList.add(d.fieldName());
                }
                // T4.1 — idempotent op: {op}Handler bean'i ayrıca IdempotencyStore alır (ctor-senkron;
                // bean GeneratedBootstrap'ta EXPLICIT @Bean — Spring context-genelinde çözülür).
                if (idempotent) {
                    depParamList.add("IdempotencyStore idempotencyStore");
                    depArgList.add("idempotencyStore");
                    wiringImports.add("import app.IdempotencyStore;");
                }
                // T4.2 — emits!=[] op: {op}Handler bean'i ayrıca EventBus alır (ctor-senkron;
                // idempotent'ten SONRA; bean GeneratedBootstrap'ta EXPLICIT @Bean — Spring
                // context-genelinde çözülür).
                boolean emits = !op.op().emits().isEmpty();
                if (emits) {
                    depParamList.add("EventBus eventBus");
                    depArgList.add("eventBus");
                    wiringImports.add("import app.EventBus;");
                }
                // T4.4 — bu op bir external çağırıyorsa ({@code callEdges.from==opId && kind=="external"}):
                // {op}Handler bean'i ayrıca {Ext} client alır (ctor-senkron; idempotent+events'ten
                // SONRA, EN SONA — M4 ctor-threading kuralı: [repos, idempotent, events, boundary]).
                for (ExternalJson boundaryExt : boundaryDepsForOp(op, gm)) {
                    String extField = Naming.camel(boundaryExt.name());
                    depParamList.add(boundaryExt.name() + " " + extField);
                    depArgList.add(extField);
                    wiringImports.add("import app.boundary." + boundaryExt.name() + ";");
                }
                String depParams = String.join(", ", depParamList);
                String depArgs = String.join(", ", depArgList);
                for (RepoDep d : deps) {
                    if (!d.module().equals(module.name())) {
                        wiringImports.add("import app." + Naming.packageOf(d.module()) + "." + d.entityId()
                                + "Repository;");
                    }
                }
                wiringImports.add("import app." + pkg + "." + opSlug + "." + opId + "Handler;");
                String camelOp = Naming.camel(opId);
                wiringBeans.append("    @Bean\n");
                wiringBeans.append("    public ").append(opId).append("Handler ").append(camelOp)
                        .append("Handler(").append(depParams).append(") {\n");
                wiringBeans.append("        return new ").append(opId).append("Handler(").append(depArgs)
                        .append(");\n");
                wiringBeans.append("    }\n\n");
                anyBean = true;
                if (emitEndpoint) {
                    wiringImports.add("import app." + pkg + "." + opSlug + "." + opId + "Endpoint;");
                    wiringBeans.append("    @Bean\n");
                    wiringBeans.append("    public ").append(opId).append("Endpoint ").append(camelOp)
                            .append("Endpoint(").append(opId).append("Handler h) {\n");
                    wiringBeans.append("        return new ").append(opId).append("Endpoint(h);\n");
                    wiringBeans.append("    }\n\n");
                }

                // Step 5.6 — T4.3: @trigger.{name} ext → SmartLifecycle stub (Generation Gap:
                // {Op}{T}TriggerBase gen-owned + human seam {Op}{T}Trigger, writeIfAbsent) + Wiring
                // EXPLICIT @Bean (referans §1 {@code {Op}.Trigger.g.cs} `:1105-1123`). realized
                // ("@trigger.{name}", opId) T3.7 ext döngüsünde (extFields) ZATEN yapılıyor —
                // burada TEKRARLANMAZ (rule 9: çift-entry önlenir).
                List<ExtJson> triggers = op.op().ext() == null ? List.of()
                        : op.op().ext().stream().filter(e -> "trigger".equals(e.ns())).toList();
                if (!triggers.isEmpty()) {
                    report.policy("trigger-wiring", "SmartLifecycle stub (generator-policy)");
                    for (ExtJson t : triggers) {
                        String triggerCls = opId + Naming.pascal(t.name()) + "Trigger";
                        writer.writeAlways(genSliceDir + "/" + triggerCls + "Base.java",
                                triggerBaseJava(slicePkg, opId, triggerCls, t));
                        writer.writeIfAbsent(humanSliceDir + "/" + triggerCls + ".java",
                                triggerLogicJava(slicePkg, opId, triggerCls));
                        wiringImports.add("import " + slicePkg + "." + triggerCls + ";");
                        String camelTrigger = Naming.camel(triggerCls);
                        wiringBeans.append("    @Bean\n");
                        wiringBeans.append("    public ").append(triggerCls).append(' ').append(camelTrigger)
                                .append('(').append(opId).append("Handler h) {\n");
                        wiringBeans.append("        return new ").append(triggerCls).append("(h);\n");
                        wiringBeans.append("    }\n\n");
                        anyBean = true;
                    }
                }
            }
            writer.writeAlways("gen/java/app/" + pkg + "/" + cls + ".java",
                    moduleWiring(module, wiringImports, wiringBeans.toString(), anyBean));
        }

        // ── Subscriptions (T4.2 §5.2-5.3; SPEC §6.3 Subscriptions.g.cs satırı; referans §1
        // `:1348-1398`/`:39-48`) — yalnız subscriptions>0. CONSUMER op'un MODÜL SLICE'INDA yaşar
        // (event'in modülünde DEĞİL — T4.2 §2 Why: yanlış modüle emit = census kapanır ama
        // davranış yanlış olur / silent-fail). ──
        if (!gm.subscriptions().isEmpty()) {
            for (SubscriptionJson s : gm.subscriptions()) {
                String consumerModule = s.consumer().module();
                String consumerOp = s.consumer().op();
                String consumerPkg = Naming.packageOf(consumerModule);
                String consumerSlug = consumerOp.toLowerCase(Locale.ROOT);
                String cls = s.event().name() + "To" + consumerOp + "Consumer";
                String genConsumerDir = "gen/java/app/" + consumerPkg + "/" + consumerSlug;
                String humanConsumerDir = "src/main/java/app/" + consumerPkg + "/" + consumerSlug;

                writer.writeAlways(genConsumerDir + "/" + cls + "Base.java", subscriptionConsumerBaseJava(s));
                writer.writeIfAbsent(humanConsumerDir + "/" + cls + ".java",
                        subscriptionConsumerLogicJava(s, report));
            }
            writer.writeAlways("gen/java/app/Subscriptions.java", subscriptionsRootJava(gm));
        }

        writer.finishAndPrune();
    }

    // ── HumanShell: yoksa-üret, asla ezilmez. Pipeline/bean-kaydı sırası insan-kontrolünde DEĞİL —
    // tam-açık kayıt (SPEC §12/4): component-scan YOK, tüm bean'ler Bootstrap/Wiring'den gelir. ──

    private static String humanPom() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- bu dosya bir kez üretilir; insan sahiplidir — elle düzenleyebilirsiniz -->
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>

                  <parent>
                    <groupId>app.gen</groupId>
                    <artifactId>generated-parent</artifactId>
                    <version>%s</version>
                    <relativePath>gen/parent/pom.xml</relativePath>
                  </parent>

                  <groupId>app</groupId>
                  <artifactId>app</artifactId>
                  <version>%s</version>
                  <packaging>jar</packaging>

                  <build>
                    <plugins>
                      <!-- build-helper konfigürasyonu parent'ın pluginManagement'ından miras alınır -->
                      <plugin>
                        <groupId>org.codehaus.mojo</groupId>
                        <artifactId>build-helper-maven-plugin</artifactId>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """.formatted(Versions.APP_VERSION, Versions.APP_VERSION);
    }

    private static String applicationJava() {
        return """
                package app;

                // component-scan kapalıdır; kendi bean'lerinizi burada @Bean/@Import ile kaydedin
                // ya da bilinçli olarak @ComponentScan ekleyin.

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
                import org.springframework.context.annotation.Import;

                @EnableAutoConfiguration
                @Import(GeneratedBootstrap.class)
                public class Application {

                    public static void main(String[] args) {
                        SpringApplication.run(Application.class, args);
                    }
                }
                """;
    }

    private static String applicationYml(GenConfig config) {
        String provider = config == null ? null : config.dbProvider();
        String datasourceComment = switch (provider == null ? "" : provider) {
            case "postgres" ->
                "# dbProvider=postgres -> spring.datasource.url=jdbc:postgresql://<host>:5432/<db>\n";
            case "sqlserver" ->
                "# dbProvider=sqlserver -> spring.datasource.url=jdbc:sqlserver://<host>:1433;databaseName=<db>\n";
            case "h2" -> "# dbProvider=h2 -> spring.datasource.url=jdbc:h2:mem:app\n";
            case "inmemory" -> "# dbProvider=inmemory -> spring.datasource.url=jdbc:h2:mem:app (H2 in-memory)\n";
            default -> "# dbProvider tanımlı değil: datasource seam — gen.config.json'a dbProvider ekleyin\n";
        };
        return """
                spring:
                  application:
                    name: app

                %s""".formatted(datasourceComment);
    }

    // ── Generated: üreteç-sahibi parent POM (Generated.props'un Maven karşılığı, SPEC §6.1). ──

    private static String parentPom(GenConfig config, BuildReport report) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- AUTO-GENERATED by techgen-spring — elle DÜZENLEME: her üretimde ezilir -->
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>

                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>%s</version>
                    <relativePath/>
                  </parent>

                  <groupId>app.gen</groupId>
                  <artifactId>generated-parent</artifactId>
                  <version>%s</version>
                  <packaging>pom</packaging>

                  <properties>
                    <java.version>21</java.version>
                  </properties>

                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-data-jpa</artifactId>
                    </dependency>
                %s\
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-test</artifactId>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>

                  <build>
                    <pluginManagement>
                      <plugins>
                        <plugin>
                          <groupId>org.codehaus.mojo</groupId>
                          <artifactId>build-helper-maven-plugin</artifactId>
                          <version>%s</version>
                          <executions>
                            <execution>
                              <id>add-gen-sources</id>
                              <phase>generate-sources</phase>
                              <goals>
                                <goal>add-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${project.basedir}/gen/java</source>
                                </sources>
                              </configuration>
                            </execution>
                            <execution>
                              <id>add-gen-test-sources</id>
                              <phase>generate-test-sources</phase>
                              <goals>
                                <goal>add-test-source</goal>
                              </goals>
                              <configuration>
                                <sources>
                                  <source>${project.basedir}/gen/test-java</source>
                                </sources>
                              </configuration>
                            </execution>
                          </executions>
                        </plugin>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-compiler-plugin</artifactId>
                          <configuration>
                            <release>21</release>
                          </configuration>
                        </plugin>
                      </plugins>
                    </pluginManagement>
                  </build>
                </project>
                """.formatted(Versions.SPRING_BOOT, Versions.APP_VERSION, providerDependencyXml(config, report),
                Versions.BUILD_HELPER_PLUGIN);
    }

    /**
     * dbProvider whitelist → provider driver bağımlılığı (SPEC §6.6). Whitelist içi →
     * {@code report.realized("dbProvider", value)} (.NET {@code DotnetEmitter.cs:1606} paritesi —
     * POLICY DEĞİL realized; {@code dbProvider} census construct'ı olmadığından bu entry gate'i
     * etkilemez, sadece bilgilendirir). null → seam, rapor entry'si YOK. Whitelist dışı →
     * {@code Unsupported} + seam davranışı.
     */
    private static String providerDependencyXml(GenConfig config, BuildReport report) {
        String provider = config == null ? null : config.dbProvider();
        if (provider == null) {
            return "    <!-- dbProvider tanımlı değil: provider seam — gen.config.json'a dbProvider ekleyin -->\n";
        }
        return switch (provider) {
            case "h2", "inmemory" -> {
                report.realized("dbProvider", provider);
                yield """
                        <dependency>
                          <groupId>com.h2database</groupId>
                          <artifactId>h2</artifactId>
                          <scope>runtime</scope>
                        </dependency>
                    """;
            }
            case "postgres" -> {
                report.realized("dbProvider", provider);
                yield """
                        <dependency>
                          <groupId>org.postgresql</groupId>
                          <artifactId>postgresql</artifactId>
                          <scope>runtime</scope>
                        </dependency>
                    """;
            }
            case "sqlserver" -> {
                report.realized("dbProvider", provider);
                yield """
                        <dependency>
                          <groupId>com.microsoft.sqlserver</groupId>
                          <artifactId>mssql-jdbc</artifactId>
                          <scope>runtime</scope>
                        </dependency>
                    """;
            }
            default -> {
                report.unsupported("dbProvider", provider, "whitelist dışı; postgres/sqlserver/h2/inmemory");
                yield "    <!-- dbProvider '" + provider + "' whitelist dışı: driver eklenmedi -->\n";
            }
        };
    }

    // ── Generated: GeneratedBootstrap + modül Wiring (SPEC §6.1/§12/4 tam-açık bean kaydı). ──

    private static String generatedBootstrap(GenerationModel gm, BuildReport report) {
        StringBuilder imports = new StringBuilder();
        StringBuilder importedClasses = new StringBuilder();
        for (ModuleDecl module : gm.modules()) {
            String pkg = Naming.packageOf(module.name());
            String cls = Naming.pascal(module.name()) + "Wiring";
            imports.append("import app.").append(pkg).append('.').append(cls).append(";\n");
            if (!importedClasses.isEmpty()) {
                importedClasses.append(", ");
            }
            importedClasses.append(cls).append(".class");
            report.realized("module", module.name());
        }
        // entities>0 → PersistenceConfig kaydı (aynı paket "app" — import satırı gerekmez).
        if (!gm.entities().isEmpty()) {
            if (!importedClasses.isEmpty()) {
                importedClasses.append(", ");
            }
            importedClasses.append("PersistenceConfig.class");
        }
        // subscriptions>0 → kök Subscriptions kaydı (aynı paket "app" — import satırı gerekmez;
        // T4.2; SPEC §6.3 Subscriptions.g.cs satırı).
        if (!gm.subscriptions().isEmpty()) {
            if (!importedClasses.isEmpty()) {
                importedClasses.append(", ");
            }
            importedClasses.append("Subscriptions.class");
        }
        // events>0 → EventBus bean kaydı (.NET Bootstrap.g.cs `AddScoped<IEventBus, OutboxEventBus>` paritesi).
        boolean eventBusBean = !gm.events().isEmpty();
        // T4.1 — herhangi op idempotent!=null → IdempotencyStore EXPLICIT @Bean (component-scan YOK,
        // tasks/_uyumluluk-raporu-2026-07-03.md §7 bağlayıcı; dedup-store=in-memory).
        boolean idempotencyStoreBean = gm.operations().stream().anyMatch(o -> o.op().idempotent() != null);
        // T4.4 — external başına {Ext}Client EXPLICIT @Bean (Wiring/Bootstrap tam-açık kayıt;
        // component-scan YOK; SPEC §12/4). Kullanılıp kullanılmadığına bakılmaksızın her external kaydedilir.
        boolean anyExternal = !gm.externals().isEmpty();
        StringBuilder externalImports = new StringBuilder();
        StringBuilder externalBeans = new StringBuilder();
        for (ExternalJson ext : gm.externals()) {
            externalImports.append("import app.boundary.").append(ext.name()).append(";\n");
            externalImports.append("import app.boundary.").append(ext.name()).append("Client;\n");
            String camel = Naming.camel(ext.name());
            externalBeans.append("\n    @Bean\n");
            externalBeans.append("    public ").append(ext.name()).append(' ').append(camel).append("Client() {\n");
            externalBeans.append("        return new ").append(ext.name()).append("Client();\n");
            externalBeans.append("    }\n");
        }
        imports.append(externalImports);
        String beanImport = (eventBusBean || idempotencyStoreBean || anyExternal)
                ? "import org.springframework.context.annotation.Bean;\n" : "";
        String eventBusMethod = eventBusBean
                ? """

                        @Bean
                        public EventBus eventBus() {
                            return new OutboxEventBus();
                        }
                    """
                : "";
        String idempotencyStoreMethod = idempotencyStoreBean
                ? """

                        @Bean
                        public IdempotencyStore idempotencyStore() {
                            return new InMemoryIdempotencyStore();
                        }
                    """
                : "";
        eventBusMethod = eventBusMethod + idempotencyStoreMethod + externalBeans;
        return """
                package app;

                import org.springframework.context.annotation.Configuration;
                import org.springframework.context.annotation.Import;
                %s%s
                @Configuration
                @Import({%s})
                public class GeneratedBootstrap {
                %s}
                """.formatted(beanImport, imports, importedClasses, eventBusMethod);
    }

    /**
     * Modül Wiring (T3.6 §5.5; SPEC §12/4 TAM-AÇIK KAYIT) — op başına {@code {Op}Handler} bean'i
     * (insan sınıfı, gen-owned config'ten kaydedilir) + varsa {@code {Op}Endpoint} bean'i
     * (controller da açık @Bean ile; component-scan YOK). {@code opImports}/{@code beanMethods}
     * çağıran döngüde biriktirilir (ordinal-sıralı import seti; determinizm).
     */
    private static String moduleWiring(ModuleDecl module, Set<String> opImports, String beanMethods,
            boolean anyBean) {
        String pkg = Naming.packageOf(module.name());
        String cls = Naming.pascal(module.name()) + "Wiring";
        String beanImport = anyBean ? "import org.springframework.context.annotation.Bean;\n" : "";
        StringBuilder importsBlock = new StringBuilder();
        for (String imp : opImports) {
            importsBlock.append(imp).append('\n');
        }
        String body = anyBean ? beanMethods : "    // op kayıtları: bu modülde op yok\n";
        return """
                package app.%s;

                %simport org.springframework.context.annotation.Configuration;
                %s
                @Configuration
                public class %s {
                %s}
                """.formatted(pkg, beanImport, importsBlock, cls, body);
    }

    /**
     * T4.5 Step 5.4 — {@code gen/java/app/{module}/ModulePrelude.java}: module-level cross-cutting
     * ext'ler (referans §1 {@code Module.g.cs} `:84-86` paritesi) — yalnız {@code module.ext()>0}
     * (çağıran taraf guard'lar). Bean/DI YOK — yorum-dosyası (realizasyon = §8 policy).
     */
    private static String modulePreludeJava(ModuleDecl module, BuildReport report) {
        String pkg = Naming.packageOf(module.name());
        String extComment = typeLevelExtComment(module.ext(), module.name(), report);
        return "package app." + pkg + ";\n\n"
                + "// module-level cross-cutting prelude'lar (realizasyon = §8 policy).\n"
                + extComment;
    }

    // ── Operation slice (T3.6 §5.1-5.4; SPEC §6.2-6.4; referans §1 `:1466-1513`/`:1663-1685`) —
    // request record + HandlerBase (Generation Gap abstract) + human seam + Endpoint. Op-slice
    // paketi (app.{module}.{op}) her zaman modül-kök paketinden (app.{module}) FARKLIDIR — bu yüzden
    // entity/repository/Result gibi modül-kök tipleri BURADA her zaman import edilir. ──

    /** Bir op'un DI'a taşıdığı repository bağımlılığı (entity id + sahip modülü + alan adı). */
    private record RepoDep(String entityId, String module, String fieldName) {
    }

    private static EntityJson findEntity(GenerationModel gm, String entityId) {
        for (EntityJson e : gm.entities()) {
            if (e.id().equals(entityId)) {
                return e;
            }
        }
        return null;
    }

    /** {@code access.reads∪creates∪updates∪deletes} entity'leri, ordinal-distinct (task §5.2). */
    private static List<String> accessEntitiesOrdinalDistinct(GmOperation op) {
        var access = op.op().access();
        Set<String> set = new TreeSet<>();
        set.addAll(access.reads());
        set.addAll(access.creates());
        set.addAll(access.updates());
        set.addAll(access.deletes());
        return new ArrayList<>(set);
    }

    /** Ordinal-distinct access entity'lerinden HandlerBase/Handler/Wiring'in paylaştığı repo bağımlılık listesi. */
    private static List<RepoDep> repoDeps(GmOperation op, GenerationModel gm) {
        List<RepoDep> deps = new ArrayList<>();
        for (String entityId : accessEntitiesOrdinalDistinct(op)) {
            EntityJson e = findEntity(gm, entityId);
            if (e != null) {
                deps.add(new RepoDep(entityId, e.module(), Naming.camel(entityId) + "Repository"));
            }
        }
        return deps;
    }

    /** Bir manifest tip adı GM'de entity/type/event id'sine eşleşiyorsa cross-package import satırı; yoksa null. */
    private static String customTypeImport(String manifestType, GenerationModel gm) {
        // T6.3-FIX #1 — Result<Unit> dönüşü: Unit kök "app" paketinde (unitRecordJava), op-slice
        // paketinden (app.{module}.{op}) HER ZAMAN farklı → Result ile aynı şekilde cross-package
        // import gerekir (manifest'te entity/type/event olarak tanımlı değil, built-in sentinel).
        if ("Unit".equals(manifestType)) {
            return "import app.Unit;\n";
        }
        for (EntityJson e : gm.entities()) {
            if (e.id().equals(manifestType)) {
                return "import app." + Naming.packageOf(e.module()) + "." + e.id() + ";\n";
            }
        }
        for (TypeJson t : gm.types()) {
            if (t.id().equals(manifestType)) {
                return "import app." + Naming.packageOf(t.module()) + "." + t.id() + ";\n";
            }
        }
        for (EventJson ev : gm.events()) {
            if (ev.id().equals(manifestType)) {
                return "import app." + Naming.packageOf(ev.module()) + "." + ev.id() + ";\n";
            }
        }
        return null;
    }

    /** Java tip adlarına göre gereken java.* import satırları ({@link #typeFieldImports} ile aynı sabit sıra). */
    private static String paramImports(List<String> javaTypes) {
        boolean bigDecimal = false;
        boolean instant = false;
        boolean localDate = false;
        boolean duration = false;
        boolean list = false;
        for (String t : javaTypes) {
            switch (t) {
                case "BigDecimal" -> bigDecimal = true;
                case "Instant" -> instant = true;
                case "LocalDate" -> localDate = true;
                case "Duration" -> duration = true;
                default -> {
                    if (t.startsWith("List<")) {
                        list = true;
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        if (bigDecimal) {
            sb.append("import java.math.BigDecimal;\n");
        }
        if (instant) {
            sb.append("import java.time.Instant;\n");
        }
        if (localDate) {
            sb.append("import java.time.LocalDate;\n");
        }
        if (duration) {
            sb.append("import java.time.Duration;\n");
        }
        if (list) {
            sb.append("import java.util.List;\n");
        }
        return sb.toString();
    }

    /** {@code {Entity}Repository} import satırları, ordinal (repoDeps sırası — entity id ordinal). */
    private static String repoImportsBlock(List<RepoDep> deps) {
        StringBuilder sb = new StringBuilder();
        for (RepoDep d : deps) {
            sb.append("import app.").append(Naming.packageOf(d.module())).append('.').append(d.entityId())
                    .append("Repository;\n");
        }
        return sb.toString();
    }

    private static String escapeJavadoc(String s) {
        return s.replace("*/", "*&#47;");
    }

    /**
     * Step 5.1 — request record ({@code {Op}Command}/{@code {Op}Query}): signature.params →
     * Naming.javaType bileşenleri; visibility yorumu; note varsa record-üstü Javadoc.
     * {@code realized("operation"/"visibility")} burada (HandlerBase ile birlikte bir kez).
     */
    private static String requestRecordJava(GmOperation op, GenerationModel gm, String slicePkg, String reqName,
            BuildReport report) {
        OperationJson o = op.op();
        List<ParamJson> params = o.signature().params();
        List<String> javaTypes = new ArrayList<>();
        List<String> components = new ArrayList<>();
        Set<String> customImports = new TreeSet<>();
        // T4.5 Step 5.4 — op signature param-ext (owner "{opId}.{param}"; census addExt op.signature.params).
        StringBuilder paramExtComments = new StringBuilder();
        for (ParamJson p : params) {
            String javaType = Naming.javaType(p.type(), p.collection());
            javaTypes.add(javaType);
            String custom = customTypeImport(p.type(), gm);
            if (custom != null) {
                customImports.add(custom);
            }
            components.add(javaType + " " + Naming.camel(p.name()));
            String extLine = typeLevelExtComment(p.ext(), op.id() + "." + p.name(), report);
            if (!extLine.isEmpty()) {
                paramExtComments.append("// ").append(Naming.pascal(p.name())).append(": ")
                        .append(extLine.replaceFirst("^// ", ""));
            }
        }
        String stdImports = paramImports(javaTypes);
        String customImportsBlock = String.join("", customImports);

        // T4.1 — pagination varsa istek kayda opak cursor (veya offset) + size eklenir (referans §1 Page;
        // .NET RequestFields paritesi — kodlama = generator-policy, §8).
        var pg = o.pagination();
        String pageComment = "";
        if (pg != null) {
            if ("offset".equals(pg.strategy())) {
                components.add("Integer offset");
            } else {
                components.add("String cursor");
            }
            components.add("int size");
            String keys = pg.keys().stream()
                    .map(k -> Naming.pascal(k.field()) + " " + k.direction())
                    .collect(Collectors.joining(", "));
            pageComment = "// pagination: " + pg.strategy() + " (size=" + (pg.size() == null ? "—" : pg.size())
                    + ") ORDER BY " + keys + " (keyset/offset + cursor-token = generator-policy)\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(slicePkg).append(";\n\n");
        sb.append(stdImports);
        sb.append(customImportsBlock);
        if (!stdImports.isEmpty() || !customImportsBlock.isEmpty()) {
            sb.append('\n');
        }
        sb.append(pageComment);
        sb.append(paramExtComments);
        sb.append("// visibility: ").append(o.visibility()).append('\n');
        if (o.note() != null) {
            sb.append("/**\n * ").append(escapeJavadoc(o.note())).append("\n */\n");
            report.realized("note", op.id());
        }
        sb.append("public record ").append(reqName).append('(').append(String.join(", ", components))
                .append(") {\n}\n");

        report.realized("operation", op.id());
        report.realized("visibility", op.id());
        // T4.5 Step 5.3 — visibility policy (bir kez; TreeMap dedup): exposed->route emit edilir, internal->emit edilmez.
        report.policy("visibility",
                "internal".equals(o.visibility()) ? "internal-no-route" : "exposed-route (generator-policy)");
        return sb.toString();
    }

    /**
     * Step 5.2 — HandlerBase (Generation Gap, gen-owned, abstract): DI alanları (repository'ler) +
     * ctor + gövdesiz {@code execute}. Guards çağrı sırası (skill canonicalOrder, SPEC §6.3/§10)
     * yalnız Javadoc referansı — çağrı T3.6 dışı. T3.7 tamamlayıcı bölümler ekler: Auth (roles/
     * scopes/ownership), Throws (THROWABLE_ERRORS + tipli fabrikalar), Consistency, Ext.
     */
    private static String handlerBaseJava(GmOperation op, GenerationModel gm, String slicePkg, String reqName,
            String retType, List<RepoDep> deps, BuildReport report) {
        String opId = op.id();
        OperationJson o = op.op();
        boolean idempotent = o.idempotent() != null;
        boolean paginated = o.pagination() != null;
        boolean emits = !o.emits().isEmpty();
        StringBuilder imports = new StringBuilder("import app.Result;\n");
        String retImport = customTypeImport(o.signature().returns(), gm);
        imports.append(repoImportsBlock(deps));
        if (retImport != null) {
            imports.append(retImport);
        }
        if (idempotent) {
            imports.append("import app.IdempotencyStore;\n");
        }
        if (paginated) {
            imports.append("import app.Page;\n");
        }
        if (emits) {
            imports.append("import app.EventBus;\n");
        }

        StringBuilder fields = new StringBuilder();
        List<String> ctorParams = new ArrayList<>();
        StringBuilder ctorAssigns = new StringBuilder();
        for (RepoDep d : deps) {
            fields.append("    protected final ").append(d.entityId()).append("Repository ")
                    .append(d.fieldName()).append(";\n");
            ctorParams.add(d.entityId() + "Repository " + d.fieldName());
            ctorAssigns.append("        this.").append(d.fieldName()).append(" = ").append(d.fieldName())
                    .append(";\n");
        }
        // T4.1 — idempotent op: DI'a IdempotencyStore eklenir (ctor-senkron; Wiring bean çağrısı da günceldir).
        if (idempotent) {
            fields.append("    protected final IdempotencyStore idempotencyStore;\n");
            ctorParams.add("IdempotencyStore idempotencyStore");
            ctorAssigns.append("        this.idempotencyStore = idempotencyStore;\n");
        }
        // T4.2 — emits!=[] op: DI'a EventBus eklenir (ctor-senkron; idempotent'ten SONRA; Wiring
        // bean çağrısı da günceldir; referans §1 EventBus.g.cs + SPEC §6.3 Events.g.cs/EventBus.g.cs
        // satırı). Publish çağrısı iş gövdesinde (insan seam) — burada yalnız DI erişimi sağlanır.
        if (emits) {
            for (String ev : o.emits()) {
                report.realized("emits", opId + "->" + ev);
            }
            fields.append("    // emits: ").append(String.join(", ", o.emits()))
                    .append(" (publish gövdesi iş mantığında; EventBus.publish çağrısı insan seam sorumluluğu)\n");
            fields.append("    protected final EventBus eventBus;\n");
            ctorParams.add("EventBus eventBus");
            ctorAssigns.append("        this.eventBus = eventBus;\n");
        }
        // T4.4 — bu op bir external çağırıyorsa (callEdges.from==opId && kind=="external"): DI'a
        // {Ext} client eklenir (ctor-senkron; idempotent+events'ten SONRA, EN SONA — M4
        // ctor-threading kuralı: [repos, idempotent, events, boundary]).
        for (ExternalJson boundaryExt : boundaryDepsForOp(op, gm)) {
            String extField = Naming.camel(boundaryExt.name());
            imports.append("import app.boundary.").append(boundaryExt.name()).append(";\n");
            fields.append("    protected final ").append(boundaryExt.name()).append(' ').append(extField)
                    .append(";\n");
            ctorParams.add(boundaryExt.name() + " " + extField);
            ctorAssigns.append("        this.").append(extField).append(" = ").append(extField).append(";\n");
        }

        // T3.7 — Auth (roles/scopes/ownership varsa).
        String authSection = authFields(o, opId, report);

        // T3.7 — Throws (op.throws() varsa): THROWABLE_ERRORS + tipli fabrikalar.
        ThrowsBlock throwsBlock = throwsBlock(op, gm, retType, report);

        // T3.7 — Consistency (mode≠null || risk==eventual).
        String consistencySection = consistencyFields(o, opId, report);

        // T3.7 — Ext (op.ext() varsa): prelude + tanınan sabitler + (@http varsa) http-binding policy.
        String extSection = extFields(op, report);

        // T4.1 — Idempotency (idempotent!=null) + Pagination (pagination!=null) (referans §1 Idem/Page + §7).
        String idempotencySection = idempotencyFields(o, opId, report);
        String paginationSection = paginationFields(o, opId, report);

        // T3.7 ek importları — ordinal-sorted (TreeSet), dedup (aynı Result alt-tipi birden fazla throws'ta olabilir).
        Set<String> extraImports = new TreeSet<>();
        if (!authSection.isEmpty() || !idempotencySection.isEmpty()) {
            extraImports.add("import java.util.List;\n");
        }
        if (throwsBlock != null) {
            extraImports.addAll(throwsBlock.imports());
        }
        for (String imp : extraImports) {
            imports.append(imp);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(slicePkg).append(";\n\n");
        sb.append(imports);
        sb.append('\n');
        sb.append("/**\n");
        sb.append(" * Generation Gap taban sınıf (abstract; SPEC §6.2) — DI alanları + Auth/Throws/\n");
        sb.append(" * Consistency/Ext bölümleri burada (T3.7); sonraki task'lar (idempotency/pagination/\n");
        sb.append(" * external/event/vb.) kendi bölümlerini ekler.\n");
        sb.append(" * Guard çağrı sırası kanonik (skill canonicalOrder, çağrı T3.6 dışı — yalnız referans):\n");
        sb.append(" * idempotency -&gt; authz -&gt; validation -&gt; external-input -&gt; rule -&gt;\n");
        sb.append(" * entity+invariant -&gt; persist -&gt; emit -&gt; return.\n");
        sb.append(" */\n");
        sb.append("public abstract class ").append(opId).append("HandlerBase {\n\n");
        sb.append(fields);
        if (!deps.isEmpty()) {
            sb.append('\n');
        }
        sb.append(authSection);
        if (throwsBlock != null) {
            sb.append(throwsBlock.fieldSection());
        }
        sb.append(consistencySection);
        sb.append(extSection);
        sb.append(idempotencySection);
        sb.append(paginationSection);
        sb.append("    protected ").append(opId).append("HandlerBase(").append(String.join(", ", ctorParams))
                .append(") {\n");
        sb.append(ctorAssigns);
        sb.append("    }\n\n");
        if (throwsBlock != null) {
            sb.append(throwsBlock.methodsSection());
        }
        sb.append("    public abstract Result<").append(retType).append("> execute(").append(reqName)
                .append(" request);\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ── T3.7 — HandlerBase tamamlayıcıları: Auth / Throws / Consistency / Ext (SPEC §6.3; referans
    // §1 `:1401-1427`/`:844-873`/`:887-910`/`:1142-1177` + §7). Java'da partial class yok — .NET'te
    // ayrı `.g.cs` dosyaları olan bu bölümler burada aynı {@code {Op}HandlerBase} gövdesine EKLENİR.
    // `note` realized T3.6 Step 5.1'de yapılır; BURADA TEKRAR EDİLMEZ (çift entry olmasın). ──

    /**
     * Auth bölümü (referans §1 `:1401-1427`) — roles/scopes/ownership'ten HERHANGİ biri varsa
     * ÜÇÜ BİRDEN emit edilir (boş liste/{@code null} olsa da). {@code report.realized} her biri
     * için BAĞIMSIZ koşulludur (yalnız o alan doluysa).
     */
    private static String authFields(OperationJson o, String opId, BuildReport report) {
        if (o.roles().isEmpty() && o.scopes().isEmpty() && o.ownership() == null) {
            return "";
        }
        if (!o.roles().isEmpty()) {
            report.realized("roles", opId);
        }
        if (!o.scopes().isEmpty()) {
            report.realized("scopes", opId);
        }
        if (o.ownership() != null) {
            report.realized("ownership", opId);
        }
        String roles = o.roles().stream().map(r -> "\"" + escapeJava(r) + "\"").collect(Collectors.joining(", "));
        String scopes = o.scopes().stream().map(s -> "\"" + escapeJava(s) + "\"").collect(Collectors.joining(", "));
        String ownership = o.ownership() == null ? "null" : "\"" + escapeJava(o.ownership()) + "\"";
        StringBuilder sb = new StringBuilder();
        sb.append("    // authz iskeleti (roles+scopes AND, ownership row-level). ponytail: meta + seam;\n");
        sb.append("    // gerçek claim/row-level kontrol insan/runtime.\n");
        sb.append("    public static final List<String> REQUIRED_ROLES = List.of(").append(roles).append(");\n");
        sb.append("    public static final List<String> REQUIRED_SCOPES = List.of(").append(scopes).append(");\n");
        sb.append("    public static final String OWNERSHIP = ").append(ownership).append(";\n\n");
        return sb.toString();
    }

    /** Throws bölümü sonucu — alan (array) + fabrika metotları + gereken ek importlar (T3.7). */
    private record ThrowsBlock(String fieldSection, String methodsSection, Set<String> imports) {
    }

    /** {@code op.throws()} boşsa null (dosyaya bölüm eklenmez). */
    private static ThrowsBlock throwsBlock(GmOperation op, GenerationModel gm, String retType, BuildReport report) {
        List<String> ids = op.op().throwsList();
        if (ids.isEmpty()) {
            return null;
        }
        String module = op.module();
        List<String> consts = new ArrayList<>();
        List<String> factories = new ArrayList<>();
        Set<String> imports = new TreeSet<>();
        for (String id : ids) {
            report.realized("throws", op.id() + "->" + id);
            ErrorJson err = findError(gm, id);
            String errModule = err != null ? err.module() : module;
            String resultType = err != null ? err.resultType() : "NotProcessable";
            String codeRef;
            if (errModule.equals(module)) {
                codeRef = "Errors." + id;
                imports.add("import app." + Naming.packageOf(module) + ".Errors;\n");
            } else {
                codeRef = "app." + Naming.packageOf(errModule) + ".Errors." + id;
            }
            consts.add(codeRef);
            imports.add(throwFactoryImport(resultType));
            if ("NotValid".equals(resultType)) {
                imports.add("import java.util.Map;\n");
            }
            factories.add(throwFactoryMethod(resultType, retType, Naming.camel(id), codeRef));
        }
        String field = "    // throws: op'un atabileceği adlı-hatalar -> tipli Result fabrikaları (iş gövdesi çağırır).\n"
                + "    public static final String[] THROWABLE_ERRORS = {" + String.join(", ", consts) + "};\n\n";
        return new ThrowsBlock(field, String.join("", factories), imports);
    }

    /** {@code gm.errors()} içinde id eşleşmesi; bulunamazsa null (tanınmayan → NotProcessable varsayımı). */
    private static ErrorJson findError(GenerationModel gm, String id) {
        for (ErrorJson e : gm.errors()) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    /** resultType → gereken {@code app.*} Result alt-tip importu (referans §1 Result taksonomisi). */
    private static String throwFactoryImport(String resultType) {
        return switch (resultType) {
            case "NotValid" -> "import app.NotValid;\n";
            case "NotAuthorized" -> "import app.NotAuthorized;\n";
            case "NotAuthenticated" -> "import app.NotAuthenticated;\n";
            case "ServerError" -> "import app.ServerError;\n";
            default -> "import app.NotProcessable;\n";
        };
    }

    /**
     * resultType → Result alt-tipi fabrikası (referans §1 `:876-883` birebir): NotValid→Map param,
     * NotAuthorized/NotAuthenticated/ServerError→String param, tanınmayan→{@code NotProcessable(codeRef, message)}.
     * Fabrika adı camelCase error id (SPEC §6.4 üye adlandırma).
     */
    private static String throwFactoryMethod(String resultType, String retType, String factoryName, String codeRef) {
        return switch (resultType) {
            case "NotValid" -> "    public static Result<" + retType + "> " + factoryName
                    + "(Map<String, String> errors) {\n        return new NotValid<>(errors);\n    }\n\n";
            case "NotAuthorized" -> "    public static Result<" + retType + "> " + factoryName
                    + "(String reason) {\n        return new NotAuthorized<>(reason);\n    }\n\n";
            case "NotAuthenticated" -> "    public static Result<" + retType + "> " + factoryName
                    + "(String reason) {\n        return new NotAuthenticated<>(reason);\n    }\n\n";
            case "ServerError" -> "    public static Result<" + retType + "> " + factoryName
                    + "(String message) {\n        return new ServerError<>(message);\n    }\n\n";
            default -> "    public static Result<" + retType + "> " + factoryName
                    + "(String message) {\n        return new NotProcessable<>(" + codeRef + ", message);\n    }\n\n";
        };
    }

    /**
     * Consistency bölümü (referans §1 `:887-910`) — yalnız {@code mode≠null VEYA risk==eventual}
     * (Census ile aynı koşul). eventual → outbox seçim-iskeleti yorumu; aksi halde in-proc tx yorumu.
     */
    private static String consistencyFields(OperationJson o, String opId, BuildReport report) {
        var c = o.consistency();
        if (c == null || (c.mode() == null && !"eventual".equals(c.risk()))) {
            return "";
        }
        report.realized("consistency", opId);
        String mode = c.mode() == null ? "default" : c.mode();
        report.policy("consistency-mode", c.risk() + "/" + mode + " (generator-policy)");
        String modeLiteral = c.mode() == null ? "null" : "\"" + escapeJava(c.mode()) + "\"";
        String strategyComment = "eventual".equals(c.risk())
                ? "outbox seçim-iskeleti: write + outbox-kaydı TEK tx; ayrı dispatcher publish eder. "
                        + "Taşıma/retry = §8 policy."
                : "in-proc transaction (" + c.risk() + "): persist ambient tx içinde. mode = ek garanti yorumu.";
        StringBuilder sb = new StringBuilder();
        sb.append("    // consistency: ").append(c.risk()).append(" (mode: ").append(mode).append(") -> ")
                .append(strategyComment).append('\n');
        sb.append("    public static final String CONSISTENCY_RISK = \"").append(escapeJava(c.risk()))
                .append("\";\n");
        sb.append("    public static final String CONSISTENCY_MODE = ").append(modeLiteral).append(";\n\n");
        return sb.toString();
    }

    /**
     * Ext bölümü (referans §1 `:1142-1177`) — {@code op.ext()} varsa TÜM ns'ler için prelude yorumu +
     * {@code realized}/{@code {ns}-realization} policy (.NET {@code ExtPartial} birebir — trigger ns
     * dahil TÜM ext'ler burada realize edilir). Tanınan ns'ler ek sabit üretir: {@code @audit.*}→
     * {@code AUDIT_CATEGORY}, {@code @metric.*}→{@code METRIC_NAME}, {@code @http.*}→
     * {@code HTTP_ROUTE}/{@code HTTP_METHOD}/{@code HTTP_QUERY}/{@code HTTP_HEADER} + AYRICA
     * {@code policy("http-binding", ...)} (.NET {@code DotnetEmitter.cs:1160} paritesi).
     */
    private static String extFields(GmOperation op, BuildReport report) {
        List<ExtJson> ext = op.op().ext();
        if (ext == null || ext.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("    // passthrough prelude'lar (core yorumlamaz; hedef-özel realizasyon = §8 policy).\n");
        for (ExtJson e : ext) {
            report.realized("@" + e.ns() + "." + e.name(), op.id());
            report.policy(e.ns() + "-realization", "annotation/interceptor (generator-policy)");
            String argsStr = e.args().entrySet().stream()
                    .map(en -> en.getKey() + "=" + jsonNodeDisplay(en.getValue()))
                    .collect(Collectors.joining(", "));
            sb.append("    // @").append(e.ns()).append('.').append(e.name()).append('(').append(argsStr)
                    .append(")\n");
        }
        ExtJson audit = findExt(ext, "audit");
        ExtJson metric = findExt(ext, "metric");
        ExtJson http = findExt(ext, "http");
        if (audit != null && audit.args().containsKey("category")) {
            sb.append("    public static final String AUDIT_CATEGORY = ")
                    .append(jsonStrLiteral(audit.args().get("category"))).append(";\n");
        }
        if (metric != null && metric.args().containsKey("name")) {
            sb.append("    public static final String METRIC_NAME = ")
                    .append(jsonStrLiteral(metric.args().get("name"))).append(";\n");
        }
        if (http != null) {
            // @http ns'li op ext AYRICA http-binding policy'si alır (.NET DotnetEmitter.cs:1160 ExtPartial paritesi).
            report.policy("http-binding", "route/query/header detail (generator-policy)");
            if (http.args().containsKey("route")) {
                sb.append("    public static final String HTTP_ROUTE = ")
                        .append(jsonStrLiteral(http.args().get("route"))).append(";\n");
            }
            if (http.args().containsKey("method")) {
                sb.append("    public static final String HTTP_METHOD = ")
                        .append(jsonStrLiteral(http.args().get("method"))).append(";\n");
            }
            if (http.args().containsKey("query")) {
                sb.append("    public static final String HTTP_QUERY = ")
                        .append(jsonStrLiteral(http.args().get("query"))).append(";\n");
            }
            if (http.args().containsKey("header")) {
                sb.append("    public static final String HTTP_HEADER = ")
                        .append(jsonStrLiteral(http.args().get("header"))).append(";\n");
            }
        }
        sb.append('\n');
        return sb.toString();
    }

    /** {@code ext} listesinde ilk {@code ns} eşleşmesi; yoksa null. */
    private static ExtJson findExt(List<ExtJson> ext, String ns) {
        for (ExtJson e : ext) {
            if (e.ns().equals(ns)) {
                return e;
            }
        }
        return null;
    }

    /** Prelude yorumunda arg değeri (unquoted; .NET {@code JsonElement.ToString()} paritesi). */
    private static String jsonNodeDisplay(JsonNode n) {
        return n.isTextual() ? n.asText() : n.toString();
    }

    /**
     * Tanınan ext sabitleri için Java string-literal (her zaman {@code String} alana atanabilir —
     * .NET {@code JsonStr} paritesi genişletilmiş: sayısal/boolean JSON değerleri de tırnaklanır,
     * .NET'in aksine — Java'da {@code String} alana sayısal literal atanamaz; bkz. task raporu notu).
     */
    private static String jsonStrLiteral(JsonNode n) {
        if (n == null) {
            return "null";
        }
        return "\"" + escapeJava(n.asText()) + "\"";
    }

    /**
     * T4.1 — Idempotency bölümü (referans §1 {@code Idem}/{@code Idempotency.g.cs} + §7) —
     * {@code op.idempotent()!=null}. {@code IDEMPOTENCY_KEYS} dedup anahtarları; dedup-store =
     * §8 policy (in-memory; {@link #idempotencyStoreJava()}).
     */
    private static String idempotencyFields(OperationJson o, String opId, BuildReport report) {
        var idem = o.idempotent();
        if (idem == null) {
            return "";
        }
        report.realized("idempotent", opId);
        report.policy("dedup-store", "in-memory (generator-policy)");
        String keys = idem.keys().stream().map(k -> "\"" + escapeJava(k) + "\"").collect(Collectors.joining(", "));
        StringBuilder sb = new StringBuilder();
        sb.append("    // idempotency: dedup anahtarları (dedup-store = §8 policy).\n");
        sb.append("    public static final List<String> IDEMPOTENCY_KEYS = List.of(").append(keys).append(");\n\n");
        return sb.toString();
    }

    /**
     * T4.1 — Pagination bölümü (referans §1 {@code Page}/{@code {Op}.Page.g.cs} + §7) —
     * {@code op.pagination()!=null}. {@code PAGINATION_STRATEGY} + (size deklare edildiyse)
     * {@code DEFAULT_PAGE_SIZE}; dönüş tipi {@code Result<Page<{Ret}>>} çağıran tarafta (retType).
     * {@code cursor-token=opaque} policy — kodlama = generator-policy.
     */
    private static String paginationFields(OperationJson o, String opId, BuildReport report) {
        var pg = o.pagination();
        if (pg == null) {
            return "";
        }
        report.realized("pagination", opId);
        report.policy("pagination-strategy", pg.strategy() + " (generator-policy)");
        report.policy("cursor-token", "opaque (generator-policy)");
        StringBuilder sb = new StringBuilder();
        sb.append("    // pagination: strategy + declared size (keyset/offset uygulaması + cursor-token = §8 policy).\n");
        sb.append("    public static final String PAGINATION_STRATEGY = \"").append(escapeJava(pg.strategy()))
                .append("\";\n");
        if (pg.size() != null) {
            sb.append("    public static final int DEFAULT_PAGE_SIZE = ").append(pg.size()).append(";\n");
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * Step 5.3 — human seam ({@code {Op}Handler}, writeIfAbsent): HandlerBase'i extend eder;
     * {@code execute} gövdesi birebir marker fırlatır (skill tespiti substring {@code doldurulacak}).
     */
    private static String humanHandlerJava(GmOperation op, GenerationModel gm, String slicePkg, String reqName,
            String retType, List<RepoDep> deps) {
        String opId = op.id();
        OperationJson o = op.op();
        boolean idempotent = o.idempotent() != null;
        boolean paginated = o.pagination() != null;
        boolean emits = !o.emits().isEmpty();
        StringBuilder imports = new StringBuilder("import app.Result;\n");
        String retImport = customTypeImport(o.signature().returns(), gm);
        imports.append(repoImportsBlock(deps));
        if (retImport != null) {
            imports.append(retImport);
        }
        if (idempotent) {
            imports.append("import app.IdempotencyStore;\n");
        }
        if (paginated) {
            imports.append("import app.Page;\n");
        }
        if (emits) {
            imports.append("import app.EventBus;\n");
        }
        List<String> ctorParamList = new ArrayList<>();
        List<String> superArgList = new ArrayList<>();
        for (RepoDep d : deps) {
            ctorParamList.add(d.entityId() + "Repository " + d.fieldName());
            superArgList.add(d.fieldName());
        }
        // T4.1 — ctor-senkron: HandlerBase idempotent op'ta IdempotencyStore alıyorsa human seam de aynısını iletir.
        if (idempotent) {
            ctorParamList.add("IdempotencyStore idempotencyStore");
            superArgList.add("idempotencyStore");
        }
        // T4.2 — ctor-senkron: HandlerBase emits!=[] op'ta EventBus alıyorsa human seam de aynısını
        // iletir (idempotent'ten SONRA — HandlerBase ctor sırasıyla birebir).
        if (emits) {
            ctorParamList.add("EventBus eventBus");
            superArgList.add("eventBus");
        }
        // T4.4 — ctor-senkron: HandlerBase bu op'ta {Ext} client alıyorsa human seam de aynısını
        // iletir (idempotent+events'ten SONRA — HandlerBase ctor sırasıyla birebir).
        for (ExternalJson boundaryExt : boundaryDepsForOp(op, gm)) {
            String extField = Naming.camel(boundaryExt.name());
            imports.append("import app.boundary.").append(boundaryExt.name()).append(";\n");
            ctorParamList.add(boundaryExt.name() + " " + extField);
            superArgList.add(extField);
        }
        String ctorParams = String.join(", ", ctorParamList);
        String superArgs = String.join(", ", superArgList);

        return """
                package %s;

                %s
                public class %sHandler extends %sHandlerBase {

                    public %sHandler(%s) {
                        super(%s);
                    }

                    @Override
                    public Result<%s> execute(%s request) {
                        throw new UnsupportedOperationException("%s: iş mantığı doldurulacak");
                    }
                }
                """.formatted(slicePkg, imports, opId, opId, opId, ctorParams, superArgs, retType, reqName, opId);
    }

    /**
     * Step 5.4 — Endpoint ({@code @RestController}): rest serving başına bir route metodu.
     * POST/PUT/PATCH → {@code @RequestBody}; GET/DELETE → route param'ları {@code @PathVariable},
     * kalan record bileşenleri {@code @RequestParam} (request paramlardan kurulur).
     */
    private static String endpointJava(GmOperation op, String slicePkg, String reqName) {
        String opId = op.id();
        OperationJson o = op.op();
        List<ParamJson> params = o.signature().params();
        Set<String> annotationImports = new TreeSet<>();
        annotationImports.add("RestController");
        StringBuilder methods = new StringBuilder();
        int idx = 0;
        for (ServingJson s : o.serving()) {
            if (!"rest".equals(s.protocol())) {
                continue;
            }
            String method = restMethod(s);
            String route = restRoute(s);
            List<String> routeParams = restRouteParams(s);
            String verbAnno = Naming.httpVerbAnnotation(method);
            annotationImports.add(verbAnno.substring(1));
            String methodName = Naming.camel(opId) + (idx > 0 ? String.valueOf(idx) : "");

            methods.append("    ").append(verbAnno).append("(\"").append(route).append("\")\n");
            if (Naming.bindsBody(method)) {
                annotationImports.add("RequestBody");
                methods.append("    public ResponseEntity<?> ").append(methodName).append("(@RequestBody ")
                        .append(reqName).append(" request) {\n");
                methods.append("        return ResultHttp.toHttp(handler.execute(request));\n");
                methods.append("    }\n\n");
            } else {
                List<String> methodParams = new ArrayList<>();
                List<String> ctorArgs = new ArrayList<>();
                for (ParamJson p : params) {
                    String javaType = Naming.javaType(p.type(), p.collection());
                    String camel = Naming.camel(p.name());
                    if (routeParams.contains(p.name())) {
                        annotationImports.add("PathVariable");
                        methodParams.add("@PathVariable " + javaType + " " + camel);
                    } else {
                        annotationImports.add("RequestParam");
                        methodParams.add("@RequestParam " + javaType + " " + camel);
                    }
                    ctorArgs.add(camel);
                }
                // T4.1 — pagination varsa GET route'una opak cursor (veya offset) + size query paramları
                // eklenir (.NET MapLine paritesi; cursor-token kodlaması = generator-policy §8).
                var pg = o.pagination();
                if (pg != null) {
                    annotationImports.add("RequestParam");
                    if ("offset".equals(pg.strategy())) {
                        methodParams.add("@RequestParam(required = false) Integer offset");
                        ctorArgs.add("offset");
                    } else {
                        methodParams.add("@RequestParam(required = false) String cursor");
                        ctorArgs.add("cursor");
                    }
                    String sizeDefault = pg.size() == null ? "20" : String.valueOf(pg.size());
                    methodParams.add("@RequestParam(defaultValue = \"" + sizeDefault + "\") int size");
                    ctorArgs.add("size");
                }
                methods.append("    public ResponseEntity<?> ").append(methodName).append('(')
                        .append(String.join(", ", methodParams)).append(") {\n");
                methods.append("        ").append(reqName).append(" request = new ").append(reqName)
                        .append('(').append(String.join(", ", ctorArgs)).append(");\n");
                methods.append("        return ResultHttp.toHttp(handler.execute(request));\n");
                methods.append("    }\n\n");
            }
            idx++;
        }

        StringBuilder imports = new StringBuilder();
        imports.append("import app.ResultHttp;\n");
        imports.append("import org.springframework.http.ResponseEntity;\n");
        for (String a : annotationImports) {
            imports.append("import org.springframework.web.bind.annotation.").append(a).append(";\n");
        }

        return "package " + slicePkg + ";\n\n"
                + imports
                + "\n@RestController\npublic class " + opId + "Endpoint {\n\n"
                + "    private final " + opId + "Handler handler;\n\n"
                + "    public " + opId + "Endpoint(" + opId + "Handler handler) {\n"
                + "        this.handler = handler;\n"
                + "    }\n\n"
                + methods
                + "}\n";
    }

    /** İlk keyword arg değeri (HTTP metodu); yoksa GET (Naming.httpVerbAnnotation default'u zaten GET). */
    private static String restMethod(ServingJson s) {
        for (ServingArg a : s.args()) {
            if ("keyword".equals(a.kind())) {
                return a.value();
            }
        }
        return "GET";
    }

    /** İlk string arg değeri (route); yoksa "/". */
    private static String restRoute(ServingJson s) {
        for (ServingArg a : s.args()) {
            if ("string".equals(a.kind())) {
                return a.value();
            }
        }
        return "/";
    }

    /** Route string arg'ının {@code params} listesi (route token'ları); yoksa boş liste. */
    private static List<String> restRouteParams(ServingJson s) {
        for (ServingArg a : s.args()) {
            if ("string".equals(a.kind())) {
                return a.params() == null ? List.of() : a.params();
            }
        }
        return List.of();
    }

    // ── Result taksonomisi (SPEC §6.3 satırı; referans §1 "Result taksonomisi"; INV-5 — kapalı 6'lı
    // taksonomi + Page/Unit). Her public record ayrı dosya (paket app); alt-tip adları ve ResultHttp'nin
    // çözdüğü şekiller conformance sözleşmesidir — DEĞİŞTİRİLMEZ. ──

    private static String resultInterfaceJava() {
        return """
                package app;

                // kapalı 6'lı result taksonomisi (INV-5); handler yalnız bu tipleri döner. NotAuthenticated
                // ayrı kalır (401 vs 403).
                public sealed interface Result<T> permits Success, NotAuthenticated, NotAuthorized, NotValid, NotProcessable, ServerError {
                }
                """;
    }

    private static String successRecordJava() {
        return """
                package app;

                public record Success<T>(T value) implements Result<T> {
                }
                """;
    }

    private static String notAuthenticatedRecordJava() {
        return """
                package app;

                public record NotAuthenticated<T>(String reason) implements Result<T> {
                }
                """;
    }

    private static String notAuthorizedRecordJava() {
        return """
                package app;

                public record NotAuthorized<T>(String reason) implements Result<T> {
                }
                """;
    }

    private static String notValidRecordJava() {
        return """
                package app;

                import java.util.Map;

                public record NotValid<T>(Map<String, String> errors) implements Result<T> {
                }
                """;
    }

    private static String notProcessableRecordJava() {
        return """
                package app;

                public record NotProcessable<T>(String code, String message) implements Result<T> {
                }
                """;
    }

    private static String serverErrorRecordJava() {
        return """
                package app;

                public record ServerError<T>(String message) implements Result<T> {
                }
                """;
    }

    private static String pageRecordJava() {
        return """
                package app;

                import java.util.List;

                // pagination zarfı (cursor-token kodlaması = generator-policy).
                public record Page<T>(List<T> items, String nextCursor) {
                }
                """;
    }

    private static String unitRecordJava() {
        return """
                package app;

                // payload'sız komut dönüşü: Result<Unit> (void analoğu).
                public record Unit() {
                }
                """;
    }

    // ── ResultHttp (SPEC §6.3 satırı): result-type → HTTP wire; process-global override hook. ──

    private static String resultHttpJava() {
        return """
                package app;

                import java.util.Map;
                import java.util.function.UnaryOperator;

                import org.springframework.http.ResponseEntity;

                // result-type → HTTP wire eşlemesi (SPEC §6.3). override: insan tarafında özel zarf
                // (RFC7807 vb.) bağlamak için process-global hook; varsayılan identity.
                public final class ResultHttp {

                    private ResultHttp() {
                    }

                    public static UnaryOperator<ResponseEntity<?>> override = UnaryOperator.identity();

                    public static ResponseEntity<?> toHttp(Result<?> result) {
                        ResponseEntity<?> base = switch (result) {
                            case Success<?> s -> ResponseEntity.ok(s.value());
                            case NotAuthenticated<?> n -> ResponseEntity.status(401).body(Map.of("reason", n.reason()));
                            case NotAuthorized<?> n -> ResponseEntity.status(403).body(Map.of("reason", n.reason()));
                            case NotValid<?> n -> ResponseEntity.status(400).body(Map.of("errors", n.errors()));
                            case NotProcessable<?> n -> ResponseEntity.status(422)
                                    .body(Map.of("code", n.code(), "message", n.message()));
                            case ServerError<?> e -> ResponseEntity.status(500).body(Map.of("message", e.message()));
                        };
                        return override.apply(base);
                    }
                }
                """;
    }

    // ── Errors katalogu (SPEC §6.3 satırı; referans §1 `:828-841`) — modül başına tek dosya. ──

    private static String errorsJava(String module, List<ErrorJson> errors, BuildReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("package app.").append(Naming.packageOf(module)).append(";\n\n");
        sb.append("// adlı-hata kataloğu (SPEC §6.3): kod = agnostik ad; resultType → Result<T> alt-tipi (yorum).\n");
        sb.append("public final class Errors {\n\n");
        sb.append("    private Errors() {\n    }\n\n");
        for (ErrorJson error : errors) {
            report.realized("error", error.id());
            sb.append("    // resultType: ").append(error.resultType()).append('\n');
            sb.append("    public static final String ").append(error.id())
                    .append(" = \"").append(error.id()).append("\";\n\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    // ── Types: enum → enum, diğer kind → record (SPEC §6.3 satırı; referans §1 `:735-752`). Type-level
    // ext realize edilir (t.ext()); Type-FIELD ext T4.5 sweep'inde kalır (task-spec §5.4). ──

    private static String typeJava(TypeJson type, BuildReport report) {
        String pkg = Naming.packageOf(type.module());
        String extComment = typeLevelExtComment(type.ext(), type.id(), report);
        if ("enum".equals(type.kind())) {
            report.realized(type.kind(), type.id());
            String values = String.join(", ", type.values() == null ? List.of() : type.values());
            return "package app." + pkg + ";\n\n"
                    + extComment
                    + "public enum " + type.id() + " { " + values + " }\n";
        }

        List<FieldJson> fields = type.fields() == null ? List.of() : type.fields();
        String imports = typeFieldImports(fields);
        report.realized(type.kind(), type.id());
        // T4.5 Step 5.4 — type FIELD ext (owner "{typeId}.{field}"; census addExt type.fields).
        StringBuilder fieldExtComments = new StringBuilder();
        for (FieldJson f : fields) {
            fieldExtComments.append(typeLevelExtComment(f.ext(), type.id() + "." + f.name(), report));
        }
        String components = fields.stream()
                .map(f -> Naming.javaType(f.type(), f.collection()) + " " + Naming.camel(f.name()))
                .collect(Collectors.joining(", "));
        return "package app." + pkg + ";\n\n"
                + imports
                + (imports.isEmpty() ? "" : "\n")
                + extComment
                + fieldExtComments
                + "public record " + type.id() + "(" + components + ") {\n}\n";
    }

    /**
     * Ext passthrough → yorum + census kaydı (her annotation-site ortak; realizasyon = §8 policy;
     * referans §1 {@code ExtComment} `:1326-1331` paritesi). {@code owner} census
     * {@code Completeness.addExt} ile birebir: type/module/deployable→id, field/param→
     * {@code "{ownerId}.{name}"}.
     */
    private static String typeLevelExtComment(List<ExtJson> ext, String owner, BuildReport report) {
        return typeLevelExtComment(ext, owner, report, "");
    }

    /** {@link #typeLevelExtComment(List, String, BuildReport)} + satır-başı girinti (entity field vb.). */
    private static String typeLevelExtComment(List<ExtJson> ext, String owner, BuildReport report, String indent) {
        if (ext == null || ext.isEmpty()) {
            return "";
        }
        StringBuilder names = new StringBuilder();
        for (ExtJson x : ext) {
            report.realized("@" + x.ns() + "." + x.name(), owner);
            report.policy(x.ns() + "-realization", "annotation/interceptor (generator-policy)");
            if (!names.isEmpty()) {
                names.append(" ; ");
            }
            names.append('@').append(x.ns()).append('.').append(x.name());
        }
        return indent + "// ext: " + names + " (realizasyon = policy)\n";
    }

    /** Field tiplerine göre gereken import satırları (deterministik sabit sıra: BigDecimal/Instant/LocalDate/Duration/List). */
    private static String typeFieldImports(List<FieldJson> fields) {
        boolean bigDecimal = false;
        boolean instant = false;
        boolean localDate = false;
        boolean duration = false;
        boolean list = false;
        for (FieldJson f : fields) {
            switch (Naming.javaType(f.type(), false)) {
                case "BigDecimal" -> bigDecimal = true;
                case "Instant" -> instant = true;
                case "LocalDate" -> localDate = true;
                case "Duration" -> duration = true;
                default -> { }
            }
            if (f.collection()) {
                list = true;
            }
        }
        StringBuilder sb = new StringBuilder();
        if (bigDecimal) {
            sb.append("import java.math.BigDecimal;\n");
        }
        if (instant) {
            sb.append("import java.time.Instant;\n");
        }
        if (localDate) {
            sb.append("import java.time.LocalDate;\n");
        }
        if (duration) {
            sb.append("import java.time.Duration;\n");
        }
        if (list) {
            sb.append("import java.util.List;\n");
        }
        return sb.toString();
    }

    // ── Events: payload'lı record (SPEC §6.3 satırı; referans §1 `:1334-1345`) — modülde event
    // başına tek dosya. Payload field ext (owner {eventId}.{field}) T4.5 sweep kapsamı (§5.4 dışı). ──

    private static String eventJava(EventJson event, BuildReport report) {
        report.realized("event", event.id());
        String pkg = Naming.packageOf(event.module());
        List<FieldJson> payload = event.payload();
        String imports = typeFieldImports(payload);
        // T4.5 Step 5.4 — event payload FIELD ext (owner "{eventId}.{field}"; census addExt event.payload).
        StringBuilder fieldExtComments = new StringBuilder();
        for (FieldJson f : payload) {
            fieldExtComments.append(typeLevelExtComment(f.ext(), event.id() + "." + f.name(), report));
        }
        String components = payload.stream()
                .map(f -> Naming.javaType(f.type(), f.collection()) + " " + Naming.camel(f.name()))
                .collect(Collectors.joining(", "));
        return "package app." + pkg + ";\n\n"
                + imports
                + (imports.isEmpty() ? "" : "\n")
                + fieldExtComments
                + "public record " + event.id() + "(" + components + ") {\n}\n";
    }

    // ── Entity → @Entity (T3.4 §5.1; SPEC §6.3 Entities.g.cs satırı; referans §1 `:755-787`).
    // Mutable sınıf (record DEĞİL — JPA @Version alanı setter ister). Field-level ext (T4.5 sweep
    // kapsamı, §5.4 dışı) ve invariants (T3.5) BURADA emit edilmez. ──

    private static String entityJava(EntityJson entity, GenerationModel gm, BuildReport report) {
        List<EntityFieldJson> fields = entity.fields();
        boolean hasId = fields.stream().anyMatch(f -> "id".equals(f.name()));
        boolean hasEnum = fields.stream().anyMatch(f -> "enum".equals(f.ref()));
        boolean optimistic = "optimistic".equals(entity.concurrency());

        StringBuilder imports = new StringBuilder();
        if (hasEnum) {
            imports.append("import jakarta.persistence.EnumType;\n");
            imports.append("import jakarta.persistence.Enumerated;\n");
        }
        imports.append("import jakarta.persistence.Entity;\n");
        if (hasId) {
            imports.append("import jakarta.persistence.Id;\n");
        }
        imports.append("import jakarta.persistence.Table;\n");
        if (optimistic) {
            imports.append("import jakarta.persistence.Version;\n");
        }
        imports.append(entityFieldImports(fields, entity.module(), gm));

        StringBuilder sb = new StringBuilder();
        sb.append("package app.").append(Naming.packageOf(entity.module())).append(";\n\n");
        sb.append(imports).append('\n');
        sb.append(typeLevelExtComment(entity.ext(), entity.id(), report));
        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(entity.id()).append("\")\n");
        sb.append("public class ").append(entity.id()).append(" {\n\n");

        for (EntityFieldJson f : fields) {
            // T4.5 Step 5.4 — entity FIELD ext (owner "{entityId}.{field}"; census addExt entity.fields).
            sb.append(typeLevelExtComment(f.ext(), entity.id() + "." + f.name(), report, "    "));
            if (f.sourceOfTruth() != null) {
                report.realized("sourceOfTruth", entity.id() + "." + f.name());
                report.policy("source-of-truth", "cross-module FK reference; no navigation (generator-policy)");
                sb.append("    // sourceOfTruth: ").append(f.sourceOfTruth().module()).append('.')
                        .append(f.sourceOfTruth().entity()).append(" — cross-module FK; navigasyon AÇILMAZ\n");
            }
            if ("id".equals(f.name())) {
                sb.append("    @Id\n");
            } else if ("enum".equals(f.ref())) {
                sb.append("    @Enumerated(EnumType.STRING)\n");
            }
            sb.append("    private ").append(Naming.javaType(f.type(), f.collection())).append(' ')
                    .append(Naming.camel(f.name())).append(";\n\n");
        }

        if (optimistic) {
            report.realized("concurrency", entity.id());
            sb.append("    @Version\n");
            sb.append("    private long version;\n\n");
        }

        for (EntityFieldJson f : fields) {
            appendAccessors(sb, Naming.javaType(f.type(), f.collection()), Naming.camel(f.name()));
        }
        if (optimistic) {
            appendAccessors(sb, "long", "version");
        }

        report.realized("entity", entity.id());
        sb.append("}\n");
        return sb.toString();
    }

    /** getX()/setX() çifti (boolean primitive → isX()); JPA mutable sınıf ister — record DEĞİL. */
    private static void appendAccessors(StringBuilder sb, String type, String fieldName) {
        String pascal = Naming.pascal(fieldName);
        String getterPrefix = "boolean".equals(type) ? "is" : "get";
        sb.append("    public ").append(type).append(' ').append(getterPrefix).append(pascal).append("() {\n");
        sb.append("        return ").append(fieldName).append(";\n");
        sb.append("    }\n\n");
        sb.append("    public void set").append(pascal).append('(').append(type).append(' ').append(fieldName)
                .append(") {\n");
        sb.append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n");
        sb.append("    }\n\n");
    }

    /**
     * {@code app.uncharted} owned-entity POJO'ları için (T4.5; kendi GM entity/module kavramı yok,
     * sabit paket) — cross-module custom-type taraması YOK, yalnız java.* built-in import'lar.
     */
    private static String entityFieldImports(List<EntityFieldJson> fields) {
        boolean bigDecimal = false;
        boolean instant = false;
        boolean localDate = false;
        boolean duration = false;
        boolean list = false;
        for (EntityFieldJson f : fields) {
            switch (Naming.javaType(f.type(), false)) {
                case "BigDecimal" -> bigDecimal = true;
                case "Instant" -> instant = true;
                case "LocalDate" -> localDate = true;
                case "Duration" -> duration = true;
                default -> { }
            }
            if (f.collection()) {
                list = true;
            }
        }
        StringBuilder sb = new StringBuilder();
        if (bigDecimal) {
            sb.append("import java.math.BigDecimal;\n");
        }
        if (instant) {
            sb.append("import java.time.Instant;\n");
        }
        if (localDate) {
            sb.append("import java.time.LocalDate;\n");
        }
        if (duration) {
            sb.append("import java.time.Duration;\n");
        }
        if (list) {
            sb.append("import java.util.List;\n");
        }
        return sb.toString();
    }

    /**
     * Entity alan tiplerine göre gereken import satırları ({@link #typeFieldImports} ile aynı sıra).
     * T6.3-FIX #2 — bir alan başka modülde tanımlı bir entity/type/enum'a (ör. {@code app.shared}
     * paylaşılan enum'ları) referans veriyorsa cross-package import da eklenir (ordinal-distinct,
     * alan görülme sırası; kendi modülü → aynı paket, import gerekmez).
     */
    private static String entityFieldImports(List<EntityFieldJson> fields, String ownerModule, GenerationModel gm) {
        boolean bigDecimal = false;
        boolean instant = false;
        boolean localDate = false;
        boolean duration = false;
        boolean list = false;
        for (EntityFieldJson f : fields) {
            switch (Naming.javaType(f.type(), false)) {
                case "BigDecimal" -> bigDecimal = true;
                case "Instant" -> instant = true;
                case "LocalDate" -> localDate = true;
                case "Duration" -> duration = true;
                default -> { }
            }
            if (f.collection()) {
                list = true;
            }
        }
        StringBuilder sb = new StringBuilder();
        if (bigDecimal) {
            sb.append("import java.math.BigDecimal;\n");
        }
        if (instant) {
            sb.append("import java.time.Instant;\n");
        }
        if (localDate) {
            sb.append("import java.time.LocalDate;\n");
        }
        if (duration) {
            sb.append("import java.time.Duration;\n");
        }
        if (list) {
            sb.append("import java.util.List;\n");
        }
        Set<String> customImports = new LinkedHashSet<>();
        for (EntityFieldJson f : fields) {
            String custom = crossModuleTypeImport(f.type(), ownerModule, gm);
            if (custom != null) {
                customImports.add(custom);
            }
        }
        for (String custom : customImports) {
            sb.append(custom);
        }
        return sb.toString();
    }

    /**
     * {@link #customTypeImport}'un modül-duyarlı hali: manifest tipi GM'de entity/type/event id'sine
     * eşleşiyor VE o tanımın modülü {@code ownerModule}'dan FARKLIYSA cross-package import satırı;
     * aynı modülse (aynı Java paketi) veya eşleşme yoksa null.
     */
    private static String crossModuleTypeImport(String manifestType, String ownerModule, GenerationModel gm) {
        for (EntityJson e : gm.entities()) {
            if (e.id().equals(manifestType) && !e.module().equals(ownerModule)) {
                return "import app." + Naming.packageOf(e.module()) + "." + e.id() + ";\n";
            }
        }
        for (TypeJson t : gm.types()) {
            if (t.id().equals(manifestType) && !t.module().equals(ownerModule)) {
                return "import app." + Naming.packageOf(t.module()) + "." + t.id() + ";\n";
            }
        }
        for (EventJson ev : gm.events()) {
            if (ev.id().equals(manifestType) && !ev.module().equals(ownerModule)) {
                return "import app." + Naming.packageOf(ev.module()) + "." + ev.id() + ";\n";
            }
        }
        return null;
    }

    // ── Repository (T3.4 §5.2; SPEC §6.3 AppDbContext.g.cs satırı; referans §1 `:1189-1212`) —
    // entity başına tek JpaRepository arayüzü; String ID (SPEC §6.4: ID/*Id soneki → String). ──

    private static String repositoryJava(EntityJson entity) {
        return """
                package app.%s;

                import org.springframework.data.jpa.repository.JpaRepository;

                public interface %sRepository extends JpaRepository<%s, String> {
                }
                """.formatted(Naming.packageOf(entity.module()), entity.id(), entity.id());
    }

    // ── Guards + Invariants (T3.5 §5.2-5.3; SPEC §6.5; referans §1 `:1216-1319`, §7 validation/rule/
    // permit/guardRef/invariant satırları). Tipli predicate (INV-4, dynamic YOK); render stratejisi
    // {@link JavaPredicateRenderer}, dil-nötr yürüyüş gen-core {@code ExprWalk}. ──

    /** validation/rule/permit varsa {@code {Op}Guards.java}; hiçbiri yoksa null (dosya emit edilmez). */
    private static String guardsJava(GmOperation op, GenerationModel gm, BuildReport report) {
        OperationJson o = op.op();
        List<GuardedExpr> validation = o.validation();
        List<GuardedExpr> rule = o.rule();
        Abac abac = o.abac();
        if (validation.isEmpty() && rule.isEmpty() && abac == null) {
            return null;
        }

        Function<List<String>, String> resolveType = p -> gm.env().resolvePath(gm, p, op.id(), null);
        List<String> methods = new ArrayList<>();
        List<String> records = new ArrayList<>();
        ImportFlags imports = new ImportFlags();
        boolean anyGuardRef = false;

        int validationOk = 0;
        for (int i = 0; i < validation.size(); i++) {
            GuardedExpr g = validation.get(i);
            anyGuardRef |= g.guardRef() != null;
            if (predicate(op.id(), "Validation", i, g, resolveType, gm, report, methods, records, imports)) {
                validationOk++;
            }
        }
        int ruleOk = 0;
        for (int i = 0; i < rule.size(); i++) {
            GuardedExpr g = rule.get(i);
            anyGuardRef |= g.guardRef() != null;
            if (predicate(op.id(), "Rule", i, g, resolveType, gm, report, methods, records, imports)) {
                ruleOk++;
            }
        }
        int permitOk = 0;
        if (abac != null) {
            GuardedExpr permitExpr = new GuardedExpr("permit when <expr>", abac.permit(), null);
            if (predicate(op.id(), "Permit", 0, permitExpr, resolveType, gm, report, methods, records, imports)) {
                permitOk++;
            }
        }

        if (validationOk > 0) {
            report.realized("validation", op.id());
        }
        if (ruleOk > 0) {
            report.realized("rule", op.id());
        }
        if (permitOk > 0) {
            report.realized("permit", op.id());
        }
        if (anyGuardRef) {
            report.realized("guardRef", op.id());
            report.policy("guard-linkage", "build-time coverage link; emitted as comment (generator-policy)");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(Naming.slicePackage(op.module(), op.id())).append(";\n\n");
        sb.append(imports.render());
        sb.append("// validation→NotValid(400) · rule→NotProcessable(422) · permit→authz. Tipli predicate\n");
        sb.append("// (dynamic YOK); input record manifest tiplerinden — çözülemeyen alan tipli seam (inferred).\n");
        sb.append("public final class ").append(op.id()).append("Guards {\n\n");
        sb.append("    private ").append(op.id()).append("Guards() {\n    }\n\n");
        for (String m : methods) {
            sb.append(m);
        }
        for (String r : records) {
            sb.append(r);
        }
        sb.append("}\n");
        return sb.toString();
    }

    /** invariants varsa {@code {Entity}Invariants.java}; yoksa null. Metot alanları doğrudan entity-field tipli (task §5.3). */
    private static String invariantsJava(EntityJson entity, GenerationModel gm, BuildReport report) {
        List<GuardedExpr> invariants = entity.invariants();
        if (invariants.isEmpty()) {
            return null;
        }
        Function<List<String>, String> resolveType = p -> gm.env().resolvePath(gm, p, null, entity.id());
        List<String> methods = new ArrayList<>();
        ImportFlags imports = new ImportFlags();
        boolean anyGuardRef = false;
        int ok = 0;
        for (int i = 0; i < invariants.size(); i++) {
            GuardedExpr g = invariants.get(i);
            anyGuardRef |= g.guardRef() != null;
            if (invariant(entity.id(), i, g, resolveType, report, methods, imports)) {
                ok++;
            }
        }
        if (ok > 0) {
            report.realized("invariant", entity.id());
        }
        if (anyGuardRef) {
            report.realized("guardRef", entity.id());
            report.policy("guard-linkage", "build-time coverage link; emitted as comment (generator-policy)");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package app.").append(Naming.packageOf(entity.module())).append(";\n\n");
        sb.append(imports.render());
        sb.append("// entity invariant'ları (kalıcı veri-bütünlüğü). Tipli predicate; alanlar entity-field tiplerinden.\n");
        sb.append("public final class ").append(entity.id()).append("Invariants {\n\n");
        sb.append("    private ").append(entity.id()).append("Invariants() {\n    }\n\n");
        for (String m : methods) {
            sb.append(m);
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Bir guard predicate'ini render eder → method + input record'u {@code methods}/{@code records}'a
     * ekler. Başarılıysa true; {@link UnsupportedConstruct}'ta {@code report.unsupported} yazar +
     * throw-stub method ekler (sessizlik YOK) ve false döner.
     */
    private static boolean predicate(String opId, String kind, int i, GuardedExpr g,
            Function<List<String>, String> resolveType, GenerationModel gm, BuildReport report,
            List<String> methods, List<String> records, ImportFlags imports) {
        String guardComment = "";
        if (g.guardRef() != null) {
            guardComment = "    // guardRef: " + g.guardRef() + " (build-time kapsama bağı)\n";
        }
        String inputName = opId + kind + i + "Input";
        try {
            ExprWalk.Result<String> res = JavaPredicateRenderer.render(g.ast(), resolveType);
            Map<String, String> hints = ExprWalk.inferLiteralTypes(g.ast());
            List<String> fields = new ArrayList<>();
            for (List<String> path : res.paths()) {
                String type = JavaPredicateRenderer.javaFieldType(path, resolveType, hints);
                imports.note(type);
                fields.add(type + " " + ExprWalk.propName(path));
            }
            imports.noteExpr(res.expr());
            methods.add(guardComment
                    + "    public static boolean " + methodName(kind, i) + "(" + inputName + " input) {\n"
                    + "        return " + res.expr() + ";\n"
                    + "    }\n\n");
            records.add("    public record " + inputName + "(" + String.join(", ", fields) + ") {\n    }\n\n");
            return true;
        } catch (UnsupportedConstruct e) {
            report.unsupported(kind.toLowerCase(java.util.Locale.ROOT), opId + "#" + kind + i, e.getMessage());
            methods.add(guardComment
                    + "    public static boolean " + methodName(kind, i) + "() {\n"
                    + "        throw new UnsupportedOperationException(\"unsupported: "
                    + escapeJava(g.text()) + "\");\n"
                    + "    }\n\n");
            return false;
        }
    }

    /**
     * Bir invariant predicate'ini render eder (bare parametre biçimi — input record YOK, task §5.3).
     * Başarılıysa true; {@link UnsupportedConstruct}'ta unsupported + throw-stub + false.
     */
    private static boolean invariant(String entityId, int i, GuardedExpr g,
            Function<List<String>, String> resolveType, BuildReport report,
            List<String> methods, ImportFlags imports) {
        String guardComment = "";
        if (g.guardRef() != null) {
            guardComment = "    // guardRef: " + g.guardRef() + " (build-time kapsama bağı)\n";
        }
        try {
            ExprWalk.Result<String> res = JavaPredicateRenderer.render(g.ast(), resolveType, false);
            Map<String, String> hints = ExprWalk.inferLiteralTypes(g.ast());
            List<String> params = new ArrayList<>();
            for (List<String> path : res.paths()) {
                String type = JavaPredicateRenderer.javaFieldType(path, resolveType, hints);
                imports.note(type);
                params.add(type + " " + ExprWalk.propName(path));
            }
            imports.noteExpr(res.expr());
            methods.add(guardComment
                    + "    public static boolean invariant" + i + "(" + String.join(", ", params) + ") {\n"
                    + "        return " + res.expr() + ";\n"
                    + "    }\n\n");
            return true;
        } catch (UnsupportedConstruct e) {
            report.unsupported("invariant", entityId + "#Invariant" + i, e.getMessage());
            methods.add(guardComment
                    + "    public static boolean invariant" + i + "() {\n"
                    + "        throw new UnsupportedOperationException(\"unsupported: "
                    + escapeJava(g.text()) + "\");\n"
                    + "    }\n\n");
            return false;
        }
    }

    /** Guards metot adı: validation0.. / rule0.. / permit0 (camelCase, SPEC §6.4). */
    private static String methodName(String kind, int i) {
        return Naming.camel(kind) + i;
    }

    private static String escapeJava(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Predicate dosyalarının gereken java.* import'larını (deterministik sabit sıra) biriktirir. */
    private static final class ImportFlags {
        private boolean bigDecimal;
        private boolean instant;
        private boolean localDate;
        private boolean duration;
        private boolean list;

        void note(String javaType) {
            switch (javaType) {
                case "BigDecimal" -> bigDecimal = true;
                case "Instant" -> instant = true;
                case "LocalDate" -> localDate = true;
                case "Duration" -> duration = true;
                default -> {
                    if (javaType.startsWith("List<")) {
                        list = true;
                    }
                }
            }
        }

        /** Literal render'ında ({@code new BigDecimal("...")}) BigDecimal geçebilir; expr üzerinden yakala. */
        void noteExpr(String expr) {
            if (expr.contains("new BigDecimal(")) {
                bigDecimal = true;
            }
        }

        String render() {
            StringBuilder sb = new StringBuilder();
            if (bigDecimal) {
                sb.append("import java.math.BigDecimal;\n");
            }
            if (instant) {
                sb.append("import java.time.Instant;\n");
            }
            if (localDate) {
                sb.append("import java.time.LocalDate;\n");
            }
            if (duration) {
                sb.append("import java.time.Duration;\n");
            }
            if (list) {
                sb.append("import java.util.List;\n");
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            return sb.toString();
        }
    }

    // ── PersistenceConfig (root, T3.4 §5.2; entities>0) — açık @EnableJpaRepositories/@EntityScan
    // kaydı (component-scan YOK, SPEC §12/4 tam-açık kayıt kararı); GeneratedBootstrap'a @Import edilir. ──

    private static String persistenceConfigJava() {
        return """
                package app;

                import org.springframework.boot.autoconfigure.domain.EntityScan;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

                @Configuration
                @EnableJpaRepositories(basePackages = "app")
                @EntityScan(basePackages = "app")
                public class PersistenceConfig {
                }
                """;
    }

    // ── DeploymentTopology (root, T4.5 Step 5.2; deployables>0; referans §1 HostFile `:789-824`) —
    // modular-monolith host: deployable -> tek process'te barındırılan units (modüller). Statik
    // LinkedHashMap init (Map.of sıra GARANTİ ETMEZ — determinizm kaynak-metin/insertion sırasıyla). ──

    private static String deploymentTopologyJava(GenerationModel gm, BuildReport report) {
        report.policy("deployment-topology",
                "modular-monolith host: units single-process co-hosted (generator-policy)");
        StringBuilder extComments = new StringBuilder();
        StringBuilder entries = new StringBuilder();
        for (Deployable d : gm.deployables()) {
            report.realized("deployable", d.name());
            extComments.append(typeLevelExtComment(d.ext(), d.name(), report));
            String units = d.units().stream().map(u -> "\"" + escapeJava(u) + "\"")
                    .collect(Collectors.joining(", "));
            entries.append("        DEPLOYABLES.put(\"").append(escapeJava(d.name())).append("\", List.of(")
                    .append(units).append("));\n");
        }
        return """
                package app;

                import java.util.LinkedHashMap;
                import java.util.List;
                import java.util.Map;

                // modular-monolith host topolojisi: deployable -> tek process'te barındırılan units (modüller).
                // ponytail: tek host; ayrı-deploy = units'i ayrı host'a taşı (seam). Docker/orchestrator = §8 / insan.
                %spublic final class DeploymentTopology {

                    private DeploymentTopology() {
                    }

                    public static final Map<String, List<String>> DEPLOYABLES = new LinkedHashMap<>();

                    static {
                %s    }
                }
                """.formatted(extComments, entries);
    }

    // ── EventBus (root, events>0): cross-module publish seam + altyapı stub (SPEC §6.3 satırı;
    // referans §1 `:1385-1398`). OutboxEventBus gen-owned infra stub'dur — WriteIfAbsent human-seam
    // DEĞİL; bu yüzden mesajında "doldurulacak" seam-marker alt-dizesi KULLANILMAZ (INV-S §3 karışmasın). ──

    private static String eventBusJava() {
        return """
                package app;

                // cross-module event yayın seam'i. ponytail: outbox/broker/ack/retry altyapısı ileride
                // eklenir (§8).
                public interface EventBus {
                    void publish(Object event);
                }

                // altyapı stub — human-seam DEĞİL (gen-owned, WriteAlways); outbox taşıma katmanı henüz yok.
                final class OutboxEventBus implements EventBus {
                    @Override
                    public void publish(Object event) {
                        throw new UnsupportedOperationException("outbox: event taşıma altyapısı henüz eklenmedi");
                    }
                }
                """;
    }

    // ── IdempotencyStore (root, herhangi op idempotent!=null): dedup seam + in-memory altyapı
    // (T4.1; referans §1 `:1073-1089` Idempotency.g.cs; policy dedup-store=in-memory). Bean kaydı
    // GeneratedBootstrap'ta EXPLICIT @Bean (SPEC §12/4 tam-açık kayıt; component-scan YOK). ──

    private static String idempotencyStoreJava() {
        return """
                package app;

                import java.util.concurrent.ConcurrentHashMap;

                // dedup seam'i: idempotent op'lar ilk çağrıda tryBegin(key)=true alır, tekrar denemede false
                // (dedup-store = §8 policy).
                public interface IdempotencyStore {
                    boolean tryBegin(String key);
                }

                // altyapı stub — human-seam DEĞİL (gen-owned, WriteAlways); dedup-store=in-memory (generator-policy).
                final class InMemoryIdempotencyStore implements IdempotencyStore {
                    private final ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<>();

                    @Override
                    public boolean tryBegin(String key) {
                        return seen.putIfAbsent(key, Boolean.TRUE) == null;
                    }
                }
                """;
    }

    // ── Subscription consumer (T4.2 §5.2; SPEC §6.2/§6.3 Subscriptions.g.cs satırı; referans §1
    // `:1348-1383`) — Generation Gap: gen-owned {Event}To{Op}ConsumerBase (WriteAlways) + human seam
    // {Event}To{Op}Consumer (WriteIfAbsent, marker). CONSUMER op'un MODÜL SLICE'INDA yaşar — event'in
    // modülünde DEĞİL (T4.2 §2 Why: yanlış modüle emit = silent-fail davranış hatası). ──

    /**
     * Gen-owned taban sınıf: {@code {Op}Handler} alanı + ctor + gövdesiz {@code handle(event)}.
     * Javadoc playbook kanonik sırasını belirtir: event→request eşle → handler.execute çağır.
     */
    private static String subscriptionConsumerBaseJava(SubscriptionJson s) {
        String consumerModule = s.consumer().module();
        String consumerOp = s.consumer().op();
        String eventModule = s.event().module();
        String eventName = s.event().name();
        String slicePkg = Naming.slicePackage(consumerModule, consumerOp);
        String cls = eventName + "To" + consumerOp + "Consumer";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(slicePkg).append(";\n\n");
        if (!eventModule.equals(consumerModule)) {
            sb.append("import app.").append(Naming.packageOf(eventModule)).append('.').append(eventName)
                    .append(";\n\n");
        }
        sb.append("/**\n");
        sb.append(" * ").append(eventName).append(" -&gt; ").append(consumerOp)
                .append(" subscription taban sınıfı (Generation Gap, gen-owned; SPEC §6.2/§6.3\n");
        sb.append(" * Subscriptions.g.cs satırı). Gövde human seam'de ({@code ").append(cls).append("}):\n");
        sb.append(" * event -&gt; request eşle -&gt; handler.execute çağır (playbook kanonik sırası).\n");
        sb.append(" */\n");
        sb.append("public abstract class ").append(cls).append("Base {\n\n");
        sb.append("    protected final ").append(consumerOp).append("Handler handler;\n\n");
        sb.append("    protected ").append(cls).append("Base(").append(consumerOp).append("Handler handler) {\n");
        sb.append("        this.handler = handler;\n");
        sb.append("    }\n\n");
        sb.append("    public abstract void handle(").append(eventName).append(" event);\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Human seam ({@code {Event}To{Op}Consumer}, writeIfAbsent): base'i extend eder; {@code handle}
     * gövdesi birebir marker fırlatır (SPEC §6.2 Subscription marker: {@code "{cls}.handle:
     * doldurulacak"}). {@code realized("subscription", event.name())} burada (census, Completeness §7).
     */
    private static String subscriptionConsumerLogicJava(SubscriptionJson s, BuildReport report) {
        String consumerModule = s.consumer().module();
        String consumerOp = s.consumer().op();
        String eventModule = s.event().module();
        String eventName = s.event().name();
        report.realized("subscription", eventName);

        String slicePkg = Naming.slicePackage(consumerModule, consumerOp);
        String cls = eventName + "To" + consumerOp + "Consumer";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(slicePkg).append(";\n\n");
        if (!eventModule.equals(consumerModule)) {
            sb.append("import app.").append(Naming.packageOf(eventModule)).append('.').append(eventName)
                    .append(";\n\n");
        }
        sb.append("public class ").append(cls).append(" extends ").append(cls).append("Base {\n\n");
        sb.append("    public ").append(cls).append('(').append(consumerOp).append("Handler handler) {\n");
        sb.append("        super(handler);\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public void handle(").append(eventName).append(" event) {\n");
        sb.append("        throw new UnsupportedOperationException(\"").append(cls)
                .append(".handle: doldurulacak\");\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    // ── Trigger (T4.3; SPEC §6.2 `{Op}.Trigger.g.cs` satırı; referans §1 `:1105-1123`/§2) —
    // Generation Gap: gen-owned {Op}{T}TriggerBase (WriteAlways; SmartLifecycle — isRunning/stop
    // basit state gen-owned, start() soyut) + human seam {Op}{T}Trigger (WriteIfAbsent, marker).
    // realized("@trigger.{name}") T3.7 ExtPartial paritesinde (extFields) yapılır — burada
    // TEKRARLANMAZ (çift-entry önlenir). ──

    /**
     * Gen-owned taban sınıf: {@code {Op}Handler} alanı + ctor; {@code SmartLifecycle}
     * {@code isRunning}/{@code stop} basit-state gen-owned; {@code start()} soyut bildirim
     * (gövdesi human seam'de, {@code {Op}{T}Trigger}).
     */
    private static String triggerBaseJava(String slicePkg, String opId, String triggerCls, ExtJson t) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(slicePkg).append(";\n\n");
        sb.append("import org.springframework.context.SmartLifecycle;\n\n");
        sb.append("/**\n");
        sb.append(" * @trigger.").append(t.name()).append(" -&gt; ").append(opId)
                .append(" tetikleyici taban sınıfı (Generation Gap, gen-owned; SPEC §6.2\n");
        sb.append(" * {@code {Op}.Trigger.g.cs} satırı). {@code start()} gövdesi human seam'de\n");
        sb.append(" * ({@code ").append(triggerCls)
                .append("}); {@code isRunning}/{@code stop} gen-owned basit state.\n");
        sb.append(" */\n");
        sb.append("public abstract class ").append(triggerCls).append("Base implements SmartLifecycle {\n\n");
        sb.append("    protected final ").append(opId).append("Handler handler;\n");
        sb.append("    protected volatile boolean running;\n\n");
        sb.append("    protected ").append(triggerCls).append("Base(").append(opId).append("Handler handler) {\n");
        sb.append("        this.handler = handler;\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public abstract void start();\n\n");
        sb.append("    @Override\n");
        sb.append("    public boolean isRunning() {\n");
        sb.append("        return running;\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public void stop() {\n");
        sb.append("        running = false;\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Human seam ({@code {Op}{T}Trigger}, writeIfAbsent): base'i extend eder; {@code start()}
     * gövdesi birebir marker fırlatır ({@code "{opId}{T}Trigger.start: doldurulacak"} — referans
     * §2 boş-marker metinleri; .NET {@code "{op.Id}{T}Trigger.StartAsync: doldurulacak"} paritesi,
     * Java hedefinde {@code SmartLifecycle.start()} senkron olduğundan {@code StartAsync}→{@code start}).
     */
    private static String triggerLogicJava(String slicePkg, String opId, String triggerCls) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(slicePkg).append(";\n\n");
        sb.append("public class ").append(triggerCls).append(" extends ").append(triggerCls)
                .append("Base {\n\n");
        sb.append("    public ").append(triggerCls).append('(').append(opId).append("Handler handler) {\n");
        sb.append("        super(handler);\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public void start() {\n");
        sb.append("        throw new UnsupportedOperationException(\"").append(triggerCls)
                .append(".start: doldurulacak\");\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Kök {@code Subscriptions.java} (T4.2 §5.3; yalnız subscriptions>0): human consumer sınıfları
     * {@code @Bean} ile TAM-AÇIK kaydedilir (SPEC §12/4; component-scan YOK). Gerçek event→consumer
     * dispatch in-memory bus stub'ının altyapı işi (§8) — bu sınıf yalnız kayıt + stub tutar.
     */
    private static String subscriptionsRootJava(GenerationModel gm) {
        Set<String> imports = new TreeSet<>();
        StringBuilder beans = new StringBuilder();
        for (SubscriptionJson s : gm.subscriptions()) {
            String consumerModule = s.consumer().module();
            String consumerOp = s.consumer().op();
            String consumerPkg = Naming.packageOf(consumerModule);
            String consumerSlug = consumerOp.toLowerCase(Locale.ROOT);
            String cls = s.event().name() + "To" + consumerOp + "Consumer";

            imports.add("import app." + consumerPkg + "." + consumerSlug + "." + consumerOp + "Handler;\n");
            imports.add("import app." + consumerPkg + "." + consumerSlug + "." + cls + ";\n");

            String camelBean = Naming.camel(cls);
            beans.append("    @Bean\n");
            beans.append("    public ").append(cls).append(' ').append(camelBean).append('(')
                    .append(consumerOp).append("Handler handler) {\n");
            beans.append("        return new ").append(cls).append("(handler);\n");
            beans.append("    }\n\n");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package app;\n\n");
        sb.append("import org.springframework.context.annotation.Bean;\n");
        sb.append("import org.springframework.context.annotation.Configuration;\n");
        for (String imp : imports) {
            sb.append(imp);
        }
        sb.append('\n');
        sb.append("// subscription kaydı — event->consumer dispatch in-memory bus stub'ının altyapı/\n");
        sb.append("// doldurucu işi (§8 EventBus); bu sınıf yalnız consumer bean'lerini TAM-AÇIK kaydeder\n");
        sb.append("// (SPEC §12/4; component-scan YOK).\n");
        sb.append("@Configuration\n");
        sb.append("public class Subscriptions {\n\n");
        sb.append(beans);
        sb.append("}\n");
        return sb.toString();
    }

    // ── Boundary (T4.4; SPEC §6.3 Boundary.g.cs satırı; referans §1 `:913-955`/`:959-974`/
    // `:977-1014`) — external çağrı-adapter'leri: {@code {Ext}} arayüzü (gen-owned) + human
    // {@code {Ext}Client} seam (writeIfAbsent, marker) + caller-side {@code {Ext}{Op}Validation}
    // (INV-4). calls/compensate saga yorumları + policy'leri T4.5 kapsamı (bu task DIŞI). ──

    /** Op'un çağırdığı external'lar ({@code callEdges.from==opId && kind=="external"}), ordinal (ext ismine göre). */
    private static List<ExternalJson> boundaryDepsForOp(GmOperation op, GenerationModel gm) {
        Set<String> extNames = new TreeSet<>();
        for (CallEdgeJson ce : gm.callEdges()) {
            if (op.id().equals(ce.from()) && "external".equals(ce.kind())) {
                extNames.add(ce.to().system());
            }
        }
        List<ExternalJson> deps = new ArrayList<>();
        for (String name : extNames) {
            for (ExternalJson ext : gm.externals()) {
                if (ext.name().equals(name)) {
                    deps.add(ext);
                    break;
                }
            }
        }
        return deps;
    }

    /** Bir boundary-op'un Java dönüş+param tiplerine göre gereken java.* import'ları ({@link #paramImports} sarmalı). */
    private static String boundaryOpImports(List<BoundaryOpJson> ops) {
        List<String> javaTypes = new ArrayList<>();
        boolean unit = false;
        for (BoundaryOpJson b : ops) {
            String retType = Naming.javaType(b.signature().returns(), false);
            javaTypes.add(retType);
            // T6.3-FIX #1 — boundary-op dönüşü Unit ise (app.boundary paketi, Unit kök "app"
            // paketinde) cross-package import gerekir; HandlerBase'deki customTypeImport ile aynı köken.
            if ("Unit".equals(retType)) {
                unit = true;
            }
            for (ParamJson p : b.signature().params()) {
                javaTypes.add(Naming.javaType(p.type(), p.collection()));
            }
        }
        String imports = paramImports(javaTypes);
        return unit ? "import app.Unit;\n" + imports : imports;
    }

    /** Boundary-op parametre listesi ({@code {JavaTip} {camelAd}, ...}). */
    private static String boundaryOpParams(BoundaryOpJson b) {
        return b.signature().params().stream()
                .map(p -> Naming.javaType(p.type(), p.collection()) + " " + Naming.camel(p.name()))
                .collect(Collectors.joining(", "));
    }

    /**
     * Step 5.1 — {@code gen/java/app/boundary/{Ext}.java}: {@code public interface {Ext}} — her
     * boundary-op bir metot; serving varsa metot üstü yorum. {@code realized("external", name)},
     * her op {@code realized("boundary-op", "{ext}.{op}")}, serving başına
     * {@code realized("serving", "{ext}.{op}:{proto}")}. T4.5 Step 5.4 — boundary-op param-ext
     * (owner {@code "{ext}.{op}.{param}"}). T4.5 Step 5.1 — bu external'i hedefleyen callEdge'ler
     * ({@code to.system==ext.name && kind==external}): trailing {@code // calls:}/{@code // saga:}
     * yorumu + {@code realized("calls"/"compensate", from)} + (compensate'li) {@code saga-orchestration-state} policy.
     */
    private static String boundaryFile(ExternalJson ext, BuildReport report, List<CallEdgeJson> callEdges) {
        report.realized("external", ext.name());
        StringBuilder sb = new StringBuilder();
        sb.append("package app.boundary;\n\n");
        String stdImports = boundaryOpImports(ext.operations());
        if (!stdImports.isEmpty()) {
            sb.append(stdImports).append('\n');
        }
        sb.append("// external çağrı-adapter'i (generated:false → sistemi ÜRETME, yalnız çağıran arayüz).\n");
        sb.append("public interface ").append(ext.name()).append(" {\n\n");
        for (BoundaryOpJson b : ext.operations()) {
            report.realized("boundary-op", ext.name() + "." + b.id());
            for (ServingJson s : b.serving() == null ? List.<ServingJson>of() : b.serving()) {
                report.realized("serving", ext.name() + "." + b.id() + ":" + s.protocol());
                sb.append("    // serving: ").append(s.raw()).append(" — transport client sorumluluğu\n");
            }
            for (ParamJson p : b.signature().params()) {
                String extLine = typeLevelExtComment(p.ext(), ext.name() + "." + b.id() + "." + p.name(), report);
                if (!extLine.isEmpty()) {
                    sb.append("    // ").append(Naming.pascal(p.name())).append(": ")
                            .append(extLine.replaceFirst("^// ", ""));
                }
            }
            String ret = Naming.javaType(b.signature().returns(), false);
            sb.append("    ").append(ret).append(' ').append(Naming.camel(b.id())).append('(')
                    .append(boundaryOpParams(b)).append(");\n\n");
        }
        sb.append("}\n");
        sb.append(callsAndCompensateComments(ext.name(), "external", callEdges, report));
        return sb.toString();
    }

    /**
     * T4.5 Step 5.1 — saga süpürmesi (referans §1 {@code BoundaryFile} `:943-953`): {@code kind} +
     * {@code to.system} eşleşen her callEdge için trailing yorum + {@code realized("calls", from)};
     * compensate varsa AYRICA {@code realized("compensate", from)} + {@code saga-orchestration-state}
     * policy (ters-sıra/LIFO — doldurucu playbook'u).
     */
    private static String callsAndCompensateComments(String systemName, String kind, List<CallEdgeJson> callEdges,
            BuildReport report) {
        StringBuilder sb = new StringBuilder();
        for (CallEdgeJson ce : callEdges) {
            if (!kind.equals(ce.kind()) || !systemName.equals(ce.to().system())) {
                continue;
            }
            sb.append("\n// calls: ").append(ce.from()).append(" -> ").append(ce.to().system()).append('.')
                    .append(ce.to().op()).append(" (").append(ce.kind()).append(")\n");
            report.realized("calls", ce.from());
            if (ce.compensate() != null) {
                sb.append("// saga: compensate = ").append(ce.compensate().system()).append('.')
                        .append(ce.compensate().op())
                        .append(" (ters-sıra/LIFO — doldurucu playbook'u)\n");
                report.realized("compensate", ce.from());
                report.policy("saga-orchestration-state", "in-memory (generator-policy)");
            }
        }
        return sb.toString();
    }

    /**
     * Step 5.2 — human client seam ({@code src/main/java/app/boundary/{Ext}Client.java},
     * writeIfAbsent): {@code {Ext}} arayüzünü implemente eder; her metot birebir marker fırlatır
     * ({@code "{Ext}.{op}: doldurulacak"}).
     */
    private static String boundaryClientJava(ExternalJson ext) {
        StringBuilder sb = new StringBuilder();
        sb.append("package app.boundary;\n\n");
        String stdImports = boundaryOpImports(ext.operations());
        if (!stdImports.isEmpty()) {
            sb.append(stdImports).append('\n');
        }
        sb.append("// ").append(ext.name())
                .append(" dış-adapter (transport impl) — insan/LLM doldurur. gen ezmez (writeIfAbsent).\n");
        sb.append("public class ").append(ext.name()).append("Client implements ").append(ext.name())
                .append(" {\n\n");
        for (BoundaryOpJson b : ext.operations()) {
            String ret = Naming.javaType(b.signature().returns(), false);
            sb.append("    @Override\n");
            sb.append("    public ").append(ret).append(' ').append(Naming.camel(b.id())).append('(')
                    .append(boundaryOpParams(b)).append(") {\n");
            sb.append("        throw new UnsupportedOperationException(\"").append(ext.name()).append('.')
                    .append(b.id()).append(": doldurulacak\");\n");
            sb.append("    }\n\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Step 5.3 — caller-side boundary validation ({@code {pkg}/{owner}{OpPascal}Validation.java}):
     * boundary-op'ta validation varsa {@code validation{i}({tipli input record})} (tip çözümü
     * boundary-op signature paramlarından, T3.5 renderer). {@code owner} = external/uncharted adı;
     * external ve uncharted ops'ları için ortak (referans §1 {@code BoundaryValidation} paritesi).
     * validation boş/null ise {@code null} (dosya emit edilmez).
     */
    private static String boundaryValidationJava(String pkg, String owner, BoundaryOpJson b, BuildReport report) {
        List<GuardedExpr> validation = b.validation();
        if (validation == null || validation.isEmpty()) {
            return null;
        }
        String clsName = owner + Naming.pascal(b.id()) + "Validation";
        String key = owner + "." + b.id();
        Function<List<String>, String> resolveType = path -> {
            for (ParamJson p : b.signature().params()) {
                if (p.name().equals(path.get(0))) {
                    return p.type();
                }
            }
            return null;
        };
        List<String> methods = new ArrayList<>();
        List<String> records = new ArrayList<>();
        ImportFlags imports = new ImportFlags();
        int ok = 0;
        for (int i = 0; i < validation.size(); i++) {
            GuardedExpr g = validation.get(i);
            String inputName = clsName + i + "Input";
            try {
                ExprWalk.Result<String> res = JavaPredicateRenderer.render(g.ast(), resolveType);
                Map<String, String> hints = ExprWalk.inferLiteralTypes(g.ast());
                List<String> fields = new ArrayList<>();
                for (List<String> path : res.paths()) {
                    String type = JavaPredicateRenderer.javaFieldType(path, resolveType, hints);
                    imports.note(type);
                    fields.add(type + " " + ExprWalk.propName(path));
                }
                imports.noteExpr(res.expr());
                methods.add("    public static boolean validation" + i + "(" + inputName + " input) {\n"
                        + "        return " + res.expr() + ";\n    }\n\n");
                records.add("    public record " + inputName + "(" + String.join(", ", fields) + ") {\n    }\n\n");
                ok++;
            } catch (UnsupportedConstruct e) {
                report.unsupported("validation", key + "#Validation" + i, e.getMessage());
                methods.add("    public static boolean validation" + i + "() {\n"
                        + "        throw new UnsupportedOperationException(\"unsupported: "
                        + escapeJava(g.text()) + "\");\n    }\n\n");
            }
        }
        if (ok > 0) {
            report.realized("validation", key);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(";\n\n");
        sb.append(imports.render());
        sb.append("// caller-side validation (INV-4): ").append(key).append(" çağrılmadan önce doğrulanır.\n");
        sb.append("public final class ").append(clsName).append(" {\n\n");
        sb.append("    private ").append(clsName).append("() {\n    }\n\n");
        for (String m : methods) {
            sb.append(m);
        }
        for (String r : records) {
            sb.append(r);
        }
        sb.append("}\n");
        return sb.toString();
    }

    // ── Uncharted (T4.4; SPEC §6.3 Uncharted/{Name}.g.cs satırı; referans §1 `:1017-1071`) — external
    // gibi çağrı-adapter STUB (gen-owned, human seam DEĞİL — .NET paritesi) AMA kendi entity/type'larını
    // OWN eder (düz POJO — JPA'ya bağlanmaz). ──

    /**
     * Step 5.4 — {@code gen/java/app/uncharted/{Name}.java}: stub {@code {Name}} arayüzü +
     * (paket-özel) {@code {Name}StubClient} (UnsupportedOperationException; "doldurulacak" YOK —
     * gen-owned WriteAlways, human seam DEĞİL). {@code realized("uncharted", name)} +
     * boundary-op/serving alt-kayıtları (census şablonu {@link techgen.core.report.Completeness}
     * ile birebir); validation ayrı dosyada ({@link #boundaryValidationJava}).
     */
    private static String unchartedFile(UnchartedJson u, BuildReport report, List<CallEdgeJson> callEdges) {
        report.realized("uncharted", u.name());
        report.policy("uncharted-realization", "call-adapter stub + owned model (generator-policy)");
        StringBuilder sb = new StringBuilder();
        sb.append("package app.uncharted;\n\n");
        String stdImports = boundaryOpImports(u.operations());
        if (!stdImports.isEmpty()) {
            sb.append(stdImports).append('\n');
        }
        sb.append("// uncharted '").append(u.name())
                .append("' (generated:false): çağrı-adapter STUB + OWNED model (entity/type korunur).\n");
        if (u.deployable() != null) {
            sb.append("// deployable: ").append(u.deployable()).append('\n');
        }
        sb.append('\n');
        sb.append("public interface ").append(u.name()).append(" {\n\n");
        for (BoundaryOpJson b : u.operations()) {
            report.realized("boundary-op", u.name() + "." + b.id());
            for (ServingJson s : b.serving() == null ? List.<ServingJson>of() : b.serving()) {
                report.realized("serving", u.name() + "." + b.id() + ":" + s.protocol());
                sb.append("    // serving: ").append(s.raw()).append(" — transport client sorumluluğu\n");
            }
            // T4.5 Step 5.4 — boundary-op param-ext (owner "{u}.{op}.{param}").
            for (ParamJson p : b.signature().params()) {
                String extLine = typeLevelExtComment(p.ext(), u.name() + "." + b.id() + "." + p.name(), report);
                if (!extLine.isEmpty()) {
                    sb.append("    // ").append(Naming.pascal(p.name())).append(": ")
                            .append(extLine.replaceFirst("^// ", ""));
                }
            }
            String ret = Naming.javaType(b.signature().returns(), false);
            sb.append("    ").append(ret).append(' ').append(Naming.camel(b.id())).append('(')
                    .append(boundaryOpParams(b)).append(");\n\n");
        }
        sb.append("}\n\n");
        sb.append("// altyapı stub — human-seam DEĞİL (gen-owned, WriteAlways); çağrı-adapter henüz eklenmedi.\n");
        sb.append("final class ").append(u.name()).append("StubClient implements ").append(u.name())
                .append(" {\n\n");
        for (BoundaryOpJson b : u.operations()) {
            String ret = Naming.javaType(b.signature().returns(), false);
            sb.append("    @Override\n");
            sb.append("    public ").append(ret).append(' ').append(Naming.camel(b.id())).append('(')
                    .append(boundaryOpParams(b)).append(") {\n");
            sb.append("        throw new UnsupportedOperationException(\"").append(u.name()).append('.')
                    .append(b.id()).append(": uncharted stub — çağrı-adapter henüz eklenmedi\");\n");
            sb.append("    }\n\n");
        }
        sb.append("}\n");
        // T4.5 Step 5.1 — bu uncharted'i hedefleyen callEdge'ler (kind==uncharted): saga süpürmesi.
        sb.append(callsAndCompensateComments(u.name(), "uncharted", callEdges, report));
        return sb.toString();
    }

    /**
     * Step 5.4 — OWNED entity POJO ({@code gen/java/app/uncharted/{Name}{Entity}.java}): düz sınıf
     * (JPA'ya BAĞLANMAZ — {@code @Entity} yok). optimistic concurrency → plain {@code version} alanı
     * ({@code @Version} YOK) + {@code realized("concurrency", "{Name}.{Entity}")}.
     */
    private static String unchartedEntityJava(UnchartedJson u, UnchartedEntity e, BuildReport report) {
        boolean optimistic = "optimistic".equals(e.concurrency());
        String clsName = u.name() + e.id();
        StringBuilder sb = new StringBuilder();
        sb.append("package app.uncharted;\n\n");
        String imports = entityFieldImports(e.fields());
        sb.append(imports);
        if (!imports.isEmpty()) {
            sb.append('\n');
        }
        sb.append("// uncharted owned POJO (").append(u.name()).append('.').append(e.id())
                .append("): JPA'ya BAĞLANMAZ (persist sorumluluğu bizde değil).\n");
        sb.append("public class ").append(clsName).append(" {\n\n");
        for (EntityFieldJson f : e.fields()) {
            sb.append("    private ").append(Naming.javaType(f.type(), f.collection())).append(' ')
                    .append(Naming.camel(f.name())).append(";\n\n");
        }
        if (optimistic) {
            report.realized("concurrency", u.name() + "." + e.id());
            sb.append("    private long version;\n\n");
        }
        for (EntityFieldJson f : e.fields()) {
            appendAccessors(sb, Naming.javaType(f.type(), f.collection()), Naming.camel(f.name()));
        }
        if (optimistic) {
            appendAccessors(sb, "long", "version");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Step 5.4 — OWNED type POJO ({@code gen/java/app/uncharted/{Name}{TypeId}.java}): enum → Java
     * {@code enum}; diğer kind → düz {@code record} (census'ta ayrı iz gerektirmez — referans §1
     * uncharted disposition'ı yalnız uncharted/boundary-op/validation/serving/concurrency izler).
     */
    private static String unchartedTypeJava(UnchartedJson u, UnchartedType t) {
        String clsName = u.name() + t.id();
        StringBuilder sb = new StringBuilder();
        sb.append("package app.uncharted;\n\n");
        if ("enum".equals(t.kind())) {
            List<String> values = t.values() == null ? List.of() : t.values();
            sb.append("// uncharted owned POJO (").append(u.name()).append('.').append(t.id()).append(").\n");
            sb.append("public enum ").append(clsName).append(" { ").append(String.join(", ", values))
                    .append(" }\n");
            return sb.toString();
        }
        List<FieldJson> fields = t.fields() == null ? List.of() : t.fields();
        String imports = typeFieldImports(fields);
        String components = fields.stream()
                .map(f -> Naming.javaType(f.type(), f.collection()) + " " + Naming.camel(f.name()))
                .collect(Collectors.joining(", "));
        sb.append(imports);
        if (!imports.isEmpty()) {
            sb.append('\n');
        }
        sb.append("// uncharted owned POJO (").append(u.name()).append('.').append(t.id()).append(").\n");
        sb.append("public record ").append(clsName).append('(').append(components).append(") {\n}\n");
        return sb.toString();
    }
}
