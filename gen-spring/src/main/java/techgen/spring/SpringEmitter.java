package techgen.spring;

import java.io.IOException;
import java.nio.file.Path;

import techgen.core.gm.GenerationModel;
import techgen.core.model.ModuleDecl;
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

        // ── modül döngüsü (sonraki task'lar bu döngüye op/entity/type emisyonu ekleyecek) ──
        for (ModuleDecl module : gm.modules()) {
            String pkg = Naming.packageOf(module.name());
            String cls = Naming.pascal(module.name()) + "Wiring";
            writer.writeAlways("gen/java/app/" + pkg + "/" + cls + ".java", moduleWiring(module));
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
        return """
                package app;

                import org.springframework.context.annotation.Configuration;
                import org.springframework.context.annotation.Import;
                %s
                @Configuration
                @Import({%s})
                public class GeneratedBootstrap {
                }
                """.formatted(imports, importedClasses);
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
}
