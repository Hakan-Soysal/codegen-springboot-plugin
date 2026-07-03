package techgen.core.pipeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import techgen.core.gm.PrereqKind;
import techgen.core.gm.PrereqStep;
import techgen.core.gm.ProcessTest;
import techgen.core.gm.ScenarioTest;
import techgen.core.gm.TestPlan;
import techgen.core.model.ContractFile;
import techgen.core.model.ContractOp;
import techgen.core.model.FlowJson;
import techgen.core.model.FlowStep;
import techgen.core.model.OperationJson;
import techgen.core.model.ProcessJson;
import techgen.core.model.ProcessStage;

/**
 * Containment (process→flow→op) + orphan (flow/op) türetir → deterministik {@link TestPlan}
 * (davranış sözleşmesi §6, CoreTemplate1 {@code Pipeline/TestPlanBuilder.cs} karşılığı).
 *
 * <p>Tüm çıktı listeleri ordinal-sıralı (GmBuilder determinizm pattern'ı). RunSequence içi sıra ise
 * contract-meaningful (Items sırası) — ORDINAL DEĞİL. Ön-gereksinim + WriteSet manifest.access
 * (Kapı 0 authority — 4-key Reads/Creates/Updates/Deletes) üzerinden türetilir; çoklu/sıfır creator
 * → DUR (PrereqKind.AMBIGUOUS/MISSING, creatorOp=null — ASLA bir creator seçilmez).</p>
 */
public final class TestPlanBuilder {

    private TestPlanBuilder() {
    }

    public static TestPlan build(ContractFile contract, List<OperationJson> manifestOps) {
        // Standalone / eski contract / eksik strüktür → boş plan (çökme YOK).
        if (contract == null || contract.processes() == null || contract.flows() == null) {
            return TestPlan.empty();
        }

        List<OperationJson> ops = manifestOps != null ? manifestOps : List.of();

        // --- Ön-gereksinim altyapısı: op-id index + creator ters-index (manifest.access, ordinal) ---
        Map<String, OperationJson> opById = new HashMap<>();
        for (OperationJson op : ops) {
            opById.put(op.id(), op); // dup id → son kazanır (toleranslı)
        }

        // creators: entity → [op.Id where Access.Creates contains entity] (Id-ordinal gezinme, distinct).
        Map<String, List<String>> creators = new HashMap<>();
        List<OperationJson> opsIdOrdinal = ops.stream()
                .sorted(Comparator.comparing(OperationJson::id))
                .toList();
        for (OperationJson op : opsIdOrdinal) {
            for (String e : op.access().creates()) {
                List<String> list = creators.computeIfAbsent(e, k -> new ArrayList<>());
                if (!list.contains(op.id())) {
                    list.add(op.id());
                }
            }
        }

        Map<String, FlowJson> flowById = new HashMap<>();
        for (FlowJson f : contract.flows()) {
            flowById.put(f.id(), f); // dup → son kazanır (toleranslı; küme-mantığı bozulmaz)
        }

        // --- ProcessTests: containment RunSequence (Items sırası korunur) ---
        Set<String> flowsInProcess = new HashSet<>();
        List<ProcessTest> processTests = new ArrayList<>();
        for (ProcessJson p : contract.processes()) {
            List<String> runSeq = new ArrayList<>();
            List<ProcessStage> stages = p.items() != null ? p.items() : List.of();
            for (ProcessStage stage : stages) {
                if (stage.flow() == null) {
                    continue;
                }
                flowsInProcess.add(stage.flow());
                FlowJson flow = flowById.get(stage.flow());
                if (flow == null) {
                    continue; // dangling → skip+devam (NO throw)
                }
                List<FlowStep> flowItems = flow.items() != null ? flow.items() : List.of();
                for (FlowStep step : flowItems) {
                    if (step.target() != null) {
                        runSeq.add(step.target());
                    }
                }
            }
            DeriveResult derived = derive(runSeq, opById, creators);
            processTests.add(new ProcessTest(p.id(), p.entity(), runSeq, derived.prerequisites(), derived.writeSet()));
        }

        // --- opsInFlow: TÜM flow'ların (orphan dahil) target'ları (set) — küme-farkından ÖNCE hesaplanır ---
        Set<String> opsInFlow = new HashSet<>();
        for (FlowJson f : contract.flows()) {
            List<FlowStep> flowItems = f.items() != null ? f.items() : List.of();
            for (FlowStep step : flowItems) {
                if (step.target() != null) {
                    opsInFlow.add(step.target());
                }
            }
        }

        // --- OrphanFlowTests: Flows \ flowsInProcess ---
        List<ScenarioTest> orphanFlowTests = new ArrayList<>();
        for (FlowJson f : contract.flows()) {
            if (flowsInProcess.contains(f.id())) {
                continue;
            }
            List<String> runSeq = new ArrayList<>();
            List<FlowStep> flowItems = f.items() != null ? f.items() : List.of();
            for (FlowStep step : flowItems) {
                if (step.target() != null) {
                    runSeq.add(step.target());
                }
            }
            DeriveResult derived = derive(runSeq, opById, creators);
            orphanFlowTests.add(new ScenarioTest(f.id(), "OrphanFlow", runSeq, derived.prerequisites(), derived.writeSet()));
        }

        // --- OrphanOpTests: contract Operations \ opsInFlow ---
        List<ScenarioTest> orphanOpTests = new ArrayList<>();
        List<ContractOp> contractOperations = contract.operations() != null ? contract.operations() : List.of();
        for (ContractOp op : contractOperations) {
            if (!opsInFlow.contains(op.id())) {
                List<String> runSeq = List.of(op.id());
                DeriveResult derived = derive(runSeq, opById, creators);
                orphanOpTests.add(new ScenarioTest(op.id(), "OrphanOp", runSeq, derived.prerequisites(), derived.writeSet()));
            }
        }

        return new TestPlan(
                processTests.stream().sorted(Comparator.comparing(ProcessTest::processId)).toList(),
                orphanFlowTests.stream().sorted(Comparator.comparing(ScenarioTest::id)).toList(),
                orphanOpTests.stream().sorted(Comparator.comparing(ScenarioTest::id)).toList());
    }

    private record DeriveResult(List<PrereqStep> prerequisites, List<String> writeSet) {
    }

    /**
     * produced = runSeq Creates birleşimi; needed = (Reads ∪ Updates) − produced; WriteSet =
     * Creates∪Updates∪Deletes ordinal distinct. Manifest'te olmayan opId → skip (toleranslı).
     * Sınıflama: creators sayısı 1→SINGLE; &gt;1→AMBIGUOUS(creatorOp=null); 0→MISSING(creatorOp=null).
     * SINGLE'lar topo-sort: e1→e2 kenarı = creators[e1][0]'ın (Reads∪Updates)'ı e2'yi içeriyorsa
     * (e2 önce gelmeli). Deterministik: her adımda deps'i tükenmiş en küçük ordinal entity; hiçbiri
     * uygun değilse (döngü) kalanların en küçük ordinali (sonsuz döngü YOK). AMBIGUOUS/MISSING sona,
     * entity-id ordinal.
     */
    private static DeriveResult derive(List<String> runSeq, Map<String, OperationJson> opById,
            Map<String, List<String>> creators) {
        Set<String> produced = new HashSet<>();
        Set<String> needed = new HashSet<>();
        Set<String> writeSet = new HashSet<>();

        for (String opId : runSeq) {
            OperationJson op = opById.get(opId);
            if (op == null) {
                continue; // manifest'te yoksa skip (toleranslı)
            }
            produced.addAll(op.access().creates());
        }
        for (String opId : runSeq) {
            OperationJson op = opById.get(opId);
            if (op == null) {
                continue;
            }
            needed.addAll(op.access().reads());
            needed.addAll(op.access().updates());
            writeSet.addAll(op.access().creates());
            writeSet.addAll(op.access().updates());
            writeSet.addAll(op.access().deletes());
        }
        needed.removeAll(produced);

        // Sınıflama: SINGLE / AMBIGUOUS / MISSING.
        List<String> single = new ArrayList<>();       // entity (creators count==1)
        List<PrereqStep> deferred = new ArrayList<>();  // AMBIGUOUS + MISSING
        for (String e : needed) {
            List<String> cl = creators.get(e);
            int count = cl != null ? cl.size() : 0;
            if (count == 1) {
                single.add(e);
            } else if (count > 1) {
                deferred.add(new PrereqStep(e, null, PrereqKind.AMBIGUOUS));
            } else {
                deferred.add(new PrereqStep(e, null, PrereqKind.MISSING));
            }
        }

        // Topo-sort SINGLE'lar.
        Set<String> singleSet = new HashSet<>(single);
        Map<String, Set<String>> deps = new HashMap<>();
        for (String e : single) {
            Set<String> d = new HashSet<>();
            OperationJson cop = opById.get(creators.get(e).get(0));
            if (cop != null) {
                for (String r : cop.access().reads()) {
                    if (!r.equals(e) && singleSet.contains(r)) {
                        d.add(r);
                    }
                }
                for (String u : cop.access().updates()) {
                    if (!u.equals(e) && singleSet.contains(u)) {
                        d.add(u);
                    }
                }
            }
            deps.put(e, d);
        }
        List<String> remaining = new ArrayList<>(single);
        remaining.sort(Comparator.naturalOrder());
        Set<String> emitted = new HashSet<>();
        List<PrereqStep> ordered = new ArrayList<>();
        while (!remaining.isEmpty()) {
            String pick = null;
            for (String e : remaining) {
                if (emitted.containsAll(deps.get(e))) {
                    pick = e;
                    break;
                }
            }
            if (pick == null) {
                pick = remaining.get(0); // döngü fallback: en küçük ordinal
            }
            ordered.add(new PrereqStep(pick, creators.get(pick).get(0), PrereqKind.SINGLE));
            emitted.add(pick);
            remaining.remove(pick);
        }
        deferred.sort(Comparator.comparing(PrereqStep::entity));
        ordered.addAll(deferred);

        List<String> writeSetList = writeSet.stream().sorted(Comparator.naturalOrder()).toList();
        return new DeriveResult(ordered, writeSetList);
    }
}
