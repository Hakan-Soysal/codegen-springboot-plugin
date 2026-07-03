package techgen.core.model;

import java.util.List;
import java.util.Objects;

/** Manifest.cs:73. concurrency/ext @Nullable. */
public record UnchartedEntity(
        String id,
        List<String> realizes,
        List<EntityFieldJson> fields,
        /* @Nullable */ String concurrency,
        /* @Nullable */ List<ExtJson> ext) {

    public UnchartedEntity {
        realizes = Objects.requireNonNullElse(realizes, List.of());
        fields = Objects.requireNonNullElse(fields, List.of());
    }
}
