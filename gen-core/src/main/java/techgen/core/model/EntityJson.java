package techgen.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Manifest.cs:55-57 — anahtar Id. {@code realizes} ÇOĞULDUR (op'unki tekil).
 * {@code concurrency == "optimistic"} census'ta "concurrency". concurrency/ext @Nullable.
 */
public record EntityJson(
        String id,
        String module,
        List<String> realizes,
        List<EntityFieldJson> fields,
        List<GuardedExpr> invariants,
        /* @Nullable */ String concurrency,
        /* @Nullable */ List<ExtJson> ext) {

    public EntityJson {
        realizes = Objects.requireNonNullElse(realizes, List.of());
        fields = Objects.requireNonNullElse(fields, List.of());
        invariants = Objects.requireNonNullElse(invariants, List.of());
    }
}
