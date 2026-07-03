package techgen.core.pipeline;

import org.junit.jupiter.api.Test;

import techgen.core.gm.PrereqKind;
import techgen.core.gm.PrereqStep;
import techgen.core.gm.ProcessTest;
import techgen.core.gm.TestPlan;
import techgen.core.model.AccessJson;
import techgen.core.model.ContractAccess;
import techgen.core.model.ContractFile;
import techgen.core.model.ContractOp;
import techgen.core.model.FlowJson;
import techgen.core.model.FlowStep;
import techgen.core.model.ManifestJson;
import techgen.core.model.OperationJson;
import techgen.core.model.ProcessJson;
import techgen.core.model.ProcessStage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T2.2 — TestPlanBuilder testleri (davranış sözleşmesi §6).
 * fixture (boş-plan yolu) + studyo (pozitif, gerçek veri) + 7 sentetik senaryo (a-g):
 * (a) tek-creator SINGLE, (b) iki-creator AMBIGUOUS, (c) sıfır-creator MISSING,
 * (d) topo-sort, (e) döngü fallback, (f) dangling flow skip, (g) dup op id son-kazanır.
 */
class TestPlanBuilderTest {

    // --- fixture path helper (GmBuilderTest/LoaderTest ile aynı desen) -----------------------

    private static Path fixture(String name) {
        Path fromModuleDir = Path.of("../fixtures", name);
        if (Files.exists(fromModuleDir)) {
            return fromModuleDir;
        }
        return Path.of("fixtures", name);
    }

    // --- sentetik model inşa yardımcıları ------------------------------------------------------

    private static OperationJson op(String id, List<String> reads, List<String> creates,
            List<String> updates, List<String> deletes) {
        return new OperationJson(id, "M", "exposed", null, null, null, null, null,
                new AccessJson(reads, creates, updates, deletes),
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static ProcessJson process(String id, List<ProcessStage> items) {
        return new ProcessJson(id, null, null, items);
    }

    private static ProcessStage stage(String flow) {
        return new ProcessStage("stage", "S", null, flow, null);
    }

    private static FlowJson flow(String id, List<FlowStep> items) {
        return new FlowJson(id, null, null, items);
    }

    private static FlowStep step(String target) {
        return new FlowStep("step", "T", target, false, false, null);
    }

    private static ContractFile contract(List<ProcessJson> processes, List<FlowJson> flows) {
        return new ContractFile(null, null, null, null, null, processes, flows);
    }

    // --- Boş-plan yolu: processes/flows yok --------------------------------------------------

    @Test
    void build_nullContract_returnsEmptyPlan() {
        assertEquals(TestPlan.empty(), TestPlanBuilder.build(null, List.of()));
    }

    @Test
    void build_fixtureOperations_noProcessesNoFlows_returnsEmptyPlan() {
        ManifestJson m = Loader.loadManifest(fixture("manifest.json"));
        ContractFile c = Loader.loadContract(fixture("manifest.json"), "./operations.json");
        assertNull(c.processes(), "fixtures/operations.json'da process yok (boş-plan ön-şartı)");
        assertNull(c.flows(), "fixtures/operations.json'da flow yok (boş-plan ön-şartı)");

        TestPlan plan = TestPlanBuilder.build(c, m.operations());
        assertEquals(TestPlan.empty(), plan);
    }

    // --- Pozitif: studyo (gerçek veri) --------------------------------------------------------

    private static TestPlan studyoPlan() {
        ManifestJson m = Loader.loadManifest(fixture("studyo.manifest.json"));
        ContractFile c = Loader.loadContract(fixture("studyo.manifest.json"), "./studyo.operations.json");
        return TestPlanBuilder.build(c, m.operations());
    }

    @Test
    void build_studyo_threeListCounts() {
        TestPlan plan = studyoPlan();
        assertEquals(4, plan.processTests().size(), "4 process (TakvimSureci/UyelikPaketSureci/RandevuKatilimSureci/PaketYonetimSureci)");
        assertEquals(1, plan.orphanFlowTests().size(), "yalnız DersTipiYonetimi hiçbir process'te referans edilmiyor");
        assertEquals("DersTipiYonetimi", plan.orphanFlowTests().get(0).id());
        assertEquals(18, plan.orphanOpTests().size(), "contract.operations(42) - opsInFlow(24) = 18");
    }

    @Test
    void build_studyo_takvimSureci_runSequenceIsItemsOrder_notSorted_withSinglePrereq() {
        TestPlan plan = studyoPlan();
        ProcessTest pt = plan.processTests().stream()
                .filter(p -> p.processId().equals("TakvimSureci"))
                .findFirst().orElseThrow();

        List<String> expectedItemsOrder = List.of("OlusturSeans", "AtaEgitmen", "GuncelleSeans", "IptalSeans", "ListSeanslarim");
        assertEquals(expectedItemsOrder, pt.runSequence(), "RunSequence = contract'taki Items sırası (SeansPlanlama flow'u)");

        List<String> sorted = expectedItemsOrder.stream().sorted().toList();
        assertNotEquals(sorted, pt.runSequence(),
                "kanıt: Items sırası sıralanmış (ordinal) sıradan FARKLI — RunSequence ordinal DEĞİL");

        // needed = {ClassType} (OlusturSeans.reads); tek creator: TanimlaDersTipi → SINGLE.
        assertEquals(List.of(new PrereqStep("ClassType", "TanimlaDersTipi", PrereqKind.SINGLE)), pt.prerequisites(),
                "≥1 SINGLE prereq örneği: ClassType/TanimlaDersTipi (creatorOp dolu)");
        assertEquals(List.of("Session"), pt.writeSet(), "Creates(Session)∪Updates(Session) ordinal-distinct");
    }

    // --- Sentetik (a): tek-creator → SINGLE + creatorOp dolu ------------------------------------

    @Test
    void derive_singleCreator_classifiesSingle_creatorOpPopulated() {
        OperationJson create = op("Create", List.of(), List.of("E"), List.of(), List.of());
        OperationJson use = op("Use", List.of("E"), List.of(), List.of(), List.of());
        ContractFile c = contract(
                List.of(process("P", List.of(stage("F")))),
                List.of(flow("F", List.of(step("Use")))));

        TestPlan plan = TestPlanBuilder.build(c, List.of(create, use));
        ProcessTest pt = plan.processTests().get(0);

        assertEquals(List.of(new PrereqStep("E", "Create", PrereqKind.SINGLE)), pt.prerequisites());
    }

    // --- Sentetik (b): iki-creator → AMBIGUOUS + creatorOp null ---------------------------------

    @Test
    void derive_twoCreators_classifiesAmbiguous_creatorOpNull_neverAutoSelected() {
        OperationJson createA = op("CreateA", List.of(), List.of("E"), List.of(), List.of());
        OperationJson createB = op("CreateB", List.of(), List.of("E"), List.of(), List.of());
        OperationJson use = op("Use", List.of("E"), List.of(), List.of(), List.of());
        ContractFile c = contract(
                List.of(process("P", List.of(stage("F")))),
                List.of(flow("F", List.of(step("Use")))));

        TestPlan plan = TestPlanBuilder.build(c, List.of(createA, createB, use));
        ProcessTest pt = plan.processTests().get(0);

        assertEquals(1, pt.prerequisites().size());
        PrereqStep prereq = pt.prerequisites().get(0);
        assertEquals(PrereqKind.AMBIGUOUS, prereq.kind());
        assertNull(prereq.creatorOp(), "AMBIGUOUS'ta creator ASLA otomatik seçilmez (ne CreateA ne CreateB)");
    }

    // --- Sentetik (c): sıfır-creator → MISSING ---------------------------------------------------

    @Test
    void derive_zeroCreators_classifiesMissing_creatorOpNull() {
        OperationJson use = op("Use", List.of("E"), List.of(), List.of(), List.of());
        ContractFile c = contract(
                List.of(process("P", List.of(stage("F")))),
                List.of(flow("F", List.of(step("Use")))));

        TestPlan plan = TestPlanBuilder.build(c, List.of(use));
        ProcessTest pt = plan.processTests().get(0);

        assertEquals(List.of(new PrereqStep("E", null, PrereqKind.MISSING)), pt.prerequisites());
    }

    // --- Sentetik (d): topo-sort — e1 creator'ı e2'yi okuyor → e2 önce -----------------------------

    @Test
    void derive_topoSort_dependencyCreatorReadsOtherEntity_dependencyFirst() {
        // CreateX creates X ama Y'yi okur (X, Y'ye bağımlı) → Y önce gelmeli.
        OperationJson createX = op("CreateX", List.of("Y"), List.of("X"), List.of(), List.of());
        OperationJson createY = op("CreateY", List.of(), List.of("Y"), List.of(), List.of());
        OperationJson useXY = op("UseXY", List.of("X", "Y"), List.of(), List.of(), List.of());
        ContractFile c = contract(
                List.of(process("P", List.of(stage("F")))),
                List.of(flow("F", List.of(step("UseXY")))));

        TestPlan plan = TestPlanBuilder.build(c, List.of(createX, createY, useXY));
        ProcessTest pt = plan.processTests().get(0);

        assertEquals(List.of(
                new PrereqStep("Y", "CreateY", PrereqKind.SINGLE),
                new PrereqStep("X", "CreateX", PrereqKind.SINGLE)), pt.prerequisites(),
                "CreateX(=X'in creator'ı) Y'yi okuyor → Y, X'ten önce sıralanmalı");
    }

    // --- Sentetik (e): döngü (e1↔e2) → en-küçük-ordinal fallback, sonlanır --------------------------

    @Test
    void derive_cycleBetweenCreators_smallestOrdinalFallback_terminates() {
        // CreateA creates A, B'yi okur; CreateB creates B, A'yı okur → A↔B döngüsü.
        OperationJson createA = op("CreateA", List.of("B"), List.of("A"), List.of(), List.of());
        OperationJson createB = op("CreateB", List.of("A"), List.of("B"), List.of(), List.of());
        OperationJson useAB = op("UseAB", List.of("A", "B"), List.of(), List.of(), List.of());
        ContractFile c = contract(
                List.of(process("P", List.of(stage("F")))),
                List.of(flow("F", List.of(step("UseAB")))));

        // Sonsuz döngüye girmeden (test bloklanmadan) tamamlanmalı — bu, sonlanmanın kanıtıdır.
        TestPlan plan = assertDoesNotThrow(() -> TestPlanBuilder.build(c, List.of(createA, createB, useAB)));
        ProcessTest pt = plan.processTests().get(0);

        // Döngüde hiçbir bağımlılık tükenmemiş: fallback en küçük ordinal (A) önce seçilir.
        assertEquals(List.of(
                new PrereqStep("A", "CreateA", PrereqKind.SINGLE),
                new PrereqStep("B", "CreateB", PrereqKind.SINGLE)), pt.prerequisites());
    }

    // --- Ek: SINGLE + AMBIGUOUS + MISSING AYNI runSeq'te → SINGLE'lar topo-sıralı ÖNCE,
    //     AMBIGUOUS/MISSING sona entity-id ordinal (a-g'nin dışında, "sona ordinal" yerleşimini
    //     tek başına test eden a/b/c'nin ötesinde karışık-sınıflama kapsama boşluğunu kapatır).

    @Test
    void derive_mixedSingleAmbiguousMissing_singlesFirstTopoSorted_deferredAppendedOrdinalByEntity() {
        OperationJson createS = op("CreateS", List.of(), List.of("S"), List.of(), List.of());
        OperationJson createA1 = op("CreateA1", List.of(), List.of("A"), List.of(), List.of());
        OperationJson createA2 = op("CreateA2", List.of(), List.of("A"), List.of(), List.of());
        OperationJson useSAM = op("UseSAM", List.of("S", "A", "M"), List.of(), List.of(), List.of());
        ContractFile c = contract(
                List.of(process("P", List.of(stage("F")))),
                List.of(flow("F", List.of(step("UseSAM")))));

        TestPlan plan = TestPlanBuilder.build(c, List.of(createS, createA1, createA2, useSAM));
        ProcessTest pt = plan.processTests().get(0);

        assertEquals(List.of(
                new PrereqStep("S", "CreateS", PrereqKind.SINGLE),
                new PrereqStep("A", null, PrereqKind.AMBIGUOUS),
                new PrereqStep("M", null, PrereqKind.MISSING)), pt.prerequisites(),
                "SINGLE(S) önce (topo-sort), sonra AMBIGUOUS/MISSING entity-id ordinal (A < M) sona eklenir");
    }

    // --- Sentetik (f): dangling flow → skip, throw yok ------------------------------------------

    @Test
    void build_danglingFlowReference_skipsStage_noThrow_otherStagesStillProcessed() {
        OperationJson use2 = op("Use2", List.of(), List.of(), List.of(), List.of());
        ContractFile c = contract(
                List.of(process("P", List.of(stage("Missing"), stage("F")))),
                List.of(flow("F", List.of(step("Use2"))))); // "Missing" flowById'de YOK

        TestPlan plan = assertDoesNotThrow(() -> TestPlanBuilder.build(c, List.of(use2)));
        ProcessTest pt = plan.processTests().get(0);

        assertEquals(List.of("Use2"), pt.runSequence(),
                "dangling 'Missing' stage'i atlanır (throw yok); gerçek 'F' stage'i işlenmeye devam eder");
    }

    // --- Sentetik (g): dup op id → son kazanır ----------------------------------------------------

    @Test
    void build_duplicateOperationId_lastEntryWins() {
        OperationJson first = op("X", List.of(), List.of("A"), List.of(), List.of());
        OperationJson second = op("X", List.of(), List.of("B"), List.of(), List.of());
        ContractFile c = contract(
                List.of(process("P", List.of(stage("F")))),
                List.of(flow("F", List.of(step("X")))));

        TestPlan plan = TestPlanBuilder.build(c, List.of(first, second));
        ProcessTest pt = plan.processTests().get(0);

        assertEquals(List.of("B"), pt.writeSet(), "dup id 'X': ikinci (son) girdi kazanır → Creates=[B]");
        assertTrue(pt.prerequisites().isEmpty(), "son girdinin (B) reads/updates'i boş → prereq yok");
    }
}
