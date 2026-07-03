package techgen.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import techgen.core.gm.GenerationModel;
import techgen.core.model.ContractFile;
import techgen.core.model.ManifestJson;
import techgen.core.pipeline.GmBuilder;
import techgen.core.pipeline.Loader;
import techgen.core.report.BuildReport;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3.5 §5.2-5.3 — Guards + Invariants fixture emisyon testleri (SPEC §6.5; referans
 * {@code docs/referans/gen-dotnet-emisyon-sozlesmesi.md} §1 `:1216-1319` + §7 validation/rule/permit/
 * guardRef/invariant satırları). Fixture: CreateInvoice (validation0 Decimal compareTo · rule0 iki
 * Decimal path), WriteAuditLog (validation0 Int + guardRef · permit0 String equals), Invoice/AuditLog
 * invariant'ları. Üretilen Guards+Invariants+Result ailesi javac ile 0-diagnostic derlenir.
 */
class GuardsEmitTest {

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

    // ── §5.2 — CreateInvoiceGuards: validation0 Decimal compareTo + rule0 iki Decimal path ──────

    @Test
    void createInvoiceGuards_validationAndRule_compareToFormWithBigDecimalInputRecords(@TempDir Path outDir)
            throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceGuards.java");
        assertTrue(content.contains("package app.billing.createinvoice;"), "op-slice paketi yanlış");
        assertTrue(content.contains("import java.math.BigDecimal;"), "BigDecimal import eksik");
        assertTrue(content.contains("public final class CreateInvoiceGuards {"), "Guards sınıfı eksik");

        // validation0: amount > 0 (Decimal) → compareTo + BigDecimal String-ctor
        assertTrue(content.contains(
                "public static boolean validation0(CreateInvoiceValidation0Input input) {"),
                "validation0 imzası eksik");
        assertTrue(content.contains(
                "return (input.amount().compareTo(new BigDecimal(\"0\")) > 0);"),
                "validation0 compareTo/BigDecimal formu yanlış");
        assertTrue(content.contains("public record CreateInvoiceValidation0Input(BigDecimal amount) {"),
                "validation0 input record BigDecimal amount olmalı");

        // rule0: amount <= resource.creditLimit (iki Decimal path) → compareTo + iki BigDecimal alan
        assertTrue(content.contains("public static boolean rule0(CreateInvoiceRule0Input input) {"),
                "rule0 imzası eksik");
        assertTrue(content.contains(
                "return (input.amount().compareTo(input.resourceCreditLimit()) <= 0);"),
                "rule0 compareTo/path-path formu yanlış");
        assertTrue(content.contains(
                "public record CreateInvoiceRule0Input(BigDecimal amount, BigDecimal resourceCreditLimit) {"),
                "rule0 input record iki BigDecimal alan olmalı");
    }

    // ── §5.2 — WriteAuditLogGuards: validation0 Int (compareTo YOK) + guardRef yorumu + permit0 equals ─

    @Test
    void writeAuditLogGuards_intValidationWithGuardRef_andStringPermitEquals(@TempDir Path outDir)
            throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/ops/writeauditlog/WriteAuditLogGuards.java");
        // validation0: seq > 0 (Int) → doğal operatör, compareTo YOK
        assertTrue(content.contains("// guardRef: g-audit"), "guardRef yorumu eksik");
        assertTrue(content.contains("public static boolean validation0(WriteAuditLogValidation0Input input) {"),
                "validation0 imzası eksik");
        assertTrue(content.contains("return (input.seq() > 0);"), "Int validation doğal operatörle olmalı");
        assertFalse(content.contains("input.seq().compareTo"), "Int yolu compareTo'ya SAPMAMALI");
        assertTrue(content.contains("public record WriteAuditLogValidation0Input(int seq) {"),
                "validation0 input record int seq olmalı");

        // permit0: actor.tenant = resource.tenant (String) → equals
        assertTrue(content.contains("public static boolean permit0(WriteAuditLogPermit0Input input) {"),
                "permit0 imzası eksik");
        assertTrue(content.contains(
                "return (input.actorTenant().equals(input.resourceTenant()));"),
                "String eşitlik equals ile olmalı (== DEĞİL)");
        assertTrue(content.contains(
                "public record WriteAuditLogPermit0Input(String actorTenant, String resourceTenant) {"),
                "permit0 input record iki String alan olmalı");
    }

    // ── §5.3 — Invariants: Invoice Decimal compareTo (bare param) + AuditLog Int doğal ─────────

    @Test
    void invariants_emittedWithTypedBareParams(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String invoice = read(outDir, "gen/java/app/billing/InvoiceInvariants.java");
        assertTrue(invoice.contains("package app.billing;"));
        assertTrue(invoice.contains("import java.math.BigDecimal;"), "Invoice invariant BigDecimal import eksik");
        assertTrue(invoice.contains("public final class InvoiceInvariants {"));
        assertTrue(invoice.contains("public static boolean invariant0(BigDecimal amount) {"),
                "invariant tipli bare parametre almalı (input record YOK)");
        assertTrue(invoice.contains("return (amount.compareTo(new BigDecimal(\"0\")) >= 0);"),
                "Invoice invariant compareTo formu yanlış");

        String audit = read(outDir, "gen/java/app/ops/AuditLogInvariants.java");
        assertTrue(audit.contains("public static boolean invariant0(int seq) {"),
                "AuditLog invariant int bare parametre almalı");
        assertTrue(audit.contains("return (seq >= 0);"), "Int invariant doğal operatörle olmalı");
        assertFalse(audit.contains("compareTo"), "Int invariant compareTo'ya SAPMAMALI");
    }

    // ── build-report: validation/rule/permit/guardRef/invariant realized + guard-linkage policy ─

    @Test
    void buildReport_realizesGuardConstructsAndPolicy(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, h2Config());

        assertRealized(report, "validation", "CreateInvoice");
        assertRealized(report, "rule", "CreateInvoice");
        assertRealized(report, "validation", "WriteAuditLog");
        assertRealized(report, "permit", "WriteAuditLog");
        assertRealized(report, "guardRef", "WriteAuditLog");
        assertRealized(report, "invariant", "Invoice");
        assertRealized(report, "invariant", "AuditLog");
        assertTrue(report.policies().containsKey("guard-linkage"), "guard-linkage policy eksik");
    }

    // ── 6.2/6.4 — üretilen Guards+Invariants+input record'ları Result ailesiyle javac 0-diagnostic ─

    @Test
    void javac_compilesGuardsInvariantsAndResultFamily_zeroDiagnostics(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        List<Path> sources = List.of(
                outDir.resolve("gen/java/app/Result.java"),
                outDir.resolve("gen/java/app/Success.java"),
                outDir.resolve("gen/java/app/NotAuthenticated.java"),
                outDir.resolve("gen/java/app/NotAuthorized.java"),
                outDir.resolve("gen/java/app/NotValid.java"),
                outDir.resolve("gen/java/app/NotProcessable.java"),
                outDir.resolve("gen/java/app/ServerError.java"),
                outDir.resolve("gen/java/app/Page.java"),
                outDir.resolve("gen/java/app/Unit.java"),
                outDir.resolve("gen/java/app/billing/createinvoice/CreateInvoiceGuards.java"),
                outDir.resolve("gen/java/app/ops/writeauditlog/WriteAuditLogGuards.java"),
                outDir.resolve("gen/java/app/billing/InvoiceInvariants.java"),
                outDir.resolve("gen/java/app/ops/AuditLogInvariants.java"));
        for (Path p : sources) {
            assertTrue(Files.exists(p), p + " compile listesinde ama diskte yok");
        }

        Path classesOut = outDir.resolve("javac-out");
        Files.createDirectories(classesOut);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sources);
            List<String> options = List.of("-d", classesOut.toString());
            JavaCompiler.CompilationTask task =
                    compiler.getTask(null, fileManager, diagnostics, options, null, units);
            boolean ok = task.call();

            List<String> diagText = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                diagText.add(d.toString());
            }
            assertTrue(ok && diagText.isEmpty(), "javac diagnostics boş olmalı, bulunan:\n"
                    + String.join("\n", diagText));
        }
    }

    private static void assertRealized(BuildReport report, String construct, String id) {
        assertTrue(report.entries().stream().anyMatch(e -> e.construct().equals(construct) && e.id().equals(id)
                        && e.status() == BuildReport.ConstructStatus.REALIZED),
                "(%s,%s) realized olarak raporlanmalı".formatted(construct, id));
    }
}
