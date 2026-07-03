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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3.3 — Result taksonomisi + ResultHttp + Errors + Types + Events emisyon testleri (davranış
 * sözleşmesi §6.3 satırı; referans {@code docs/referans/gen-dotnet-emisyon-sozlesmesi.md} §1
 * "Result taksonomisi" + Errors/Types/Events satırları; INV-5 kapalı taksonomi, INV-7 no-silent-loss).
 */
class ResultTypesEmitTest {

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

    // ── Step 5.1 — Result hiyerarşisi ──────────────────────────────────────

    @Test
    void resultFamily_allFilesEmitted_withSealedInterfaceAndPermitsClause(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        for (String name : List.of("Result", "Success", "NotAuthenticated", "NotAuthorized",
                "NotValid", "NotProcessable", "ServerError", "Page", "Unit")) {
            assertTrue(Files.exists(outDir.resolve("gen/java/app/" + name + ".java")), name + ".java eksik");
        }

        String result = read(outDir, "gen/java/app/Result.java");
        assertTrue(result.contains("sealed interface Result<T>"), "sealed interface bildirimi eksik");
        assertTrue(result.contains(
                "permits Success, NotAuthenticated, NotAuthorized, NotValid, NotProcessable, ServerError"),
                "permits satırı conformance sözleşmesiyle birebir değil");

        assertTrue(read(outDir, "gen/java/app/NotValid.java").contains("Map<String, String> errors"),
                "NotValid.errors Map<String,String> olmalı (özel tip YASAK)");
        assertTrue(read(outDir, "gen/java/app/Page.java").contains("record Page<T>(List<T> items, String nextCursor)"));
        assertTrue(read(outDir, "gen/java/app/Unit.java").contains("record Unit()"));
    }

    // ── Step 5.2 — ResultHttp ───────────────────────────────────────────────

    @Test
    void resultHttp_fileEmitted_withToHttpAndOverrideHook(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/ResultHttp.java");
        assertTrue(content.contains("static ResponseEntity<?> toHttp(Result<?> result)"), "toHttp imzası eksik");
        assertTrue(content.contains("UnaryOperator<ResponseEntity<?>> override"), "override hook eksik");
        assertTrue(content.contains("UnaryOperator.identity()"), "override varsayılanı identity olmalı");
        assertTrue(content.contains("case NotAuthenticated<?>") && content.contains("401"));
        assertTrue(content.contains("case NotAuthorized<?>") && content.contains("403"));
        assertTrue(content.contains("case NotValid<?>") && content.contains("400"));
        assertTrue(content.contains("case NotProcessable<?>") && content.contains("422"));
        assertTrue(content.contains("case ServerError<?>") && content.contains("500"));
    }

    // ── Step 5.3 — Errors katalogu ───────────────────────────────────────────

    @Test
    void errors_billingCatalog_hasDuplicateInvoiceConstantAndResultTypeComment(@TempDir Path outDir)
            throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/Errors.java");
        assertTrue(content.contains("public static final String DuplicateInvoice = \"DuplicateInvoice\";"),
                "DuplicateInvoice sabiti eksik");
        assertTrue(content.contains("// resultType: NotProcessable"), "resultType yorumu eksik/yanlış");
    }

    /** §6.3 negatif — resultType yorumu birebir manifest değerini taşır (uydurma değer yok). */
    @Test
    void errors_resultTypeComment_carriesExactManifestValue_noFabrication(@TempDir Path outDir) throws IOException {
        Path manifestPath = fixture("manifest.json");
        ManifestJson manifest = Loader.loadManifest(manifestPath);
        String manifestResultType = manifest.errors().stream()
                .filter(e -> e.id().equals("DuplicateInvoice"))
                .findFirst()
                .orElseThrow()
                .resultType();

        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());
        String content = read(outDir, "gen/java/app/billing/Errors.java");

        assertTrue(content.contains("// resultType: " + manifestResultType),
                "emisyon manifest'teki resultType değerinden SAPMIŞ");
    }

    // ── Step 5.4 — Types (enum/composite) ────────────────────────────────────

    @Test
    void types_invoiceStatusEnum_hasThreeValuesInManifestOrder(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/InvoiceStatus.java");
        assertTrue(content.contains("public enum InvoiceStatus { Draft, Issued, Paid }"),
                "enum değerleri manifest sırasıyla/haliyle birebir olmalı (re-case YASAK)");
    }

    @Test
    void types_moneyComposite_hasTwoFieldsWithBigDecimalAmount(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/Money.java");
        assertTrue(content.contains("import java.math.BigDecimal;"), "BigDecimal import eksik");
        assertTrue(content.contains("public record Money(BigDecimal amount, String currency)"),
                "Money 2 alanlı, ilk alan BigDecimal amount olmalı");
    }

    @Test
    void types_auditMetaTypeLevelExt_realizedAndCommented(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/ops/AuditMeta.java");
        assertTrue(content.contains("// ext: @schema.versioned (realizasyon = policy)"),
                "type-level ext prelude yorumu eksik");
        assertTrue(content.contains("public record AuditMeta(String source, List<String> labels)"));

        assertTrue(report.entries().stream().anyMatch(e -> e.construct().equals("@schema.versioned")
                        && e.id().equals("AuditMeta") && e.status() == BuildReport.ConstructStatus.REALIZED),
                "@schema.versioned/AuditMeta realized olmalı — aksi halde census silentDrop yapar (T4.5 ZeroDropTest kırılır)");
        assertTrue(report.policies().containsKey("schema-realization"), "schema-realization policy eksik");
    }

    // ── Step 5.4 — Events + EventBus + Bootstrap kaydı ───────────────────────

    @Test
    void events_invoiceCreatedRecord_emittedWithPayloadFields(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/InvoiceCreated.java");
        assertTrue(content.contains("public record InvoiceCreated(String invoiceId, Money amount)"),
                "InvoiceCreated payload alanları eksik/yanlış");
    }

    @Test
    void eventBus_fileEmitted_andBootstrapRegistersBeanWhenEventsPresent(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String eventBus = read(outDir, "gen/java/app/EventBus.java");
        assertTrue(eventBus.contains("public interface EventBus"));
        assertTrue(eventBus.contains("void publish(Object event);"));
        assertTrue(eventBus.contains("class OutboxEventBus implements EventBus"));
        assertTrue(eventBus.contains("UnsupportedOperationException"), ".NET NotImplemented paritesi eksik");
        assertFalse(eventBus.contains("doldurulacak"),
                "OutboxEventBus gen-owned infra stub'dur — WriteIfAbsent human-seam marker'ı ('doldurulacak') KULLANILMAMALI");

        String bootstrap = read(outDir, "gen/java/app/GeneratedBootstrap.java");
        assertTrue(bootstrap.contains("import org.springframework.context.annotation.Bean;"));
        assertTrue(bootstrap.contains("@Bean"));
        assertTrue(bootstrap.contains("public EventBus eventBus()"));
        assertTrue(bootstrap.contains("new OutboxEventBus()"));
    }

    // ── Step 5.5 — build-report realized çağrıları (toplu) ───────────────────

    @Test
    void buildReport_realizesErrorEnumCompositeEventAndTypeExt(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, h2Config());

        assertRealized(report, "error", "DuplicateInvoice");
        assertRealized(report, "enum", "InvoiceStatus");
        assertRealized(report, "composite", "Money");
        assertRealized(report, "event", "InvoiceCreated");
        assertRealized(report, "@schema.versioned", "AuditMeta");
    }

    private static void assertRealized(BuildReport report, String construct, String id) {
        assertTrue(report.entries().stream().anyMatch(e -> e.construct().equals(construct) && e.id().equals(id)
                        && e.status() == BuildReport.ConstructStatus.REALIZED),
                "(%s,%s) realized olarak raporlanmalı".formatted(construct, id));
    }

    // ── 6.2 Pozitif — Spring bağımsız dosyalar (Result ailesi, Errors, enum) javac ile 0 diagnostic ──

    @Test
    void javac_compilesResultFamilyErrorsAndEnum_zeroDiagnostics(@TempDir Path outDir) throws IOException {
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
                outDir.resolve("gen/java/app/billing/Errors.java"),
                outDir.resolve("gen/java/app/billing/InvoiceStatus.java"));
        for (Path p : sources) {
            assertTrue(Files.exists(p), p + " compile listesinde ama diskte yok");
        }

        Path classesOut = outDir.resolve("javac-out");
        Files.createDirectories(classesOut);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units =
                    fileManager.getJavaFileObjectsFromPaths(sources);
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
}
