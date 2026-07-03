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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * T6.1 — refactor güvenlik ağı (davranış sözleşmesi §9; referans
 * {@code docs/referans/conformance-testler-skill-sozlesmesi.md} §B; CoreTemplate1
 * {@code CharacterizationTests.cs} karşılığı, Java hedefine uyarlanmış + iki-dizin
 * byte-determinizm testi eklenmiş).
 *
 * <p>Fixture'ın TÜM emit ağacının (her dosya yolu + içerik SHA-256) snapshot'ını commit'li
 * golden ile karşılaştırır (INV-D). Golden'ı kasıtlı değiştirdiğinde yeniden üret:
 * {@code UPDATE_GOLDEN=1 mvn -q -pl gen-spring test -Dtest=CharacterizationTest}.</p>
 */
class CharacterizationTest {

    private static Path fixture(String name) {
        Path fromModuleDir = Path.of("../fixtures", name);
        if (Files.exists(fromModuleDir)) {
            return fromModuleDir;
        }
        return Path.of("fixtures", name);
    }

    private static ManifestJson fixtureManifest() {
        return Loader.loadManifest(fixture("manifest.json"));
    }

    private static GenerationModel fixtureGm(ManifestJson manifest) {
        ContractFile contract = Loader.loadContract(fixture("manifest.json"), manifest.contract());
        return GmBuilder.build(manifest, contract);
    }

    private static GenConfig h2Config() {
        return new GenConfig("h2", "inmemory");
    }

    @Test
    void emit_tree_matches_golden_snapshot(@TempDir Path outDir) throws IOException {
        ManifestJson manifest = fixtureManifest();
        GenerationModel gm = fixtureGm(manifest);
        SpringEmitter.emit(gm, outDir, new BuildReport(), h2Config());

        String actual = GoldenSupport.snapshot(outDir);
        String diff = GoldenSupport.compareOrUpdate(actual);
        if (diff != null) {
            fail(diff);
        }
    }

    @Test
    void unchanged_input_does_not_rewrite_generated_file(@TempDir Path outDir) throws IOException {
        ManifestJson manifest = fixtureManifest();
        GenerationModel gm = fixtureGm(manifest);
        SpringEmitter.emit(gm, outDir, new BuildReport(), h2Config());

        // mtime'ı bilinen geçmişe çek (Thread.sleep YOK — flake yasağı); içerik aynıysa ikinci
        // emit DOKUNMAMALI (write-only-if-changed, INV-D).
        Path f = outDir.resolve("gen/java/app/Result.java");
        assertTrue(Files.exists(f), "ön koşul: Result.java üretildi");
        FileTime past = FileTime.from(Instant.parse("2001-01-01T00:00:00Z"));
        Files.setLastModifiedTime(f, past);

        SpringEmitter.emit(gm, outDir, new BuildReport(), h2Config());

        assertEquals(past, Files.getLastModifiedTime(f), "yeniden yazılmadı → mtime korundu");
    }

    @Test
    void removed_operation_prunes_generated_but_keeps_human_logic(@TempDir Path outDir) throws IOException {
        ManifestJson manifest = fixtureManifest();
        GenerationModel gm = fixtureGm(manifest);
        SpringEmitter.emit(gm, outDir, new BuildReport(), h2Config());

        Path genSliceDir = outDir.resolve("gen/java/app/billing/getinvoice");
        Path humanHandler = outDir.resolve("src/main/java/app/billing/getinvoice/GetInvoiceHandler.java");
        assertTrue(Files.exists(genSliceDir), "ön koşul: GetInvoice gen slice üretildi");
        assertTrue(Files.exists(humanHandler), "ön koşul: GetInvoiceHandler.java üretildi");

        String humanMarker = "// HUMAN BODY — elle yazılmış iş mantığı\n";
        Files.writeString(humanHandler, humanMarker);

        // GetInvoice manifest'ten çıkarıldı (in-memory mutasyon) → yeniden üret.
        GenerationModel trimmed = new GenerationModel(
                gm.mode(), gm.modules(),
                gm.operations().stream().filter(o -> !o.id().equals("GetInvoice")).toList(),
                gm.entities(), gm.types(), gm.events(), gm.subscriptions(), gm.errors(),
                gm.externals(), gm.callEdges(), gm.deployables(), gm.uncharted(), gm.env(), gm.testPlan());
        SpringEmitter.emit(trimmed, outDir, new BuildReport(), h2Config());

        assertFalse(Files.exists(genSliceDir), "orphan gen slice prune edilmeli (manifest-diff)");
        assertTrue(Files.exists(humanHandler), "human handler korunmalı — orphan, asla auto-silinmez");
        assertEquals(humanMarker, Files.readString(humanHandler), "human İÇERİK de korunmalı (yalnız varlık değil)");
    }

    @Test
    void human_shell_survives_regeneration(@TempDir Path outDir) throws IOException {
        ManifestJson manifest = fixtureManifest();
        GenerationModel gm = fixtureGm(manifest);
        SpringEmitter.emit(gm, outDir, new BuildReport(), h2Config());

        // insan shell'i düzenler (bağımlılık eklemek, custom config vb.)
        Path pom = outDir.resolve("pom.xml");
        Path appJava = outDir.resolve("src/main/java/app/Application.java");
        Path appYml = outDir.resolve("src/main/resources/application.yml");
        String humanPomMarker = "<!-- HUMAN: custom packages -->\n";
        String humanAppMarker = "// HUMAN: custom pipeline\n";
        String humanYmlMarker = "# HUMAN: custom config\n";
        Files.writeString(pom, humanPomMarker);
        Files.writeString(appJava, humanAppMarker);
        Files.writeString(appYml, humanYmlMarker);

        SpringEmitter.emit(gm, outDir, new BuildReport(), h2Config());   // regen

        assertEquals(humanPomMarker, Files.readString(pom), "pom.xml (HumanShell) ezilmedi");
        assertEquals(humanAppMarker, Files.readString(appJava), "Application.java (HumanShell) ezilmedi");
        assertEquals(humanYmlMarker, Files.readString(appYml), "application.yml (HumanShell) ezilmedi");

        // ama gen-owned parent POM HER ZAMAN üreteç içeriğiyle yeniden yazılır (shell ona güvenir).
        String parentPom = Files.readString(outDir.resolve("gen/parent/pom.xml"));
        assertTrue(parentPom.contains("AUTO-GENERATED by techgen-spring"),
                "gen/parent/pom.xml üreteç-sahibi içerikle yeniden üretilmeli");
    }

    @Test
    void generated_tree_is_byte_deterministic(@TempDir Path dirA, @TempDir Path dirB) throws IOException {
        ManifestJson manifest = fixtureManifest();
        GenerationModel gm = fixtureGm(manifest);

        SpringEmitter.emit(gm, dirA, new BuildReport(), h2Config());
        SpringEmitter.emit(gm, dirB, new BuildReport(), h2Config());

        String snapshotA = GoldenSupport.snapshot(dirA);
        String snapshotB = GoldenSupport.snapshot(dirB);

        assertEquals(snapshotA, snapshotB, "iki AYRI dizine emit byte-özdeş olmalı (INV-D)");
    }
}
