package techgen.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import techgen.core.gm.GenerationModel;
import techgen.core.model.Consistency;
import techgen.core.model.ContractFile;
import techgen.core.model.EntityFieldJson;
import techgen.core.model.EntityJson;
import techgen.core.model.GuardedExpr;
import techgen.core.model.ManifestJson;
import techgen.core.model.OperationJson;
import techgen.core.model.Pagination;
import techgen.core.model.PaginationKey;
import techgen.core.model.ServingArg;
import techgen.core.model.ServingJson;
import techgen.core.model.SourceOfTruth;
import techgen.core.pipeline.GmBuilder;
import techgen.core.pipeline.Loader;
import techgen.core.report.BuildReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T6.2 — {@code docs/referans/conformance-testler-skill-sozlesmesi.md} §B {@code LatentConstructTests}
 * portu (CoreTemplate1 {@code tests/Gen.Tests/LatentConstructTests.cs}). Fixture'ı ({@code with}-benzeri
 * kopya kurucularla, gen-core model record'ları immutable olduğundan canonical-constructor kopyası)
 * genişleterek fixture'ın DOĞAL OLARAK tetiklemediği dal/keyword kombinasyonlarını hedefler
 * (consistency=eventual, pagination=offset, serving=queue, @internal → normalde exposed bir op,
 * guardRef/sourceOfTruth → normalde bu alanı taşımayan bir op/entity). Fixture'ın ZATEN doğal
 * olarak taşıdığı site'lar (module/type/type-field/entity/boundary-op-param/uncharted-op-param ext,
 * deployable-ext, note) MUTASYONSUZ doğrudan fixture'dan doğrulanır — bunlar başka T-task
 * dosyalarında (ör. {@code ResultTypesEmitTest}, {@code CensusTest}) YALNIZCA census/genel düzeyde
 * kapsanmıştı, bu dosya somut emit-çıktısı içeriğini ilk kez doğrudan assert eder.
 */
class LatentConstructTests {

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

    private static ContractFile fixtureContract(ManifestJson m) {
        return Loader.loadContract(fixture("manifest.json"), m.contract());
    }

    private static GenConfig h2Config() {
        return new GenConfig("h2", "inmemory");
    }

    private static String read(Path outDir, String relPath) throws IOException {
        return Files.readString(outDir.resolve(relPath), StandardCharsets.UTF_8);
    }

    private static boolean exists(Path outDir, String relPath) {
        return Files.exists(outDir.resolve(relPath));
    }

    // ── "with"-benzeri kopya kurucular (gen-core record'ları immutable — C# `with` karşılığı) ──────

    private static OperationJson findOp(ManifestJson m, String id) {
        return m.operations().stream().filter(o -> o.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("op yok: " + id));
    }

    private static ManifestJson withOp(ManifestJson m, OperationJson replacement) {
        List<OperationJson> ops = m.operations().stream()
                .map(o -> o.id().equals(replacement.id()) ? replacement : o)
                .collect(Collectors.toList());
        return new ManifestJson(m.mode(), m.contract(), m.meta(), m.deployables(), m.modules(), ops,
                m.entities(), m.types(), m.errors(), m.events(), m.subscriptions(), m.externals(),
                m.uncharted(), m.callEdges(), m.coverage());
    }

    private static ManifestJson withEntity(ManifestJson m, EntityJson replacement) {
        List<EntityJson> entities = m.entities().stream()
                .map(e -> e.id().equals(replacement.id()) ? replacement : e)
                .collect(Collectors.toList());
        return new ManifestJson(m.mode(), m.contract(), m.meta(), m.deployables(), m.modules(),
                m.operations(), entities, m.types(), m.errors(), m.events(), m.subscriptions(),
                m.externals(), m.uncharted(), m.callEdges(), m.coverage());
    }

    private static OperationJson withConsistency(OperationJson o, Consistency c) {
        return new OperationJson(o.id(), o.module(), o.visibility(), o.realizes(), o.signature(), o.serving(),
                o.roles(), o.ownership(), o.access(), o.validation(), o.rule(), o.note(), o.businessNote(),
                c, o.abac(), o.scopes(), o.throwsList(), o.idempotent(), o.emits(), o.pagination(), o.ext());
    }

    private static OperationJson withServing(OperationJson o, List<ServingJson> serving) {
        return new OperationJson(o.id(), o.module(), o.visibility(), o.realizes(), o.signature(), serving,
                o.roles(), o.ownership(), o.access(), o.validation(), o.rule(), o.note(), o.businessNote(),
                o.consistency(), o.abac(), o.scopes(), o.throwsList(), o.idempotent(), o.emits(), o.pagination(),
                o.ext());
    }

    private static OperationJson withVisibility(OperationJson o, String visibility) {
        return new OperationJson(o.id(), o.module(), visibility, o.realizes(), o.signature(), o.serving(),
                o.roles(), o.ownership(), o.access(), o.validation(), o.rule(), o.note(), o.businessNote(),
                o.consistency(), o.abac(), o.scopes(), o.throwsList(), o.idempotent(), o.emits(), o.pagination(),
                o.ext());
    }

    private static OperationJson withValidation(OperationJson o, List<GuardedExpr> validation) {
        return new OperationJson(o.id(), o.module(), o.visibility(), o.realizes(), o.signature(), o.serving(),
                o.roles(), o.ownership(), o.access(), validation, o.rule(), o.note(), o.businessNote(),
                o.consistency(), o.abac(), o.scopes(), o.throwsList(), o.idempotent(), o.emits(), o.pagination(),
                o.ext());
    }

    private static OperationJson withPagination(OperationJson o, Pagination pagination) {
        return new OperationJson(o.id(), o.module(), o.visibility(), o.realizes(), o.signature(), o.serving(),
                o.roles(), o.ownership(), o.access(), o.validation(), o.rule(), o.note(), o.businessNote(),
                o.consistency(), o.abac(), o.scopes(), o.throwsList(), o.idempotent(), o.emits(), pagination,
                o.ext());
    }

    private static EntityFieldJson withSourceOfTruth(EntityFieldJson f, SourceOfTruth s) {
        return new EntityFieldJson(f.name(), f.type(), f.collection(), f.cardinality(), f.ref(),
                f.targetModule(), f.crossModule(), s, f.ext());
    }

    private static EntityJson withField(EntityJson e, EntityFieldJson replacement) {
        List<EntityFieldJson> fields = e.fields().stream()
                .map(f -> f.name().equals(replacement.name()) ? replacement : f)
                .collect(Collectors.toList());
        return new EntityJson(e.id(), e.module(), e.realizes(), fields, e.invariants(), e.concurrency(), e.ext());
    }

    private static GenerationModel gmFrom(ManifestJson mutated, ContractFile contract) {
        return GmBuilder.build(mutated, contract);
    }

    private static void noDrop(BuildReport r, String construct, String owner) {
        assertFalse(r.silentDrops().stream().anyMatch(d -> d.construct().equals(construct) && d.id().equals(owner)),
                construct + "/" + owner + " silentDrop OLMAMALI");
    }

    // ── B2 — consistency {risk=eventual, mode} → outbox seçim-iskeleti yorumu (CreateInvoice'ta LATENT: fixture'da strong/durable var, eventual YOK) ──

    @Test
    void consistency_eventualRisk_emitsOutboxComment_andPolicy(@TempDir Path outDir) throws IOException {
        ManifestJson m = fixtureManifest();
        ContractFile c = fixtureContract(m);
        OperationJson create = findOp(m, "CreateInvoice");
        ManifestJson mutated = withOp(m, withConsistency(create, new Consistency("eventual", "durable")));

        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(mutated, c), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceHandlerBase.java");
        assertTrue(content.contains("outbox seçim-iskeleti"), "eventual risk → outbox yorumu eksik");
        assertTrue(content.contains("public static final String CONSISTENCY_RISK = \"eventual\";"));
        assertTrue(content.contains("public static final String CONSISTENCY_MODE = \"durable\";"));
        assertTrue(report.covers("consistency", "CreateInvoice"));
        noDrop(report, "consistency", "CreateInvoice");
    }

    // ── B3 — deployable → modular-monolith host topolojisi (fixture doğrudan; DeploymentTopology.java içeriği başka hiçbir testte doğrudan doğrulanmıyor) ──

    @Test
    void deployable_topologyClass_mapsUnitsPerDeployable(@TempDir Path outDir) throws IOException {
        ManifestJson m = fixtureManifest();
        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(m, fixtureContract(m)), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/DeploymentTopology.java");
        assertTrue(content.contains("public final class DeploymentTopology {"));
        assertTrue(content.contains("DEPLOYABLES.put(\"BillingService\", List.of(\"Billing\"));"));
        assertTrue(content.contains("DEPLOYABLES.put(\"OpsService\", List.of(\"Ops\"));"));
        assertTrue(report.covers("deployable", "BillingService"));
        assertTrue(report.covers("deployable", "OpsService"));
        noDrop(report, "deployable", "BillingService");
        noDrop(report, "deployable", "OpsService");
    }

    // ── deployable-level ext (OpsService @deploy.region(zone=eu), fixture doğal) — 6. annotation-site ──

    @Test
    void deployableExt_regionZone_emitsCommentAndRealized(@TempDir Path outDir) throws IOException {
        ManifestJson m = fixtureManifest();
        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(m, fixtureContract(m)), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/DeploymentTopology.java");
        assertTrue(content.contains("// ext: @deploy.region (realizasyon = policy)"));
        assertTrue(report.covers("@deploy.region", "OpsService"));
        assertTrue(report.policies().containsKey("deploy-realization"));
    }

    // ── D1 — serving @queue → explicit UnsupportedConstruct (grpc zaten ZeroDropTest'te; queue LATENT — fixture'da hiç yok) ──

    @Test
    void serving_queue_recordedUnsupported_notSilentDrop(@TempDir Path outDir) throws IOException {
        ManifestJson m = fixtureManifest();
        ContractFile c = fixtureContract(m);
        OperationJson create = findOp(m, "CreateInvoice");
        List<ServingJson> serving = new java.util.ArrayList<>(create.serving());
        serving.add(new ServingJson("queue", List.of(), "@queue()"));
        ManifestJson mutated = withOp(m, withServing(create, serving));

        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(mutated, c), outDir, report, h2Config());

        assertTrue(report.entries().stream().anyMatch(e -> e.construct().equals("serving")
                && e.id().equals("CreateInvoice:queue") && e.status() == BuildReport.ConstructStatus.UNSUPPORTED),
                "CreateInvoice:queue UNSUPPORTED olmalı (REST-only binding; drop DEĞİL)");
        noDrop(report, "serving", "CreateInvoice:queue");
        // rest hâlâ realized (queue eklenmesi rest'i etkilemez)
        assertTrue(report.covers("serving", "CreateInvoice:rest"));
    }

    // ── visibility @internal → route bastırma, normalde EXPOSED bir op üzerinde (GetInvoice) — fixture'da internal olan tek op (WriteAuditLog) zaten serving=[] ──

    @Test
    void visibility_internal_suppressesRoute_onNormallyExposedOp(@TempDir Path outDir) throws IOException {
        ManifestJson m = fixtureManifest();
        ContractFile c = fixtureContract(m);
        OperationJson getInvoice = findOp(m, "GetInvoice");
        ManifestJson mutated = withOp(m, withVisibility(getInvoice, "internal"));

        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(mutated, c), outDir, report, h2Config());

        assertFalse(exists(outDir, "gen/java/app/billing/getinvoice/GetInvoiceEndpoint.java"),
                "internal GetInvoice → Endpoint dosyası hiç EMİT EDİLMEMELİ");
        String createEp = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceEndpoint.java");
        assertTrue(createEp.contains("\"/invoices\""), "exposed CreateInvoice → route VAR olmaya devam etmeli");
        assertTrue(report.covers("visibility", "GetInvoice"));
    }

    // ── C1 — module-level ext (Ops @audit.module, fixture doğal) ──────────────────────────────────

    @Test
    void extModuleLevel_opsAuditModule_emitsCommentAndRealized(@TempDir Path outDir) throws IOException {
        ManifestJson m = fixtureManifest();
        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(m, fixtureContract(m)), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/ops/ModulePrelude.java");
        assertTrue(content.contains("// ext: @audit.module (realizasyon = policy)"));
        assertTrue(report.covers("@audit.module", "Ops"));
    }

    // ── C1 — entity-level ext (AuditLog @audit.tracked, fixture doğal) ────────────────────────────

    @Test
    void extEntityLevel_auditLogAuditTracked_emitsCommentAndRealized(@TempDir Path outDir) throws IOException {
        ManifestJson m = fixtureManifest();
        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(m, fixtureContract(m)), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/ops/AuditLog.java");
        assertTrue(content.contains("// ext: @audit.tracked (realizasyon = policy)"));
        assertTrue(report.covers("@audit.tracked", "AuditLog"));
    }

    // ── C1 — type-field-level ext (AuditMeta.source @sensitivity.tag, fixture doğal) ──────────────

    @Test
    void extTypeFieldLevel_auditMetaSourceSensitivityTag_emitsCommentAndRealized(@TempDir Path outDir)
            throws IOException {
        ManifestJson m = fixtureManifest();
        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(m, fixtureContract(m)), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/ops/AuditMeta.java");
        assertTrue(content.contains("// ext: @sensitivity.tag (realizasyon = policy)"));
        assertTrue(report.covers("@sensitivity.tag", "AuditMeta.source"));
    }

    // ── C1 — op-param-level ext (WriteAuditLog.seq @sensitivity.tag, fixture doğal) ────────────────

    @Test
    void extOpParamLevel_writeAuditLogSeqSensitivityTag_emitsCommentAndRealized(@TempDir Path outDir)
            throws IOException {
        ManifestJson m = fixtureManifest();
        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(m, fixtureContract(m)), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/ops/writeauditlog/WriteAuditLogCommand.java");
        assertTrue(content.contains("@sensitivity.tag"), "op-param ext yorumu eksik");
        assertTrue(report.covers("@sensitivity.tag", "WriteAuditLog.seq"));
    }

    // ── C1 — boundary-op-param-level ext (PaymentGateway.charge.amount @sensitivity.tag, fixture doğal) ──

    @Test
    void extBoundaryOpParamLevel_paymentGatewayChargeAmountSensitivityTag_emitsCommentAndRealized(
            @TempDir Path outDir) throws IOException {
        ManifestJson m = fixtureManifest();
        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(m, fixtureContract(m)), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/boundary/PaymentGateway.java");
        assertTrue(content.contains("@sensitivity.tag"), "boundary-op-param ext yorumu eksik");
        assertTrue(report.covers("@sensitivity.tag", "PaymentGateway.charge.amount"));
    }

    // ── C1 — uncharted-boundary-op-param-level ext (LegacyLedger.post.amount @ucparam.tag, fixture doğal) ──

    @Test
    void extUnchartedOpParamLevel_legacyLedgerPostAmountUcparamTag_emitsCommentAndRealized(@TempDir Path outDir)
            throws IOException {
        ManifestJson m = fixtureManifest();
        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(m, fixtureContract(m)), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/uncharted/LegacyLedger.java");
        assertTrue(content.contains("@ucparam.tag"), "uncharted-op-param ext yorumu eksik");
        assertTrue(report.covers("@ucparam.tag", "LegacyLedger.post.amount"));
    }

    // ── C4 — guardRef, normalde bu alanı taşımayan bir op üzerinde (CreateInvoice) — fixture'da guardRef
    // yalnız WriteAuditLog'ta ────────────────────────────────────────────────────────────────────────

    @Test
    void guardRef_onDifferentOp_emitsPredicateCommentAndPolicy(@TempDir Path outDir) throws IOException {
        ManifestJson m = fixtureManifest();
        ContractFile c = fixtureContract(m);
        OperationJson create = findOp(m, "CreateInvoice");
        GuardedExpr guarded = create.validation().get(0);
        GuardedExpr withRef = new GuardedExpr(guarded.text(), guarded.ast(), "credit-policy");
        ManifestJson mutated = withOp(m, withValidation(create, List.of(withRef)));

        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(mutated, c), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceGuards.java");
        assertTrue(content.contains("// guardRef: credit-policy (build-time kapsama bağı)"));
        assertTrue(report.covers("guardRef", "CreateInvoice"));
        noDrop(report, "guardRef", "CreateInvoice");
    }

    // ── F3 — pagination strategy=offset (fixture'da yalnız cursor var) + size fidelity ────────────

    @Test
    void pagination_offsetStrategy_emitsIntegerOffsetField_notCursor_andSizeReachesArtifact(@TempDir Path outDir)
            throws IOException {
        ManifestJson m = fixtureManifest();
        ContractFile c = fixtureContract(m);
        OperationJson list = findOp(m, "ListInvoices");
        Pagination offset = new Pagination("offset", List.of(new PaginationKey("createdAt", "desc")), 50);
        ManifestJson mutated = withOp(m, withPagination(list, offset));

        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(mutated, c), outDir, report, h2Config());

        String handlerBase = read(outDir, "gen/java/app/billing/listinvoices/ListInvoicesHandlerBase.java");
        assertTrue(handlerBase.contains("public static final String PAGINATION_STRATEGY = \"offset\";"));
        assertTrue(handlerBase.contains("public static final int DEFAULT_PAGE_SIZE = 50;"));

        String req = read(outDir, "gen/java/app/billing/listinvoices/ListInvoicesQuery.java");
        assertTrue(req.contains("Integer offset"), "offset-şekilli alan eksik");
        assertFalse(req.contains("String cursor"), "offset stratejisinde cursor OLMAMALI");

        String endpoint = read(outDir, "gen/java/app/billing/listinvoices/ListInvoicesEndpoint.java");
        assertTrue(endpoint.contains("@RequestParam(required = false) Integer offset"));
        assertTrue(endpoint.contains("@RequestParam(defaultValue = \"50\") int size"));
        assertTrue(report.covers("pagination", "ListInvoices"));
    }

    // ── C3 — sourceOfTruth, normalde bu alanı taşımayan bir entity üzerinde (Invoice.customerId) —
    // fixture'da sourceOfTruth yalnız AuditLog.invoiceRef'te ───────────────────────────────────────

    @Test
    void sourceOfTruth_onDifferentEntity_emitsCrossModuleFkComment_noNavigation(@TempDir Path outDir)
            throws IOException {
        ManifestJson m = fixtureManifest();
        ContractFile c = fixtureContract(m);
        EntityJson invoice = m.entities().stream().filter(e -> e.id().equals("Invoice")).findFirst().orElseThrow();
        EntityFieldJson customerId = invoice.fields().stream().filter(f -> f.name().equals("customerId"))
                .findFirst().orElseThrow();
        ManifestJson mutated = withEntity(m,
                withField(invoice, withSourceOfTruth(customerId, new SourceOfTruth("Ops", "AuditLog"))));

        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(mutated, c), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/billing/Invoice.java");
        assertTrue(content.contains("// sourceOfTruth: Ops.AuditLog — cross-module FK; navigasyon AÇILMAZ"));
        assertFalse(content.contains("public AuditLog "), "navigasyon alanı AÇILMAMALI");
        assertTrue(report.covers("sourceOfTruth", "Invoice.customerId"));
        noDrop(report, "sourceOfTruth", "Invoice.customerId");
    }

    // ── B4 — note → Javadoc yorumu (fixture doğal: CreateInvoice.note) ────────────────────────────

    @Test
    void note_emitsJavadocComment_onRequestRecord(@TempDir Path outDir) throws IOException {
        ManifestJson m = fixtureManifest();
        BuildReport report = new BuildReport();
        SpringEmitter.emit(gmFrom(m, fixtureContract(m)), outDir, report, h2Config());

        String content = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceCommand.java");
        assertTrue(content.contains("/**\n * Müşteri kredi limiti dış servisten gelir.\n */"));
        assertTrue(report.covers("note", "CreateInvoice"));
        noDrop(report, "note", "CreateInvoice");
    }
}
