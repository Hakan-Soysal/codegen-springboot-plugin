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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3.7 — HandlerBase tamamlayıcıları: Auth / Throws / Consistency / Ext (SPEC §6.3; referans
 * {@code docs/referans/gen-dotnet-emisyon-sozlesmesi.md} §1 `:1401-1427`/`:844-873`/`:887-910`/
 * `:1142-1177` + §7). Fixture canlı örnekler: WriteAuditLog (roles+scopes+ownership+ext+
 * consistency mode=durable), CreateInvoice (throws+ext; consistency mode=null/risk=strong →
 * REALIZE EDİLMEZ).
 */
class HandlerBaseAuthThrowsConsistencyExtEmitTest {

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

    private static String read(Path outDir, String relPath) throws IOException {
        return Files.readString(outDir.resolve(relPath), StandardCharsets.UTF_8);
    }

    // ── Auth: WriteAuditLog roles=["Auditor"], scopes=["audit:write"], ownership="own" ──────────

    @Test
    void writeAuditLog_handlerBase_hasAuthSection(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/ops/writeauditlog/WriteAuditLogHandlerBase.java");
        assertTrue(content.contains("import java.util.List;"), "REQUIRED_ROLES için List import eksik");
        assertTrue(content.contains("public static final List<String> REQUIRED_ROLES = List.of(\"Auditor\");"));
        assertTrue(content.contains(
                "public static final List<String> REQUIRED_SCOPES = List.of(\"audit:write\");"));
        assertTrue(content.contains("public static final String OWNERSHIP = \"own\";"));

        assertTrue(report.entries().stream().anyMatch(e -> e.construct().equals("roles") && e.id().equals("WriteAuditLog")));
        assertTrue(report.entries().stream().anyMatch(e -> e.construct().equals("scopes") && e.id().equals("WriteAuditLog")));
        assertTrue(report.entries().stream().anyMatch(e -> e.construct().equals("ownership") && e.id().equals("WriteAuditLog")));
    }

    // ── Auth: CreateInvoice roles=["Clerk"], scopes=[], ownership=null → üçü birden emit ─────────

    @Test
    void createInvoice_handlerBase_hasAuthSection_withEmptyScopesAndNullOwnership(@TempDir Path outDir)
            throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceHandlerBase.java");
        assertTrue(content.contains("public static final List<String> REQUIRED_ROLES = List.of(\"Clerk\");"));
        assertTrue(content.contains("public static final List<String> REQUIRED_SCOPES = List.of();"));
        assertTrue(content.contains("public static final String OWNERSHIP = null;"));
    }

    // ── Throws: CreateInvoice throws=["DuplicateInvoice"] (module Billing == op module) ──────────

    @Test
    void createInvoice_handlerBase_hasThrowsSection(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceHandlerBase.java");
        assertTrue(content.contains("import app.billing.Errors;"), "aynı-modül Errors import eksik");
        assertTrue(content.contains("import app.NotProcessable;"));
        assertTrue(content.contains(
                "public static final String[] THROWABLE_ERRORS = {Errors.DuplicateInvoice};"));
        assertTrue(content.contains("public static Result<Invoice> duplicateInvoice(String message) {"),
                "fabrika adı camelCase error id olmalı");
        assertTrue(content.contains("return new NotProcessable<>(Errors.DuplicateInvoice, message);"));

        assertTrue(report.entries().stream()
                .anyMatch(e -> e.construct().equals("throws") && e.id().equals("CreateInvoice->DuplicateInvoice")));
    }

    // ── Consistency: WriteAuditLog mode="durable" → realized; CreateInvoice mode=null/strong → değil ─

    @Test
    void writeAuditLog_handlerBase_hasConsistencySection_modeDurable(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/ops/writeauditlog/WriteAuditLogHandlerBase.java");
        assertTrue(content.contains("public static final String CONSISTENCY_RISK = \"strong\";"));
        assertTrue(content.contains("public static final String CONSISTENCY_MODE = \"durable\";"));

        assertTrue(report.policies().containsKey("consistency-mode"));
        assertEquals("strong/durable (generator-policy)", report.policies().get("consistency-mode"));
        assertTrue(report.entries().stream()
                .anyMatch(e -> e.construct().equals("consistency") && e.id().equals("WriteAuditLog")));
    }

    @Test
    void createInvoice_handlerBase_hasNoConsistencySection_modeNullRiskStrong(@TempDir Path outDir)
            throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceHandlerBase.java");
        assertFalse(content.contains("CONSISTENCY_RISK"), "mode=null && risk!=eventual → consistency REALIZE EDİLMEMELİ");
        assertFalse(report.entries().stream()
                .anyMatch(e -> e.construct().equals("consistency") && e.id().equals("CreateInvoice")));
    }

    // ── Ext: WriteAuditLog @http.endpoint(route=/audit) → HTTP_ROUTE + http-binding policy ────────

    @Test
    void writeAuditLog_handlerBase_hasExtSection_withHttpBindingPolicy(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/ops/writeauditlog/WriteAuditLogHandlerBase.java");
        assertTrue(content.contains("public static final String AUDIT_CATEGORY = \"sys\";"));
        assertTrue(content.contains("public static final String HTTP_ROUTE = \"/audit\";"));
        assertTrue(content.contains("public static final String HTTP_METHOD = \"POST\";"));
        assertTrue(content.contains("// @audit.logged(category=sys)"));
        assertTrue(content.contains("// @http.endpoint(route=/audit, method=POST)"));
        assertTrue(content.contains("// @trigger.cron(schedule=0 0 * * *)"), "trigger ns de prelude+realize alır (.NET ExtPartial paritesi)");

        assertTrue(report.policies().containsKey("http-binding"));
        assertTrue(report.entries().stream()
                .anyMatch(e -> e.construct().equals("@http.endpoint") && e.id().equals("WriteAuditLog")));
        assertTrue(report.entries().stream()
                .anyMatch(e -> e.construct().equals("@audit.logged") && e.id().equals("WriteAuditLog")));
        assertTrue(report.entries().stream()
                .anyMatch(e -> e.construct().equals("@trigger.cron") && e.id().equals("WriteAuditLog")));
        assertTrue(report.policies().containsKey("audit-realization"));
        assertTrue(report.policies().containsKey("http-realization"));
        assertTrue(report.policies().containsKey("trigger-realization"));
    }

    // ── Ext: CreateInvoice @audit.logged + @metric.emit (http YOK → http-binding policy YOK burada) ──

    @Test
    void createInvoice_handlerBase_hasExtSection_auditAndMetric(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceHandlerBase.java");
        assertTrue(content.contains("public static final String AUDIT_CATEGORY = \"financial\";"));
        assertTrue(content.contains("public static final String METRIC_NAME = \"invoice_created\";"));
        assertFalse(content.contains("HTTP_ROUTE"), "CreateInvoice'ta @http ext yok");
    }

    // ── note çift-entry OLMADIĞI teyidi (T3.6 Step 5.1'de tek sefer realize edilir) ────────────────

    @Test
    void note_isRealizedExactlyOnce_notDuplicatedByT37(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, h2Config());

        long noteEntries = report.entries().stream()
                .filter(e -> e.construct().equals("note") && e.id().equals("WriteAuditLog"))
                .count();
        assertEquals(1, noteEntries, "note construct'ı yalnız BİR kez realize edilmeli (T3.6'da)");
    }

    // ── Üretilen app gerçek mvn compile (DoD) ─────────────────────────────────────────────────────

    @org.junit.jupiter.api.Tag("e2e")
    @Test
    void generatedApp_realMvnCompile_exitsZero_afterT37(@TempDir Path outDir) throws IOException, InterruptedException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());
        Path humanPom = outDir.resolve("pom.xml");

        ProcessBuilder pb = new ProcessBuilder("mvn", "-q", "-f", humanPom.toString(), "compile");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "mvn compile exit 0 bekleniyor; çıktı:\n" + output);
    }
}
