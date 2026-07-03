package techgen.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import techgen.core.gm.GenerationModel;
import techgen.core.model.ContractFile;
import techgen.core.model.ManifestJson;
import techgen.core.pipeline.GmBuilder;
import techgen.core.pipeline.Loader;
import techgen.core.report.BuildReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3.2 — App iskeleti emit smoke testi (davranış sözleşmesi §6.1-6.2): parent POM + HumanShell
 * üçlüsü + GeneratedBootstrap + modül Wiring'leri; ezme/ezmeme asimetrisi; gerçek {@code mvn
 * validate} ile POM zincirinin geçerliliği (bkz. tasks/T3-2-app-iskeleti.md §6.2 — "gerçek koşum").
 */
class AppSkeletonTest {

    private static Path fixture(String name) {
        Path fromModuleDir = Path.of("../fixtures", name);
        if (Files.exists(fromModuleDir)) {
            return fromModuleDir;
        }
        return Path.of("fixtures", name);
    }

    private static GenerationModel fixtureGm() {
        Path manifestPath = fixture("manifest.json");
        ManifestJson manifest = Loader.loadManifest(manifestPath);
        ContractFile contract = Loader.loadContract(manifestPath, manifest.contract());
        return GmBuilder.build(manifest, contract);
    }

    private static GenConfig h2Config() {
        return new GenConfig("h2", "inmemory");
    }

    @Test
    void emit_writesAppSkeletonFiles(@TempDir Path outDir) throws IOException {
        GenerationModel gm = fixtureGm();
        BuildReport report = new BuildReport();

        SpringEmitter.emit(gm, outDir, report, h2Config());

        assertTrue(Files.exists(outDir.resolve("gen/parent/pom.xml")), "üreteç-sahibi parent POM");
        assertTrue(Files.exists(outDir.resolve("pom.xml")), "HumanShell pom.xml");
        assertTrue(Files.exists(outDir.resolve("src/main/java/app/Application.java")), "HumanShell Application.java");
        assertTrue(Files.exists(outDir.resolve("src/main/resources/application.yml")), "HumanShell application.yml");
        assertTrue(Files.exists(outDir.resolve("gen/java/app/GeneratedBootstrap.java")), "GeneratedBootstrap");
        assertTrue(Files.exists(outDir.resolve("gen/java/app/billing/BillingWiring.java")), "Billing modül Wiring");
        assertTrue(Files.exists(outDir.resolve("gen/java/app/ops/OpsWiring.java")), "Ops modül Wiring");

        // her modül için report.realized("module", ...) (davranış sözleşmesi §6.1 Step 5.5).
        assertTrue(report.entries().stream()
                        .anyMatch(e -> e.construct().equals("module") && e.id().equals("Billing")
                                && e.status() == BuildReport.ConstructStatus.REALIZED),
                "Billing modülü realized olarak raporlanmalı");
        assertTrue(report.entries().stream()
                        .anyMatch(e -> e.construct().equals("module") && e.id().equals("Ops")
                                && e.status() == BuildReport.ConstructStatus.REALIZED),
                "Ops modülü realized olarak raporlanmalı");
    }

    @Test
    void emit_secondRun_humanShellTrioIsNeverOverwritten(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());
        Path humanPom = outDir.resolve("pom.xml");
        Path application = outDir.resolve("src/main/java/app/Application.java");
        Path applicationYml = outDir.resolve("src/main/resources/application.yml");
        Files.writeString(humanPom, "<!-- elle bozuldu -->", StandardCharsets.UTF_8);
        Files.writeString(application, "// elle bozuldu", StandardCharsets.UTF_8);
        Files.writeString(applicationYml, "# elle bozuldu", StandardCharsets.UTF_8);

        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        assertEquals("<!-- elle bozuldu -->", Files.readString(humanPom, StandardCharsets.UTF_8),
                "HumanShell pom.xml ikinci emit'te EZİLMEMELİ (writeIfAbsent)");
        assertEquals("// elle bozuldu", Files.readString(application, StandardCharsets.UTF_8),
                "HumanShell Application.java ikinci emit'te EZİLMEMELİ (writeIfAbsent)");
        assertEquals("# elle bozuldu", Files.readString(applicationYml, StandardCharsets.UTF_8),
                "HumanShell application.yml ikinci emit'te EZİLMEMELİ (writeIfAbsent)");
    }

    @Test
    void emit_secondRun_parentPomIsAlwaysOverwritten(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());
        Path parentPom = outDir.resolve("gen/parent/pom.xml");
        String original = Files.readString(parentPom, StandardCharsets.UTF_8);
        Files.writeString(parentPom, "<!-- elle bozuldu -->", StandardCharsets.UTF_8);

        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String restored = Files.readString(parentPom, StandardCharsets.UTF_8);
        assertEquals(original, restored, "üreteç-sahibi parent POM ikinci emit'te EZİLMELİ (writeAlways)");
    }

    /**
     * Bu task'ta {@code mvn compile} beklenmez (handler'lar yok — derleme T3.6 sonunda anlamlı)
     * ama {@code mvn validate} gerçek koşumla exit 0 vermeli (POM zinciri: human pom → generated-parent
     * → spring-boot-starter-parent geçerli).
     */
    @Test
    void generatedPom_realMavenValidate_exitsZero(@TempDir Path outDir) throws IOException, InterruptedException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());
        Path humanPom = outDir.resolve("pom.xml");

        ProcessBuilder pb = new ProcessBuilder("mvn", "-q", "-f", humanPom.toString(), "validate");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "mvn validate exit 0 bekleniyor; çıktı:\n" + output);
    }
}
