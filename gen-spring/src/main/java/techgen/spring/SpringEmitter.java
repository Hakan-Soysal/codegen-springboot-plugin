package techgen.spring;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import techgen.core.errors.UnsupportedConstruct;
import techgen.core.gm.GenerationModel;
import techgen.core.gm.GmOperation;
import techgen.core.model.Abac;
import techgen.core.model.EntityFieldJson;
import techgen.core.model.EntityJson;
import techgen.core.model.ErrorJson;
import techgen.core.model.EventJson;
import techgen.core.model.ExprNode;
import techgen.core.model.ExtJson;
import techgen.core.model.FieldJson;
import techgen.core.model.GuardedExpr;
import techgen.core.model.ModuleDecl;
import techgen.core.model.OperationJson;
import techgen.core.model.ParamJson;
import techgen.core.model.ServingArg;
import techgen.core.model.ServingJson;
import techgen.core.model.TypeJson;
import techgen.core.predicate.ExprWalk;
import techgen.core.report.BuildReport;

import java.util.ArrayList;
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

        // ── PersistenceConfig (T3.4 §5.2; SPEC §6.3 AppDbContext.g.cs satırı) — yalnız entities>0 ──
        if (!gm.entities().isEmpty()) {
            writer.writeAlways("gen/java/app/PersistenceConfig.java", persistenceConfigJava());
        }

        // ── modül döngüsü (sonraki task'lar bu döngüye op/entity emisyonu ekleyecek) ──
        for (ModuleDecl module : gm.modules()) {
            String pkg = Naming.packageOf(module.name());
            String cls = Naming.pascal(module.name()) + "Wiring";

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
                writer.writeAlways("gen/java/app/" + pkg + "/" + entity.id() + ".java", entityJava(entity, report));
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
                String retType = Naming.javaType(op.op().signature().returns(), false);
                List<RepoDep> deps = repoDeps(op, gm);

                // Step 5.1 — request record.
                writer.writeAlways(genSliceDir + "/" + reqName + ".java",
                        requestRecordJava(op, gm, slicePkg, reqName, report));

                // Step 5.2 — HandlerBase (abstract, gen-owned).
                writer.writeAlways(genSliceDir + "/" + opId + "HandlerBase.java",
                        handlerBaseJava(op, gm, slicePkg, reqName, retType, deps));

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
                    report.realized("serving", opId + ":rest");
                }

                // Step 5.5 — Wiring bean kayıtları (TAM-AÇIK KAYIT, SPEC §12/4) biriktir.
                String depParams = deps.stream()
                        .map(d -> d.entityId() + "Repository " + d.fieldName())
                        .collect(Collectors.joining(", "));
                String depArgs = deps.stream().map(RepoDep::fieldName).collect(Collectors.joining(", "));
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
            }
            writer.writeAlways("gen/java/app/" + pkg + "/" + cls + ".java",
                    moduleWiring(module, wiringImports, wiringBeans.toString(), anyBean));
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

    /** dbProvider whitelist → provider driver bağımlılığı (SPEC §6.6). Policy detayı T5.1'de. */
    private static String providerDependencyXml(GenConfig config, BuildReport report) {
        String provider = config == null ? null : config.dbProvider();
        if (provider == null) {
            return "    <!-- dbProvider tanımlı değil: provider seam — gen.config.json'a dbProvider ekleyin -->\n";
        }
        return switch (provider) {
            case "h2", "inmemory" -> """
                        <dependency>
                          <groupId>com.h2database</groupId>
                          <artifactId>h2</artifactId>
                          <scope>runtime</scope>
                        </dependency>
                    """;
            case "postgres" -> """
                        <dependency>
                          <groupId>org.postgresql</groupId>
                          <artifactId>postgresql</artifactId>
                          <scope>runtime</scope>
                        </dependency>
                    """;
            case "sqlserver" -> """
                        <dependency>
                          <groupId>com.microsoft.sqlserver</groupId>
                          <artifactId>mssql-jdbc</artifactId>
                          <scope>runtime</scope>
                        </dependency>
                    """;
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
        // events>0 → EventBus bean kaydı (.NET Bootstrap.g.cs `AddScoped<IEventBus, OutboxEventBus>` paritesi).
        boolean eventBusBean = !gm.events().isEmpty();
        String beanImport = eventBusBean ? "import org.springframework.context.annotation.Bean;\n" : "";
        String eventBusMethod = eventBusBean
                ? """

                        @Bean
                        public EventBus eventBus() {
                            return new OutboxEventBus();
                        }
                    """
                : "";
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
        for (ParamJson p : params) {
            String javaType = Naming.javaType(p.type(), p.collection());
            javaTypes.add(javaType);
            String custom = customTypeImport(p.type(), gm);
            if (custom != null) {
                customImports.add(custom);
            }
            components.add(javaType + " " + Naming.camel(p.name()));
        }
        String stdImports = paramImports(javaTypes);
        String customImportsBlock = String.join("", customImports);

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(slicePkg).append(";\n\n");
        sb.append(stdImports);
        sb.append(customImportsBlock);
        if (!stdImports.isEmpty() || !customImportsBlock.isEmpty()) {
            sb.append('\n');
        }
        sb.append("// visibility: ").append(o.visibility()).append('\n');
        if (o.note() != null) {
            sb.append("/**\n * ").append(escapeJavadoc(o.note())).append("\n */\n");
            report.realized("note", op.id());
        }
        sb.append("public record ").append(reqName).append('(').append(String.join(", ", components))
                .append(") {\n}\n");

        report.realized("operation", op.id());
        report.realized("visibility", op.id());
        return sb.toString();
    }

    /**
     * Step 5.2 — HandlerBase (Generation Gap, gen-owned, abstract): DI alanları (repository'ler) +
     * ctor + gövdesiz {@code execute}. Guards çağrı sırası (skill canonicalOrder, SPEC §6.3/§10)
     * yalnız Javadoc referansı — çağrı T3.6 dışı.
     */
    private static String handlerBaseJava(GmOperation op, GenerationModel gm, String slicePkg, String reqName,
            String retType, List<RepoDep> deps) {
        String opId = op.id();
        StringBuilder imports = new StringBuilder("import app.Result;\n");
        String retImport = customTypeImport(op.op().signature().returns(), gm);
        imports.append(repoImportsBlock(deps));
        if (retImport != null) {
            imports.append(retImport);
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

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(slicePkg).append(";\n\n");
        sb.append(imports);
        sb.append('\n');
        sb.append("/**\n");
        sb.append(" * Generation Gap taban sınıf (abstract; SPEC §6.2) — DI alanları burada; sonraki\n");
        sb.append(" * task'lar (idempotency/authz/external/event/vb.) kendi bölümlerini ekler.\n");
        sb.append(" * Guard çağrı sırası kanonik (skill canonicalOrder, çağrı T3.6 dışı — yalnız referans):\n");
        sb.append(" * idempotency -&gt; authz -&gt; validation -&gt; external-input -&gt; rule -&gt;\n");
        sb.append(" * entity+invariant -&gt; persist -&gt; emit -&gt; return.\n");
        sb.append(" */\n");
        sb.append("public abstract class ").append(opId).append("HandlerBase {\n\n");
        sb.append(fields);
        if (!deps.isEmpty()) {
            sb.append('\n');
        }
        sb.append("    protected ").append(opId).append("HandlerBase(").append(String.join(", ", ctorParams))
                .append(") {\n");
        sb.append(ctorAssigns);
        sb.append("    }\n\n");
        sb.append("    public abstract Result<").append(retType).append("> execute(").append(reqName)
                .append(" request);\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Step 5.3 — human seam ({@code {Op}Handler}, writeIfAbsent): HandlerBase'i extend eder;
     * {@code execute} gövdesi birebir marker fırlatır (skill tespiti substring {@code doldurulacak}).
     */
    private static String humanHandlerJava(GmOperation op, GenerationModel gm, String slicePkg, String reqName,
            String retType, List<RepoDep> deps) {
        String opId = op.id();
        StringBuilder imports = new StringBuilder("import app.Result;\n");
        String retImport = customTypeImport(op.op().signature().returns(), gm);
        imports.append(repoImportsBlock(deps));
        if (retImport != null) {
            imports.append(retImport);
        }
        String ctorParams = deps.stream().map(d -> d.entityId() + "Repository " + d.fieldName())
                .collect(Collectors.joining(", "));
        String superArgs = deps.stream().map(RepoDep::fieldName).collect(Collectors.joining(", "));

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
        String components = fields.stream()
                .map(f -> Naming.javaType(f.type(), f.collection()) + " " + Naming.camel(f.name()))
                .collect(Collectors.joining(", "));
        return "package app." + pkg + ";\n\n"
                + imports
                + (imports.isEmpty() ? "" : "\n")
                + extComment
                + "public record " + type.id() + "(" + components + ") {\n}\n";
    }

    /** Ext realizasyonu YALNIZ type-seviyesinde (owner={@code typeId}); field ext T4.5 sweep'inde kalır. */
    private static String typeLevelExtComment(List<ExtJson> ext, String owner, BuildReport report) {
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
        return "// ext: " + names + " (realizasyon = policy)\n";
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
        String components = payload.stream()
                .map(f -> Naming.javaType(f.type(), f.collection()) + " " + Naming.camel(f.name()))
                .collect(Collectors.joining(", "));
        return "package app." + pkg + ";\n\n"
                + imports
                + (imports.isEmpty() ? "" : "\n")
                + "public record " + event.id() + "(" + components + ") {\n}\n";
    }

    // ── Entity → @Entity (T3.4 §5.1; SPEC §6.3 Entities.g.cs satırı; referans §1 `:755-787`).
    // Mutable sınıf (record DEĞİL — JPA @Version alanı setter ister). Field-level ext (T4.5 sweep
    // kapsamı, §5.4 dışı) ve invariants (T3.5) BURADA emit edilmez. ──

    private static String entityJava(EntityJson entity, BuildReport report) {
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
        imports.append(entityFieldImports(fields));

        StringBuilder sb = new StringBuilder();
        sb.append("package app.").append(Naming.packageOf(entity.module())).append(";\n\n");
        sb.append(imports).append('\n');
        sb.append(typeLevelExtComment(entity.ext(), entity.id(), report));
        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(entity.id()).append("\")\n");
        sb.append("public class ").append(entity.id()).append(" {\n\n");

        for (EntityFieldJson f : fields) {
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

    /** Entity alan tiplerine göre gereken import satırları ({@link #typeFieldImports} ile aynı sıra). */
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
}
