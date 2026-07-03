package techgen.spring;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import techgen.core.gm.GenerationModel;
import techgen.core.model.ContractFile;
import techgen.core.model.ManifestJson;
import techgen.core.pipeline.GmBuilder;
import techgen.core.pipeline.Loader;
import techgen.core.report.BuildReport;
import techgen.core.report.Completeness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T6.3 §M6 — studyo ölçek smoke: gerçek-dünya boyutlu {@code fixtures/studyo.manifest.json}
 * fixture'ının emisyonu + INV-7 (silentDrop=0) + GERÇEK {@code mvn compile} yeşil. Amaç: küçük
 * (invoice) fixture'ın ötesinde, çok modüllü/çok operasyonlu bir manifest'in uçtan uca üretilebilir
 * ve derlenebilir olduğunu kanıtlamak. {@code @Tag("e2e")} — CI-dışı hızlı koşumdan ayrık tutulur
 * (gerçek {@code mvn} alt-süreci çalıştırır; {@link GeneratedAppCompileTest} ile aynı desen).
 */
class StudyoScaleE2ETest {

    private static Path fixture(String name) {
        Path fromModuleDir = Path.of("../fixtures", name);
        if (Files.exists(fromModuleDir)) {
            return fromModuleDir;
        }
        return Path.of("fixtures", name);
    }

    private static ManifestJson studyoManifest() {
        return Loader.loadManifest(fixture("studyo.manifest.json"));
    }

    private static GenerationModel studyoGm(ManifestJson manifest) {
        ContractFile contract = Loader.loadContract(fixture("studyo.manifest.json"), manifest.contract());
        return GmBuilder.build(manifest, contract);
    }

    private static GenConfig h2Config() {
        return new GenConfig("h2", "inmemory");
    }

    @Test
    @Tag("e2e")
    void studyoManifest_emitsWithZeroSilentDrop_andRealMvnCompileExitsZero(@TempDir Path outDir)
            throws IOException, InterruptedException {
        ManifestJson manifest = studyoManifest();
        GenerationModel gm = studyoGm(manifest);
        BuildReport report = new BuildReport();

        long startNanos = System.nanoTime();
        SpringEmitter.emit(gm, outDir, report, h2Config());
        long emitMillis = (System.nanoTime() - startNanos) / 1_000_000;

        Completeness.check(manifest, report);

        List<BuildReport.BuildEntry> drops = report.silentDrops();
        String diagnosis = drops.stream()
                .map(e -> e.construct() + " / " + e.id())
                .collect(Collectors.joining("\n"));
        assertTrue(drops.isEmpty(), "studyo fixture: silentDrop OLMAMALI (INV-7); düşenler:\n" + diagnosis);

        long fileCount;
        try (Stream<Path> walk = Files.walk(outDir)) {
            fileCount = walk.filter(Files::isRegularFile).count();
        }
        System.out.println(
                "[T6.3] studyo emit süresi=" + emitMillis + "ms, üretilen dosya sayısı=" + fileCount);

        Path humanPom = outDir.resolve("pom.xml");
        ProcessBuilder pb = new ProcessBuilder("mvn", "-q", "-f", humanPom.toString(), "compile");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "studyo fixture: mvn compile exit 0 bekleniyor; çıktı:\n" + output);
    }
}
