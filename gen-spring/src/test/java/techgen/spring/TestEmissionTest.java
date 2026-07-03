package techgen.spring;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import techgen.core.gm.GenerationModel;
import techgen.core.model.AccessJson;
import techgen.core.model.ContractFile;
import techgen.core.model.ContractOp;
import techgen.core.model.FlowJson;
import techgen.core.model.FlowStep;
import techgen.core.model.ManifestJson;
import techgen.core.model.Meta;
import techgen.core.model.OperationJson;
import techgen.core.model.ProcessJson;
import techgen.core.model.ProcessStage;
import techgen.core.model.SignatureJson;
import techgen.core.pipeline.GmBuilder;
import techgen.core.pipeline.Loader;
import techgen.core.report.BuildReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T7.1 — TestPlan→JUnit emisyonu: Fixture harness + owned test iskeleti + ARRANGE human seam
 * (SPEC §6.3 tests satırı; referans {@code docs/referans/gen-dotnet-emisyon-sozlesmesi.md} §1
 * Fixture/TestSkeleton/TestArrangeSeam). Üç senaryo: (1) fixture (process/flow YOK, contract
 * operations=4) → 4 orphanop_* iskelet+seam çifti, (2) sentetik iki-creator'lı process →
 * Unsupported("test-prereq") + dosya YOK, (3) studyo (gerçek veri, processTests&gt;0) — sayı
 * raporlanır, {@code mvn test-compile} KOŞULMAZ (süre; task §5.5 notu).
 */
class TestEmissionTest {

    private static Path fixture(String name) {
        Path fromModuleDir = Path.of("../fixtures", name);
        if (Files.exists(fromModuleDir)) {
            return fromModuleDir;
        }
        return Path.of("fixtures", name);
    }

    private static GenConfig h2Config() {
        return new GenConfig("h2", "inmemory");
    }

    private static String read(Path outDir, String relPath) throws IOException {
        return Files.readString(outDir.resolve(relPath), StandardCharsets.UTF_8);
    }

    // ── Senaryo 1: fixture manifest'in 4 op'u, process/flow YOK (contract.operations=4,
    // processes=[]/flows=[]) → contract.operations() \ opsInFlow(∅) = 4 orphanOpTest. ────────────

    private static ContractOp contractOp(String id) {
        return new ContractOp(id, null, null, null, null, null, null, null, null, null);
    }

    private static GenerationModel fourOrphanOpsGm() {
        Path manifestPath = fixture("manifest.json");
        ManifestJson loaded = Loader.loadManifest(manifestPath);
        // mode="standalone" (linked DEĞİL) — bu senaryo business-realizes join'ini test ETMİYOR,
        // yalnız TestPlan (access-tabanlı) türetimini; "linked" kalsaydı realizes="biz.*" bu
        // sentetik (processes=[]/flows=[]) contract'ta çözülemeyip JoinError fırlatırdı.
        ManifestJson manifest = new ManifestJson("standalone", loaded.contract(), loaded.meta(),
                loaded.deployables(), loaded.modules(), loaded.operations(), loaded.entities(), loaded.types(),
                loaded.errors(), loaded.events(), loaded.subscriptions(), loaded.externals(), loaded.uncharted(),
                loaded.callEdges(), loaded.coverage());
        ContractFile contract = new ContractFile(null,
                List.of(contractOp("CreateInvoice"), contractOp("GetInvoice"), contractOp("ListInvoices"),
                        contractOp("WriteAuditLog")),
                null, null, null, List.of(), List.of());
        return GmBuilder.build(manifest, contract);
    }

    @Test
    void fourOrphanOps_allSingle_emitsSkeletonAndArrangeSeam_forEachOp(@TempDir Path outDir) throws IOException {
        GenerationModel gm = fourOrphanOpsGm();
        assertEquals(4, gm.testPlan().orphanOpTests().size(), "ön koşul: 4 orphanOpTest (process/flow yok)");
        BuildReport report = new BuildReport();
        SpringEmitter.emit(gm, outDir, report, h2Config());

        // T7.1-PARITE: .NET DotnetEmitter.cs:235 paritesi — her all-Single test emit edildiğinde
        // report.realized("test", "{Scope}_{Name}") çağrılır (id-şeması §10 OrphanOp_*).
        assertTrue(report.entries().stream().anyMatch(e -> "test".equals(e.construct())
                        && "OrphanOp_CreateInvoice".equals(e.id())
                        && e.status() == BuildReport.ConstructStatus.REALIZED),
                "report.realized(\"test\", \"OrphanOp_CreateInvoice\") bekleniyor; entries=" + report.entries());
        assertTrue(report.entries().stream().anyMatch(e -> "test".equals(e.construct())
                        && "OrphanOp_WriteAuditLog".equals(e.id())
                        && e.status() == BuildReport.ConstructStatus.REALIZED),
                "report.realized(\"test\", \"OrphanOp_WriteAuditLog\") bekleniyor; entries=" + report.entries());

        // Fixture harness her zaman emit edilir.
        assertTrue(Files.exists(outDir.resolve("gen/test-java/app/Fixture.java")));

        // CreateInvoice — prereq yok (kendi Invoice'ını yaratır); writeSet=[Invoice].
        String createTest = read(outDir, "gen/test-java/app/orphanop_createinvoice/CreateInvoiceTest.java");
        assertTrue(createTest.contains("package app.orphanop_createinvoice;"));
        assertTrue(createTest.contains(
                "Result<?> r1 = fixture.run(CreateInvoiceHandler.class, arrange.request1());"));
        assertTrue(createTest.contains("assertInstanceOf(Success.class, r1);"));
        assertTrue(createTest.contains("assertNotNull(fixture.get(Invoice.class, null));"));
        assertFalse(createTest.contains("doldurulacak"), "owned iskelette HUMAN-SEAM marker OLMAMALI");
        assertTrue(Files.exists(outDir.resolve("src/test/java/app/orphanop_createinvoice/CreateInvoiceArrange.java")));
        String createArrange = read(outDir, "src/test/java/app/orphanop_createinvoice/CreateInvoiceArrange.java");
        assertTrue(createArrange.contains("public CreateInvoiceCommand request1()"));
        assertTrue(createArrange.contains("doldurulacak"), "human seam'de marker OLMALI");

        // GetInvoice — prereq: Invoice/CreateInvoice (SINGLE) → request1=prereq, request2=act.
        String getTest = read(outDir, "gen/test-java/app/orphanop_getinvoice/GetInvoiceTest.java");
        assertTrue(getTest.contains(
                "Result<?> r1 = fixture.run(CreateInvoiceHandler.class, arrange.request1()); "
                        + "// ön-gereksinim: Invoice"));
        assertTrue(getTest.contains("Result<?> r2 = fixture.run(GetInvoiceHandler.class, arrange.request2());"));
        assertTrue(getTest.contains("assertInstanceOf(Success.class, r1);"));
        assertTrue(getTest.contains("assertInstanceOf(Success.class, r2);"));
        String getArrange = read(outDir, "src/test/java/app/orphanop_getinvoice/GetInvoiceArrange.java");
        assertTrue(getArrange.contains("public CreateInvoiceCommand request1()"));
        assertTrue(getArrange.contains("public GetInvoiceQuery request2()"));

        // ListInvoices — aynı prereq deseni (Invoice/CreateInvoice).
        assertTrue(Files.exists(outDir.resolve("gen/test-java/app/orphanop_listinvoices/ListInvoicesTest.java")));
        assertTrue(Files.exists(
                outDir.resolve("src/test/java/app/orphanop_listinvoices/ListInvoicesArrange.java")));

        // WriteAuditLog — prereq yok; writeSet=[AuditLog].
        String auditTest = read(outDir, "gen/test-java/app/orphanop_writeauditlog/WriteAuditLogTest.java");
        assertTrue(auditTest.contains("assertNotNull(fixture.get(AuditLog.class, null));"));
    }

    @Test
    void secondEmit_arrangeSeam_neverOverwritten(@TempDir Path outDir) throws IOException {
        GenerationModel gm = fourOrphanOpsGm();
        SpringEmitter.emit(gm, outDir, new BuildReport(), h2Config());

        Path arrangeFile = outDir.resolve("src/test/java/app/orphanop_createinvoice/CreateInvoiceArrange.java");
        Files.writeString(arrangeFile, "// elle dolduruldu\n");

        SpringEmitter.emit(gm, outDir, new BuildReport(), h2Config());

        assertEquals("// elle dolduruldu\n", Files.readString(arrangeFile, StandardCharsets.UTF_8),
                "ARRANGE human-seam ikinci emit'te EZİLMEMELİ (writeIfAbsent)");
    }

    @Test
    @Tag("e2e")
    void fourOrphanOps_realMvnTestCompile_exitsZero(@TempDir Path outDir) throws IOException, InterruptedException {
        SpringEmitter.emit(fourOrphanOpsGm(), outDir, new BuildReport(), h2Config());
        Path humanPom = outDir.resolve("pom.xml");

        ProcessBuilder pb = new ProcessBuilder("mvn", "-q", "-f", humanPom.toString(), "test-compile");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "mvn test-compile exit 0 bekleniyor (gövde null-dönen stub'larla derleme "
                + "yeterli); çıktı:\n" + output);
    }

    // ── Senaryo 2: sentetik iki-creator'lı process (E entity'si CreateA/CreateB'den AMBIGUOUS) →
    // test iskeleti/seam EMİT EDİLMEZ + Unsupported("test-prereq", ...) (T7.1 Step 5.4). ──────────

    private static OperationJson syntheticOp(String id, List<String> reads, List<String> creates,
            List<String> updates, List<String> deletes) {
        return new OperationJson(id, "M", "internal", null, new SignatureJson(List.of(), "Unit"), List.of(),
                List.of(), null, new AccessJson(reads, creates, updates, deletes), List.of(), List.of(), null, null,
                null, null, List.of(), List.of(), null, List.of(), null, null);
    }

    private static ManifestJson ambiguousManifest() {
        OperationJson createA = syntheticOp("CreateA", List.of(), List.of("E"), List.of(), List.of());
        OperationJson createB = syntheticOp("CreateB", List.of(), List.of("E"), List.of(), List.of());
        OperationJson useE = syntheticOp("UseE", List.of("E"), List.of(), List.of(), List.of());
        return new ManifestJson("standalone", null, new Meta(false, 0), null, null,
                List.of(createA, createB, useE), null, null, null, null, null, null, null, null, null);
    }

    private static ContractFile ambiguousContract() {
        ProcessJson process = new ProcessJson("P", null, null,
                List.of(new ProcessStage("stage", "S", null, "F", null)));
        FlowJson flow = new FlowJson("F", null, null,
                List.of(new FlowStep("step", "T", "UseE", false, false, null)));
        return new ContractFile(null, null, null, null, null, List.of(process), List.of(flow));
    }

    @Test
    void ambiguousPrereq_process_emitsNoTestFiles_andReportsUnsupportedTestPrereq(@TempDir Path outDir)
            throws IOException {
        GenerationModel gm = GmBuilder.build(ambiguousManifest(), ambiguousContract());
        assertEquals(1, gm.testPlan().processTests().size(), "ön koşul: 1 ProcessTest (P)");
        assertEquals(techgen.core.gm.PrereqKind.AMBIGUOUS, gm.testPlan().processTests().get(0).prerequisites()
                .get(0).kind(), "ön koşul: E entity'si CreateA/CreateB'den AMBIGUOUS");

        BuildReport report = new BuildReport();
        SpringEmitter.emit(gm, outDir, report, h2Config());

        assertFalse(Files.exists(outDir.resolve("gen/test-java/app/process/PTest.java")),
                "Single-dışı prereq'li test için owned iskelet EMİT EDİLMEMELİ");
        assertFalse(Files.exists(outDir.resolve("src/test/java/app/process/PArrange.java")),
                "Single-dışı prereq'li test için ARRANGE seam EMİT EDİLMEMELİ");

        assertTrue(report.entries().stream().anyMatch(e -> "test-prereq".equals(e.construct())
                        && e.id().equals("P: E creator=ambiguous")
                        && e.status() == BuildReport.ConstructStatus.UNSUPPORTED),
                "Unsupported(\"test-prereq\", \"P: E creator=ambiguous\", ...) entry bekleniyor; gerçek entries="
                        + report.entries());

        // T7.1-PARITE: Single-dışı (Unsupported) test için realize ÇAĞRILMAMALI — yalnız all-Single
        // testler census'a "test" olarak girer (.NET DotnetEmitter.cs:235 paritesi).
        assertFalse(report.entries().stream().anyMatch(e -> "test".equals(e.construct())),
                "Unsupported test-prereq'li process için 'test' realize entry OLMAMALI; entries="
                        + report.entries());
    }

    // ── Senaryo 3: studyo (gerçek veri) — processTests&gt;0 iskeletleri üretiliyor (sayı raporlanır).
    // mvn test-compile KOŞULMAZ (süre; task §5.5 notu — GeneratedAppCompileTest/StudyoScaleE2ETest
    // zaten gerçek mvn compile'ı ayrı task'larda kapsıyor). ─────────────────────────────────────

    private static ManifestJson studyoManifest() {
        return Loader.loadManifest(fixture("studyo.manifest.json"));
    }

    private static GenerationModel studyoGm(ManifestJson manifest) {
        ContractFile contract = Loader.loadContract(fixture("studyo.manifest.json"), manifest.contract());
        return GmBuilder.build(manifest, contract);
    }

    @Test
    void studyo_processTestsGreaterThanZero_skeletonsEmitted(@TempDir Path outDir) throws IOException {
        ManifestJson manifest = studyoManifest();
        GenerationModel gm = studyoGm(manifest);
        int processTestCount = gm.testPlan().processTests().size();
        assertTrue(processTestCount > 0, "ön koşul: studyo'da processTests>0 (T2.2 build_studyo_threeListCounts)");

        BuildReport report = new BuildReport();
        SpringEmitter.emit(gm, outDir, report, h2Config());

        assertTrue(Files.exists(outDir.resolve("gen/test-java/app/process")),
                "studyo processTests için 'process' klasörü üretilmeli");
        long emittedCount;
        try (var walk = Files.walk(outDir.resolve("gen/test-java/app/process"))) {
            emittedCount = walk.filter(p -> p.toString().endsWith("Test.java")).count();
        }
        assertTrue(emittedCount > 0, "en az bir process test iskeleti üretilmeli; üretilen=" + emittedCount);

        // T7.1-PARITE: studyo (gerçek veri) emisyonunda en az bir "test" census realize entry var.
        long testRealizedCount = report.entries().stream()
                .filter(e -> "test".equals(e.construct()) && e.status() == BuildReport.ConstructStatus.REALIZED)
                .count();
        assertTrue(testRealizedCount > 0,
                "studyo emisyonunda en az bir report.realized(\"test\", ...) entry bekleniyor");
        // Not (task raporunda tekrarlanır): processTestCount=%d, emittedCount=%d — bazı process'ler
        // Single-dışı prereq içerebileceğinden emittedCount <= processTestCount (Unsupported-farkı normal).
    }
}
