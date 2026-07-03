package techgen.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import techgen.core.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * T5.1 — gen-cli uçtan-uca akış + INV-7 exit sözleşmesi (SPEC §7; referans
 * {@code docs/referans/gen-dotnet-emisyon-sozlesmesi.md} §6). Exit ikiliği İKİ ayrı senaryoyla
 * kanıtlanır: silentDrop → exit 1 (gerçek gate yolu, mock YOK); whitelist-dışı dbProvider →
 * unsupported + exit 0 (drop DEĞİL). LoadError/JoinError ayrıca exit 2.
 */
class MainTest {

    private static final Pattern CONSOLE_LINE =
            Pattern.compile("^emit → .+ {2}\\(clean=(true|false), constructs=\\d+, silentDrops=\\d+\\)$");

    private static Path fixture(String name) {
        Path fromModuleDir = Path.of("../fixtures", name);
        if (Files.exists(fromModuleDir)) {
            return fromModuleDir;
        }
        return Path.of("fixtures", name);
    }

    /** stdout'u yakalar; {@code body} çalışırken üretilen çıktıyı döner. */
    private interface ThrowingSupplier {
        int run() throws IOException;
    }

    private record Captured(int exit, String stdout, String stderr) {
    }

    private static Captured capture(ThrowingSupplier body) throws IOException {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(outBuf, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(errBuf, true, StandardCharsets.UTF_8));
            int exit = body.run();
            return new Captured(exit, outBuf.toString(StandardCharsets.UTF_8), errBuf.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    // ── Pozitif: fixture manifest → exit 0; build-report/provenance yazılır; konsol biçimi ──

    @Test
    void fixtureManifest_exitsZero_withReportsAndConsoleFormat(@TempDir Path outDir) throws IOException {
        Path manifest = fixture("manifest.json");
        Captured c = capture(() -> Main.run(new String[] {manifest.toString(), outDir.toString()}));

        assertEquals(0, c.exit(), "stderr:\n" + c.stderr());
        assertTrue(Files.exists(outDir.resolve("build-report.json")), "build-report.json yok");
        assertTrue(Files.exists(outDir.resolve("provenance.json")), "provenance.json yok");

        String firstLine = c.stdout().lines().findFirst().orElse("");
        assertTrue(CONSOLE_LINE.matcher(firstLine).matches(),
                "konsol biçimi uymuyor: [" + firstLine + "]");
    }

    // ── Sentetik drop: gate'in GERÇEK yolu — census sayar ama emitter modül-döngüsünde asla
    // ziyaret edilmeyen bir tip (module alanı manifest.modules'ta bildirilmemiş) → SILENT_DROP. ──

    @Test
    void orphanModuleType_isRealSilentDrop_exitsOneWithDropLine(@TempDir Path work, @TempDir Path outDir)
            throws IOException {
        Path manifestSrc = fixture("manifest.json");
        Path contractSrc = fixture("operations.json");
        Files.copy(contractSrc, work.resolve("operations.json"));

        JsonNode root = Json.mapper().readTree(Files.readString(manifestSrc));
        ArrayNode types = (ArrayNode) root.get("types");
        ObjectNode ghost = Json.mapper().createObjectNode();
        ghost.put("id", "GhostType");
        // "GhostModule" manifest.modules'ta YOK -> SpringEmitter'ın per-modül döngüsü bu tipi
        // asla ziyaret etmez (hiçbir module.name() eşleşmez) -> report.realized/unsupported hiç
        // çağrılmaz -> Completeness census bunu sayar (kind="composite") -> INV-7 SILENT_DROP.
        ghost.put("module", "GhostModule");
        ghost.put("kind", "composite");
        types.add(ghost);
        Path manifestDst = work.resolve("manifest.json");
        Files.writeString(manifestDst, Json.mapper().writeValueAsString(root));

        Captured c = capture(() -> Main.run(new String[] {manifestDst.toString(), outDir.toString()}));

        assertEquals(1, c.exit(), "stdout:\n" + c.stdout() + "\nstderr:\n" + c.stderr());
        assertTrue(c.stdout().contains("⚠ SESSİZ DROP: composite / GhostType"),
                "drop satırı yok:\n" + c.stdout());
    }

    // ── gen.config varyantları: h2 → parent POM'da h2 bağımlılığı; exit 0 (drop yaratmaz) ──

    @Test
    void genConfigH2_addsH2Dependency_exitsZero(@TempDir Path work, @TempDir Path outDir) throws IOException {
        Path manifestSrc = fixture("manifest.json");
        Path contractSrc = fixture("operations.json");
        Files.copy(manifestSrc, work.resolve("manifest.json"));
        Files.copy(contractSrc, work.resolve("operations.json"));
        Files.writeString(work.resolve("gen.config.json"), "{\"dbProvider\":\"h2\"}");

        Captured c = capture(() ->
                Main.run(new String[] {work.resolve("manifest.json").toString(), outDir.toString()}));

        assertEquals(0, c.exit(), "stderr:\n" + c.stderr());
        String parentPom = Files.readString(outDir.resolve("gen/parent/pom.xml"));
        assertTrue(parentPom.contains("com.h2database"), "h2 bağımlılığı parent POM'da yok");
    }

    // ── gen.config varyantları: bilinmeyen 'oracle' → build-report'ta unsupported + exit 0
    // (unsupported drop DEĞİLDİR — exit ikiliğinin İKİNCİ yönü). ──

    @Test
    void genConfigUnknownProvider_isUnsupported_exitsZero(@TempDir Path work, @TempDir Path outDir)
            throws IOException {
        Path manifestSrc = fixture("manifest.json");
        Path contractSrc = fixture("operations.json");
        Files.copy(manifestSrc, work.resolve("manifest.json"));
        Files.copy(contractSrc, work.resolve("operations.json"));
        Files.writeString(work.resolve("gen.config.json"), "{\"dbProvider\":\"oracle\"}");

        Captured c = capture(() ->
                Main.run(new String[] {work.resolve("manifest.json").toString(), outDir.toString()}));

        assertEquals(0, c.exit(), "stderr:\n" + c.stderr());
        String buildReport = Files.readString(outDir.resolve("build-report.json"));
        assertTrue(buildReport.contains("\"dbProvider\""), "build-report'ta dbProvider entry yok");
        assertTrue(buildReport.contains("\"oracle\""), "build-report'ta oracle değeri yok");
        assertTrue(buildReport.contains("\"unsupported\""), "build-report'ta unsupported status yok");
    }

    // ── Bozuk manifest → exit 2 + stderr "ayrıştırılamadı" (LoadError; ASLA exit 1) ──

    @Test
    void malformedManifest_exitsTwo_withStderrMessage(@TempDir Path work, @TempDir Path outDir) throws IOException {
        Path manifest = work.resolve("manifest.json");
        Files.writeString(manifest, "{ bu gecerli json degil");

        Captured c = capture(() -> Main.run(new String[] {manifest.toString(), outDir.toString()}));

        assertEquals(2, c.exit());
        assertTrue(c.stderr().contains("ayrıştırılamadı"), "stderr:\n" + c.stderr());
    }

    // ── Eksik arg → usage + exit 2 ──

    @Test
    void missingArgs_exitsTwo() throws IOException {
        Captured c = capture(() -> Main.run(new String[] {"only-one-arg"}));
        assertEquals(2, c.exit());
    }

    // ── @Tag("e2e"): shaded jar'ı GERÇEK süreç olarak koş (`java -jar ...`). `mvn test` sırasında
    // dışlanır (pom.xml excludedGroups=e2e); yalnız `package` sonrası manuel/CI aşamasında koşar. ──

    @Tag("e2e")
    @Test
    void shadedJar_realProcess_exitsZero(@TempDir Path outDir) throws Exception {
        Path jar = Path.of("target/gen-cli.jar");
        assumeTrue(Files.exists(jar), "shaded jar yok — önce `mvn -pl gen-cli -am package` çalıştırın: " + jar);

        Path manifest = fixture("manifest.json");
        String javaBin = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder pb = new ProcessBuilder(javaBin, "-jar", jar.toString(),
                manifest.toString(), outDir.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();

        assertEquals(0, exit, "süreç çıktısı:\n" + out);
        assertTrue(Files.exists(outDir.resolve("build-report.json")));
        assertTrue(Files.exists(outDir.resolve("provenance.json")));
    }
}
