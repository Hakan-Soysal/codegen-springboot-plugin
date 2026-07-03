package techgen.spring;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import techgen.core.gm.GenerationModel;
import techgen.core.model.EntityFieldJson;
import techgen.core.model.EntityJson;
import techgen.core.model.ErrorJson;
import techgen.core.model.EventJson;
import techgen.core.model.ExtJson;
import techgen.core.model.FieldJson;
import techgen.core.model.ModuleDecl;
import techgen.core.model.TypeJson;
import techgen.core.report.BuildReport;

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
            writer.writeAlways("gen/java/app/" + pkg + "/" + cls + ".java", moduleWiring(module));

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

            // ── Entities → @Entity + JpaRepository (T3.4 §5.1-5.2) ──
            for (EntityJson entity : gm.entities()) {
                if (!entity.module().equals(module.name())) {
                    continue;
                }
                writer.writeAlways("gen/java/app/" + pkg + "/" + entity.id() + ".java", entityJava(entity, report));
                writer.writeAlways("gen/java/app/" + pkg + "/" + entity.id() + "Repository.java",
                        repositoryJava(entity));
            }
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

    private static String moduleWiring(ModuleDecl module) {
        String pkg = Naming.packageOf(module.name());
        String cls = Naming.pascal(module.name()) + "Wiring";
        return """
                package app.%s;

                import org.springframework.context.annotation.Configuration;

                @Configuration
                public class %s {
                    // op kayıtları: T3.6+
                }
                """.formatted(pkg, cls);
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
