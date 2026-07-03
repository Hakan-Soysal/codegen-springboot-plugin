package techgen.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Manifest.cs:75-77 — anahtar Name. External gibi çağrı-adapter AMA kendi
 * entity/type'larını OWN eder (T-4.3). deployable @Nullable.
 */
public record UnchartedJson(
        String name,
        boolean generated,
        /* @Nullable */ String deployable,
        List<BoundaryOpJson> operations,
        List<UnchartedEntity> entities,
        List<UnchartedType> types) {

    public UnchartedJson {
        operations = Objects.requireNonNullElse(operations, List.of());
        entities = Objects.requireNonNullElse(entities, List.of());
        types = Objects.requireNonNullElse(types, List.of());
    }
}
