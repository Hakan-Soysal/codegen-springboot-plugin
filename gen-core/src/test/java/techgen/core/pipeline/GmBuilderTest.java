package techgen.core.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import techgen.core.Json;
import techgen.core.errors.JoinError;
import techgen.core.gm.GenerationModel;
import techgen.core.gm.GmOperation;
import techgen.core.gm.TestPlan;
import techgen.core.gm.TypeEnv;
import techgen.core.model.CallEdgeJson;
import techgen.core.model.ContractFile;
import techgen.core.model.ManifestJson;
import techgen.core.model.ModuleDecl;
import techgen.core.model.SubscriptionJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T2.1 — GmBuilder (join &amp; GM) + TypeEnv testleri (davranış sözleşmesi §5).
 *
 * <p>TestPlan yer-tutucu kararı (bkz. rapor): {@link GenerationModel#testPlan()} bu task'ta
 * daima {@link TestPlan#empty()} — T2.2 gerçek {@code TestPlanBuilder} çıktısını takar.</p>
 */
class GmBuilderTest {

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

    private static ContractFile fixtureContract() {
        return Loader.loadContract(fixture("manifest.json"), "./operations.json");
    }

    private static GenerationModel fixtureGm() {
        return GmBuilder.build(fixtureManifest(), fixtureContract());
    }

    /** manifest.json'u JsonNode olarak okuyup mutasyon uygulamak ve tekrar ManifestJson'a ayrıştırmak için. */
    private static ManifestJson mutateManifest(java.util.function.Consumer<ObjectNode> mutator) throws IOException {
        JsonNode tree = Json.mapper().readTree(fixture("manifest.json").toFile());
        ObjectNode root = (ObjectNode) tree;
        mutator.accept(root);
        return Json.mapper().treeToValue(root, ManifestJson.class);
    }

    // --- Pozitif: fixture join ---------------------------------------------------------------

    @Test
    void build_fixture_createInvoice_hasBusinessAndCommandKind() {
        GenerationModel gm = fixtureGm();
        GmOperation createInvoice = opById(gm, "CreateInvoice");
        assertNotNull(createInvoice.business(), "CreateInvoice.realizes='biz.CreateInvoice' çözülmeli");
        assertEquals("command", createInvoice.business().kind());
    }

    @Test
    void build_fixture_getInvoice_hasNoBusiness_realizesIsNull() {
        GenerationModel gm = fixtureGm();
        GmOperation getInvoice = opById(gm, "GetInvoice");
        assertNull(getInvoice.business(), "GetInvoice.realizes==null → business null");
    }

    @Test
    void build_fixture_isCommand_derivedFromManifestAccess4Keys() {
        GenerationModel gm = fixtureGm();
        assertTrue(opById(gm, "CreateInvoice").isCommand(), "creates=[Invoice] → command");
        assertTrue(opById(gm, "WriteAuditLog").isCommand(), "creates=[AuditLog] → command");
        assertFalse(opById(gm, "GetInvoice").isCommand(), "yalnız reads → query");
        assertFalse(opById(gm, "ListInvoices").isCommand(), "yalnız reads → query");
    }

    @Test
    void build_fixture_operations_sortedOrdinalById() {
        GenerationModel gm = fixtureGm();
        List<String> ids = gm.operations().stream().map(GmOperation::id).toList();
        assertEquals(List.of("CreateInvoice", "GetInvoice", "ListInvoices", "WriteAuditLog"), ids);
    }

    @Test
    void build_fixture_moduleAndIdShortcuts_delegateToOp() {
        GenerationModel gm = fixtureGm();
        GmOperation createInvoice = opById(gm, "CreateInvoice");
        assertEquals("CreateInvoice", createInvoice.id());
        assertEquals("Billing", createInvoice.module());
    }

    @Test
    void build_fixture_testPlanIsEmptyPlaceholder_untilT2_2() {
        GenerationModel gm = fixtureGm();
        assertEquals(TestPlan.empty(), gm.testPlan(), "T2.1: testPlan her zaman boş yer-tutucu (T2.2'de gerçek IR)");
    }

    // --- Pozitif: koleksiyon sıralaması (anahtarlar sözleşmeyle birebir) --------------------

    @Test
    void build_modulesReverseOrder_sortedOrdinalByName() throws IOException {
        ManifestJson m = mutateManifest(root -> {
            ArrayNode modules = (ArrayNode) root.get("modules");
            // ters sırala: girdi zaten alfabetik olduğundan reverse ile determinizmi kanıtla.
            ArrayNode reversed = modules.arrayNode();
            for (int i = modules.size() - 1; i >= 0; i--) {
                reversed.add(modules.get(i));
            }
            root.set("modules", reversed);
        });
        GenerationModel gm = GmBuilder.build(m, fixtureContract());
        List<String> names = gm.modules().stream().map(ModuleDecl::name).toList();
        assertEquals(List.of("Billing", "Ops"), names, "girdi sırası ne olursa olsun ordinal(name) çıktı");
    }

    @Test
    void build_subscriptions_sortedByEventModuleThenNameThenConsumerOp() {
        GenerationModel gm = fixtureGm();
        assertEquals(1, gm.subscriptions().size());
        SubscriptionJson s = gm.subscriptions().get(0);
        assertEquals("Billing", s.event().module());
        assertEquals("InvoiceCreated", s.event().name());
        assertEquals("WriteAuditLog", s.consumer().op());
    }

    @Test
    void build_callEdges_sortedByFromThenToSystemThenToOp() {
        GenerationModel gm = fixtureGm();
        List<String> froms = gm.callEdges().stream().map(CallEdgeJson::from).toList();
        assertEquals(List.of("CreateInvoice", "WriteAuditLog"), froms, "from ordinal: CreateInvoice < WriteAuditLog");
    }

    // --- Negatif: ÜÇ JoinError koşulu (mesajlarla) --------------------------------------------

    @Test
    void build_linkedModeContractPresentButUnresolved_throwsJoinError_exactMessage() {
        ManifestJson m = fixtureManifest();
        assertEquals("linked", m.mode());
        assertNotNull(m.contract());

        JoinError err = assertThrows(JoinError.class, () -> GmBuilder.build(m, null));
        assertEquals("linked mod ama operations.json çözülemedi: " + m.contract(), err.getMessage());
    }

    @Test
    void build_operationRealizesUnresolvedInContract_throwsJoinError_exactMessage() throws IOException {
        ManifestJson m = mutateManifest(root -> {
            ArrayNode ops = (ArrayNode) root.get("operations");
            ((ObjectNode) ops.get(0)).put("realizes", "biz.DoesNotExist");
        });
        ContractFile contract = fixtureContract();

        JoinError err = assertThrows(JoinError.class, () -> GmBuilder.build(m, contract));
        assertEquals("operation 'CreateInvoice' realizes 'biz.DoesNotExist' operations.json'da yok", err.getMessage());
    }

    @Test
    void build_entityRealizesUnresolvedInContract_throwsJoinError_exactMessage() throws IOException {
        ManifestJson m = mutateManifest(root -> {
            ArrayNode entities = (ArrayNode) root.get("entities");
            ObjectNode invoice = (ObjectNode) entities.get(0);
            ArrayNode realizes = invoice.putArray("realizes");
            realizes.add("biz.DoesNotExist");
        });
        ContractFile contract = fixtureContract();

        JoinError err = assertThrows(JoinError.class, () -> GmBuilder.build(m, contract));
        assertEquals("entity 'Invoice' realizes 'biz.DoesNotExist' operations.json'da yok", err.getMessage());
    }

    // --- Standalone: join N/A, business her zaman null, JoinError YOK ------------------------

    @Test
    void build_standaloneMode_joinSkipped_businessAlwaysNull_evenIfRealizesSet() throws IOException {
        ManifestJson m = mutateManifest(root -> root.put("mode", "standalone"));
        assertEquals("standalone", m.mode());

        GenerationModel gm = GmBuilder.build(m, null);
        for (GmOperation op : gm.operations()) {
            assertNull(op.business(), "standalone: join N/A → business her zaman null (" + op.id() + ")");
        }
        assertTrue(opById(gm, "CreateInvoice").isCommand(), "isCommand standalone'da da manifest access'ten türer");
    }

    // --- TypeEnv.resolvePath -------------------------------------------------------------------

    @Test
    void resolvePath_barePathOnOpParam_returnsParamType() {
        GenerationModel gm = fixtureGm();
        TypeEnv env = gm.env();
        String type = env.resolvePath(gm, List.of("amount"), "CreateInvoice", null);
        assertEquals("Decimal", type, "bare path → op param tipi");
    }

    @Test
    void resolvePath_resourcePath_writeTargetHasNoField_returnsNull() {
        GenerationModel gm = fixtureGm();
        TypeEnv env = gm.env();
        // CreateInvoice writeTarget=Invoice; Invoice'ta 'creditLimit' alanı YOK.
        String type = env.resolvePath(gm, List.of("resource", "creditLimit"), "CreateInvoice", null);
        assertNull(type, "resource.creditLimit çözülemez → null (Invoice'ta alan yok)");
    }

    @Test
    void resolvePath_actorPath_alwaysReturnsString() {
        GenerationModel gm = fixtureGm();
        TypeEnv env = gm.env();
        assertEquals("String", env.resolvePath(gm, List.of("actor", "tenant"), "WriteAuditLog", null));
        assertEquals("String", env.resolvePath(gm, List.of("actor", "anything"), null, null), "actor.* opId olmasa da String");
    }

    @Test
    void resolvePath_emptyPath_returnsNull() {
        GenerationModel gm = fixtureGm();
        assertNull(gm.env().resolvePath(gm, List.of(), "CreateInvoice", null));
    }

    @Test
    void resolvePath_barePathOnEntityField_returnsFieldType() {
        GenerationModel gm = fixtureGm();
        String type = gm.env().resolvePath(gm, List.of("amount"), null, "Invoice");
        assertEquals("Decimal", type, "bare path (opId null) → entity alan tipi (invariant bağlamı)");
    }

    @Test
    void writeTarget_pickFirstOfCreatesUpdatesDeletes_inThatOrder() {
        GenerationModel gm = fixtureGm();
        assertEquals("Invoice", TypeEnv.writeTarget(opById(gm, "CreateInvoice")));
        assertEquals("AuditLog", TypeEnv.writeTarget(opById(gm, "WriteAuditLog")));
        assertNull(TypeEnv.writeTarget(opById(gm, "GetInvoice")), "creates/updates/deletes hepsi boş → null");
    }

    private static GmOperation opById(GenerationModel gm, String id) {
        return gm.operations().stream().filter(o -> o.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("op bulunamadı: " + id));
    }
}
