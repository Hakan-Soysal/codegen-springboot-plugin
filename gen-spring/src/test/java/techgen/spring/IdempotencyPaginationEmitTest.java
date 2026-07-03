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
 * T4.1 — Idempotency + Pagination (SPEC M4; referans
 * {@code docs/referans/gen-dotnet-emisyon-sozlesmesi.md} §1 Idem/Page (`:1073-1089`/`:1432-1446`) +
 * §7). Fixture canlı örnekler: CreateInvoice (idempotent, keys=["customerId"]), ListInvoices
 * (cursor pagination, size=20).
 */
class IdempotencyPaginationEmitTest {

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

    // ── Idempotency: CreateInvoice idempotent.keys=["customerId"] ────────────────────────────────

    @Test
    void createInvoice_handlerBase_hasIdempotencySection(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceHandlerBase.java");
        assertTrue(content.contains("import app.IdempotencyStore;"), "IdempotencyStore import eksik");
        assertTrue(content.contains("protected final IdempotencyStore idempotencyStore;"), "DI alanı eksik");
        assertTrue(content.contains("public static final List<String> IDEMPOTENCY_KEYS = List.of(\"customerId\");"));
        // T4.2 — CreateInvoice emits=["InvoiceCreated"] (fixture): EventBus idempotent'ten SONRA eklenir
        // (ctor-senkron; M4 kuralı) — IdempotencyStore artık ctor'da SON param DEĞİL.
        assertTrue(content.contains("IdempotencyStore idempotencyStore, EventBus eventBus)"), "ctor param eksik");

        assertTrue(report.entries().stream()
                .anyMatch(e -> e.construct().equals("idempotent") && e.id().equals("CreateInvoice")));
        assertTrue(report.policies().containsKey("dedup-store"));
        assertEquals("in-memory (generator-policy)", report.policies().get("dedup-store"));
    }

    @Test
    void createInvoice_humanHandler_ctorThreadsIdempotencyStore(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "src/main/java/app/billing/createinvoice/CreateInvoiceHandler.java");
        assertTrue(content.contains("import app.IdempotencyStore;"));
        assertTrue(content.contains("public CreateInvoiceHandler("));
        // T4.2 — EventBus idempotent'ten SONRA eklenir (ctor-senkron; M4 kuralı).
        assertTrue(content.contains("IdempotencyStore idempotencyStore, EventBus eventBus) {"));
        assertTrue(content.contains("super("), "super çağrısı eksik");
    }

    @Test
    void idempotencyStoreSeam_isEmitted_whenAnyOpIdempotent(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/IdempotencyStore.java");
        assertTrue(content.contains("public interface IdempotencyStore {"));
        assertTrue(content.contains("boolean tryBegin(String key);"));
        assertTrue(content.contains("final class InMemoryIdempotencyStore implements IdempotencyStore {"));
        assertTrue(content.contains("ConcurrentHashMap"));
    }

    @Test
    void generatedBootstrap_hasExplicitIdempotencyStoreBean_noComponentScan(@TempDir Path outDir)
            throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/GeneratedBootstrap.java");
        assertTrue(content.contains("@Bean"));
        assertTrue(content.contains("public IdempotencyStore idempotencyStore() {"));
        assertTrue(content.contains("return new InMemoryIdempotencyStore();"));
        assertTrue(content.contains("@Configuration"));
        assertTrue(!content.contains("@ComponentScan"), "component-scan YASAK (SPEC §12/4 tam-açık kayıt)");
    }

    @Test
    void billingWiring_beanMethod_takesIdempotencyStoreParam_ctorSynced(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/BillingWiring.java");
        assertTrue(content.contains("import app.IdempotencyStore;"));
        assertTrue(content.contains("public CreateInvoiceHandler createInvoiceHandler("));
        // T4.2 — EventBus idempotent'ten SONRA eklenir (ctor-senkron; M4 kuralı).
        assertTrue(content.contains("IdempotencyStore idempotencyStore, EventBus eventBus) {"));
        assertTrue(content.contains(
                "return new CreateInvoiceHandler(invoiceRepository, idempotencyStore, eventBus);"));
    }

    // ── Pagination: ListInvoices strategy=cursor, keys=[createdAt desc], size=20 ─────────────────

    @Test
    void listInvoices_handlerBase_hasPaginationSection(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/billing/listinvoices/ListInvoicesHandlerBase.java");
        assertTrue(content.contains("import app.Page;"), "Page import eksik");
        assertTrue(content.contains("public static final String PAGINATION_STRATEGY = \"cursor\";"));
        assertTrue(content.contains("public static final int DEFAULT_PAGE_SIZE = 20;"));
        assertTrue(content.contains("public abstract Result<Page<Invoice>> execute(ListInvoicesQuery request);"),
                "dönüş tipi Result<Page<{Ret}>> olmalı");

        assertTrue(report.entries().stream()
                .anyMatch(e -> e.construct().equals("pagination") && e.id().equals("ListInvoices")));
        assertTrue(report.policies().containsKey("pagination-strategy"));
        assertEquals("cursor (generator-policy)", report.policies().get("pagination-strategy"));
        assertTrue(report.policies().containsKey("cursor-token"));
        assertEquals("opaque (generator-policy)", report.policies().get("cursor-token"));
    }

    @Test
    void listInvoices_requestRecord_hasCursorAndSizeFields(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/listinvoices/ListInvoicesQuery.java");
        assertTrue(content.contains("public record ListInvoicesQuery(String cursor, int size) {"));
    }

    @Test
    void listInvoices_endpoint_hasCursorAndSizeQueryParams(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/listinvoices/ListInvoicesEndpoint.java");
        assertTrue(content.contains("@RequestParam(required = false) String cursor"));
        assertTrue(content.contains("@RequestParam(defaultValue = \"20\") int size"));
        assertTrue(content.contains("new ListInvoicesQuery(cursor, size)"));
    }

    @Test
    void listInvoices_humanHandler_extendsPagedReturnType(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "src/main/java/app/billing/listinvoices/ListInvoicesHandler.java");
        assertTrue(content.contains("import app.Page;"));
        assertTrue(content.contains("public Result<Page<Invoice>> execute(ListInvoicesQuery request) {"));
    }

    // ── CreateInvoice non-paginated, ListInvoices non-idempotent — bölümler karışmamalı ────────────

    @Test
    void createInvoice_handlerBase_hasNoPaginationSection(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceHandlerBase.java");
        assertTrue(!content.contains("PAGINATION_STRATEGY"), "CreateInvoice pagination=null");
    }

    @Test
    void listInvoices_handlerBase_hasNoIdempotencySection(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/listinvoices/ListInvoicesHandlerBase.java");
        assertTrue(!content.contains("IDEMPOTENCY_KEYS"), "ListInvoices idempotent=null");
    }

    // ── Üretilen app gerçek mvn compile (DoD) ─────────────────────────────────────────────────────

    @org.junit.jupiter.api.Tag("e2e")
    @Test
    void generatedApp_realMvnCompile_exitsZero_afterT41(@TempDir Path outDir) throws IOException, InterruptedException {
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
