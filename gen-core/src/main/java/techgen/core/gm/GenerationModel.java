package techgen.core.gm;

import java.util.List;

import techgen.core.model.CallEdgeJson;
import techgen.core.model.Deployable;
import techgen.core.model.EntityJson;
import techgen.core.model.ErrorJson;
import techgen.core.model.EventJson;
import techgen.core.model.ExternalJson;
import techgen.core.model.ModuleDecl;
import techgen.core.model.SubscriptionJson;
import techgen.core.model.TypeJson;
import techgen.core.model.UnchartedJson;

/**
 * Hedef-nötr IR (davranış sözleşmesi §5, {@code GenerationModel.cs:10-24} karşılığı).
 * manifest ⋈ operations.json join ürünü. Build-time; runtime semantiği yok.
 * Tüm koleksiyonlar {@link techgen.core.pipeline.GmBuilder} tarafından ordinal (UTF-16
 * kod-birimi, {@code String::compareTo}) sıralı üretilir.
 */
public record GenerationModel(
        String mode,
        List<ModuleDecl> modules,
        List<GmOperation> operations,
        List<EntityJson> entities,
        List<TypeJson> types,
        List<EventJson> events,
        List<SubscriptionJson> subscriptions,
        List<ErrorJson> errors,
        List<ExternalJson> externals,
        List<CallEdgeJson> callEdges,
        List<Deployable> deployables,
        List<UnchartedJson> uncharted,
        TypeEnv env,
        TestPlan testPlan) {
}
