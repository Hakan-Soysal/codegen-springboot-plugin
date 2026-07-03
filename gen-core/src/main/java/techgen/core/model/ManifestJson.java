package techgen.core.model;

import java.util.List;
import java.util.Objects;

/**
 * manifest.json kök şeması (davranış sözleşmesi §1, Manifest.cs:8-23).
 * {@code contract} = operations.json'a göreli yol (@Nullable).
 * {@code uncharted} = external-benzeri çağrı-adapter AMA kendi entity/type'larını OWN eder.
 * Liste alanları eksikse boş listeye normalize edilir.
 */
public record ManifestJson(
        String mode,
        /* @Nullable */ String contract,
        Meta meta,
        List<Deployable> deployables,
        List<ModuleDecl> modules,
        List<OperationJson> operations,
        List<EntityJson> entities,
        List<TypeJson> types,
        List<ErrorJson> errors,
        List<EventJson> events,
        List<SubscriptionJson> subscriptions,
        List<ExternalJson> externals,
        List<UnchartedJson> uncharted,
        List<CallEdgeJson> callEdges,
        Coverage coverage) {

    public ManifestJson {
        deployables = Objects.requireNonNullElse(deployables, List.of());
        modules = Objects.requireNonNullElse(modules, List.of());
        operations = Objects.requireNonNullElse(operations, List.of());
        entities = Objects.requireNonNullElse(entities, List.of());
        types = Objects.requireNonNullElse(types, List.of());
        errors = Objects.requireNonNullElse(errors, List.of());
        events = Objects.requireNonNullElse(events, List.of());
        subscriptions = Objects.requireNonNullElse(subscriptions, List.of());
        externals = Objects.requireNonNullElse(externals, List.of());
        uncharted = Objects.requireNonNullElse(uncharted, List.of());
        callEdges = Objects.requireNonNullElse(callEdges, List.of());
    }
}
