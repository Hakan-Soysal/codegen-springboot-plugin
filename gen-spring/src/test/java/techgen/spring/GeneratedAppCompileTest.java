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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3.6 §5.6 — Operation slice emisyon testleri (SPEC §6.2-6.4; referans
 * {@code docs/referans/gen-dotnet-emisyon-sozlesmesi.md} §1 `:1466-1513`/`:1663-1685`, §2/§3).
 * Fixture: CreateInvoice (POST, command), GetInvoice (GET path+query bind), ListInvoices
 * (GET, param'sız), WriteAuditLog (internal, controller yok). E2E testi ({@code @Tag("e2e")})
 * üretilen app'i GERÇEK {@code mvn compile} ile derler (ilk gerçek derlenen app, task §6.2).
 */
class GeneratedAppCompileTest {

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

    // ── §5.6 — gerçek `mvn compile`: ilk gerçek derlenen app (task §6.2 pozitif kabul) ──────────

    @Test
    @Tag("e2e")
    void generatedApp_realMvnCompile_exitsZero(@TempDir Path outDir) throws IOException, InterruptedException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());
        Path humanPom = outDir.resolve("pom.xml");

        ProcessBuilder pb = new ProcessBuilder("mvn", "-q", "-f", humanPom.toString(), "compile");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "mvn compile exit 0 bekleniyor; çıktı:\n" + output);
    }

    // ── §5.1-5.4 — 4 op için slice dosyaları mevcut; internal op controller ÜRETMİYOR ───────────

    @Test
    void emit_fourOps_sliceFilesExist_andInternalOpHasNoEndpoint(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        // CreateInvoice — command, exposed+rest → Command/HandlerBase/Handler/Endpoint hepsi var.
        assertTrue(Files.exists(outDir.resolve("gen/java/app/billing/createinvoice/CreateInvoiceCommand.java")));
        assertTrue(Files.exists(outDir.resolve("gen/java/app/billing/createinvoice/CreateInvoiceHandlerBase.java")));
        assertTrue(Files.exists(outDir.resolve("src/main/java/app/billing/createinvoice/CreateInvoiceHandler.java")));
        assertTrue(Files.exists(outDir.resolve("gen/java/app/billing/createinvoice/CreateInvoiceEndpoint.java")));

        // GetInvoice — query, exposed+rest.
        assertTrue(Files.exists(outDir.resolve("gen/java/app/billing/getinvoice/GetInvoiceQuery.java")));
        assertTrue(Files.exists(outDir.resolve("gen/java/app/billing/getinvoice/GetInvoiceHandlerBase.java")));
        assertTrue(Files.exists(outDir.resolve("src/main/java/app/billing/getinvoice/GetInvoiceHandler.java")));
        assertTrue(Files.exists(outDir.resolve("gen/java/app/billing/getinvoice/GetInvoiceEndpoint.java")));

        // ListInvoices — query, param'sız, exposed+rest.
        assertTrue(Files.exists(outDir.resolve("gen/java/app/billing/listinvoices/ListInvoicesQuery.java")));
        assertTrue(Files.exists(outDir.resolve("gen/java/app/billing/listinvoices/ListInvoicesHandlerBase.java")));
        assertTrue(Files.exists(outDir.resolve("src/main/java/app/billing/listinvoices/ListInvoicesHandler.java")));
        assertTrue(Files.exists(outDir.resolve("gen/java/app/billing/listinvoices/ListInvoicesEndpoint.java")));

        // WriteAuditLog — command, internal + rest-serving YOK → Endpoint dosyası ASLA emit edilmez.
        assertTrue(Files.exists(outDir.resolve("gen/java/app/ops/writeauditlog/WriteAuditLogCommand.java")));
        assertTrue(Files.exists(outDir.resolve("gen/java/app/ops/writeauditlog/WriteAuditLogHandlerBase.java")));
        assertTrue(Files.exists(outDir.resolve("src/main/java/app/ops/writeauditlog/WriteAuditLogHandler.java")));
        assertFalse(Files.exists(outDir.resolve("gen/java/app/ops/writeauditlog/WriteAuditLogEndpoint.java")),
                "internal op için controller ÜRETİLMEMELİ");
    }

    // ── §6.2 — HandlerBase: repository DI alanı + abstract execute + kanonik-sıra Javadoc ────────

    @Test
    void handlerBase_hasRepositoryFieldAbstractExecute_andCanonicalOrderJavadoc(@TempDir Path outDir)
            throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceHandlerBase.java");
        assertTrue(content.contains("package app.billing.createinvoice;"));
        assertTrue(content.contains("import app.Result;"));
        assertTrue(content.contains("import app.billing.InvoiceRepository;"), "cross-package repo import eksik");
        assertTrue(content.contains("import app.billing.Invoice;"), "cross-package dönüş tipi import eksik");
        assertTrue(content.contains("public abstract class CreateInvoiceHandlerBase {"));
        assertTrue(content.contains("protected final InvoiceRepository invoiceRepository;"));
        // T4.1 — CreateInvoice idempotent (fixture): ctor'a IdempotencyStore da eklenir (ctor-senkron).
        assertTrue(content.contains(
                "protected CreateInvoiceHandlerBase(InvoiceRepository invoiceRepository, IdempotencyStore idempotencyStore) {"));
        assertTrue(content.contains("public abstract Result<Invoice> execute(CreateInvoiceCommand request);"));
        assertTrue(content.contains("idempotency"), "kanonik-sıra Javadoc'u eksik");
        assertTrue(content.contains("authz"), "kanonik-sıra Javadoc'u eksik");
    }

    // ── §5.3 — human seam: marker birebir + ikinci emit'te EZİLMEZ (içerik değiştirerek test) ────

    @Test
    void humanSeam_containsVerbatimMarker(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "src/main/java/app/billing/createinvoice/CreateInvoiceHandler.java");
        assertTrue(content.contains(
                "throw new UnsupportedOperationException(\"CreateInvoice: iş mantığı doldurulacak\");"),
                "seam marker birebir olmalı");
        assertTrue(content.contains("public class CreateInvoiceHandler extends CreateInvoiceHandlerBase {"));
        assertFalse(content.contains("@Component"), "human seam'e @Component KONULMAMALI (kayıt gen-owned Wiring'de)");
    }

    @Test
    void humanSeam_secondEmit_neverOverwritten(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());
        Path handler = outDir.resolve("src/main/java/app/billing/createinvoice/CreateInvoiceHandler.java");
        Files.writeString(handler, "// elle bozuldu", StandardCharsets.UTF_8);

        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        assertEquals("// elle bozuldu", Files.readString(handler, StandardCharsets.UTF_8),
                "human seam ikinci emit'te EZİLMEMELİ (writeIfAbsent) — bayat-imza kasıtlı davranış");
    }

    // ── §6.3 — bind asimetrisi: POST @RequestBody vs GET @PathVariable/@RequestParam ─────────────

    @Test
    void bindAsymmetry_postRequestBody_vs_getPathVariableAndRequestParam(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String createInvoice = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceEndpoint.java");
        assertTrue(createInvoice.contains("@PostMapping(\"/invoices\")"));
        assertTrue(createInvoice.contains("@RequestBody CreateInvoiceCommand request"));

        String getInvoice = read(outDir, "gen/java/app/billing/getinvoice/GetInvoiceEndpoint.java");
        assertTrue(getInvoice.contains("@GetMapping(\"/invoices/{id}\")"));
        assertTrue(getInvoice.contains("@PathVariable String id"), "route param @PathVariable olmalı");
        assertTrue(getInvoice.contains("@RequestParam boolean includeVoid"), "route-dışı param @RequestParam olmalı");
        assertTrue(getInvoice.contains("new GetInvoiceQuery(id, includeVoid)"), "request paramlardan kurulmalı");
    }

    // ── §5.5 — Wiring: {op}Handler + {op}Endpoint @Bean kayıtları (TAM-AÇIK KAYIT, SPEC §12/4) ───

    @Test
    void wiring_hasExplicitBeanRegistrationsForHandlerAndEndpoint(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String billingWiring = read(outDir, "gen/java/app/billing/BillingWiring.java");
        assertTrue(billingWiring.contains("import app.billing.createinvoice.CreateInvoiceHandler;"));
        assertTrue(billingWiring.contains("import app.billing.createinvoice.CreateInvoiceEndpoint;"));
        // T4.1 — CreateInvoice idempotent (fixture): bean param + ctor arg listesine IdempotencyStore
        // eklenir (ctor-senkron; bean GeneratedBootstrap'ta EXPLICIT @Bean).
        assertTrue(billingWiring.contains(
                "public CreateInvoiceHandler createInvoiceHandler(InvoiceRepository invoiceRepository, "
                        + "IdempotencyStore idempotencyStore) {"));
        assertTrue(billingWiring.contains(
                "return new CreateInvoiceHandler(invoiceRepository, idempotencyStore);"));
        assertTrue(billingWiring.contains(
                "public CreateInvoiceEndpoint createInvoiceEndpoint(CreateInvoiceHandler h) {"));
        assertTrue(billingWiring.contains("return new CreateInvoiceEndpoint(h);"));

        String opsWiring = read(outDir, "gen/java/app/ops/OpsWiring.java");
        assertTrue(opsWiring.contains("import app.ops.writeauditlog.WriteAuditLogHandler;"));
        assertFalse(opsWiring.contains("WriteAuditLogEndpoint"), "internal op için Endpoint bean'i OLMAMALI");
    }
}
