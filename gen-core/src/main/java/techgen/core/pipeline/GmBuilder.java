package techgen.core.pipeline;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import techgen.core.errors.JoinError;
import techgen.core.gm.GenerationModel;
import techgen.core.gm.GmOperation;
import techgen.core.gm.TestPlan;
import techgen.core.gm.TypeEnv;
import techgen.core.model.CallEdgeJson;
import techgen.core.model.ContractFile;
import techgen.core.model.ContractOp;
import techgen.core.model.EntityJson;
import techgen.core.model.ManifestJson;
import techgen.core.model.OperationJson;
import techgen.core.model.ParamJson;
import techgen.core.model.SubscriptionJson;

/**
 * Aşama 2+3 (Join &amp; Validate → Generation Model). Hedef-bağımsız
 * (davranış sözleşmesi §5, CoreTemplate1 {@code Pipeline/GmBuilder.cs} karşılığı).
 *
 * <p>Not (T2.1 → T2.2 seam): {@link GenerationModel#testPlan()} bu sınıfta HENÜZ
 * {@link TestPlan#empty()} ile doldurulur — gerçek {@code TestPlanBuilder.build(contract,
 * m.operations())} çağrısı T2.2'nin işidir (bkz. {@code tasks/T2-2-testplan.md}).</p>
 */
public final class GmBuilder {

    private GmBuilder() {
    }

    public static GenerationModel build(ManifestJson m, /* @Nullable */ ContractFile contract) {
        boolean linked = "linked".equals(m.mode());

        if (linked && m.contract() != null && contract == null) {
            throw new JoinError("linked mod ama operations.json çözülemedi: " + m.contract());
        }

        // standalone'da join N/A (INV-9); linked'de contract Operations/Entities Id ile indekslenir.
        Map<String, ContractOp> contractOps = new HashMap<>();
        Set<String> contractEntityIds = new HashSet<>();
        if (linked && contract != null) {
            if (contract.operations() != null) {
                for (ContractOp co : contract.operations()) {
                    contractOps.put(co.id(), co);
                }
            }
            if (contract.entities() != null) {
                contract.entities().forEach(ce -> contractEntityIds.add(ce.id()));
            }
        }

        List<GmOperation> operations = m.operations().stream()
                .sorted(Comparator.comparing(OperationJson::id))
                .map(o -> buildOp(o, linked, contractOps))
                .toList();

        if (linked) {
            for (EntityJson e : m.entities()) {
                for (String rid : e.realizes()) {
                    if (!contractEntityIds.contains(rid)) {
                        throw new JoinError("entity '" + e.id() + "' realizes '" + rid + "' operations.json'da yok");
                    }
                }
            }
        }

        Map<String, Map<String, String>> opParams = new HashMap<>();
        for (OperationJson o : m.operations()) {
            Map<String, String> params = new HashMap<>();
            for (ParamJson p : o.signature().params()) {
                params.put(p.name(), p.type());
            }
            opParams.put(o.id(), params);
        }

        Map<String, Map<String, String>> entityFields = new HashMap<>();
        for (EntityJson e : m.entities()) {
            Map<String, String> fields = new HashMap<>();
            e.fields().forEach(f -> fields.put(f.name(), f.type()));
            entityFields.put(e.id(), fields);
        }

        TypeEnv env = new TypeEnv(opParams, entityFields);

        return new GenerationModel(
                m.mode(),
                m.modules().stream().sorted(Comparator.comparing(techgen.core.model.ModuleDecl::name)).toList(),
                operations,
                m.entities().stream().sorted(Comparator.comparing(EntityJson::id)).toList(),
                m.types().stream().sorted(Comparator.comparing(techgen.core.model.TypeJson::id)).toList(),
                m.events().stream().sorted(Comparator.comparing(techgen.core.model.EventJson::id)).toList(),
                m.subscriptions().stream()
                        .sorted(Comparator
                                .<SubscriptionJson, String>comparing(s -> s.event().module())
                                .thenComparing(s -> s.event().name())
                                .thenComparing(s -> s.consumer().op()))
                        .toList(),
                m.errors().stream().sorted(Comparator.comparing(techgen.core.model.ErrorJson::id)).toList(),
                m.externals().stream().sorted(Comparator.comparing(techgen.core.model.ExternalJson::name)).toList(),
                m.callEdges().stream()
                        .sorted(Comparator
                                .<CallEdgeJson, String>comparing(CallEdgeJson::from)
                                .thenComparing(c -> c.to().system())
                                .thenComparing(c -> c.to().op()))
                        .toList(),
                m.deployables().stream().sorted(Comparator.comparing(techgen.core.model.Deployable::name)).toList(),
                m.uncharted().stream().sorted(Comparator.comparing(techgen.core.model.UnchartedJson::name)).toList(),
                env,
                TestPlan.empty());
    }

    private static GmOperation buildOp(OperationJson o, boolean linked, Map<String, ContractOp> contractOps) {
        ContractOp business = null;
        if (linked && o.realizes() != null) {
            business = contractOps.get(o.realizes());
            if (business == null) {
                throw new JoinError("operation '" + o.id() + "' realizes '" + o.realizes() + "' operations.json'da yok");
            }
        }
        boolean isCommand = !o.access().creates().isEmpty()
                || !o.access().updates().isEmpty()
                || !o.access().deletes().isEmpty();
        return new GmOperation(o, business, isCommand);
    }
}
