package techgen.core.model;

import java.util.List;
import java.util.Objects;

/** Manifest.cs:39. size @Nullable. */
public record Pagination(
        String strategy,
        List<PaginationKey> keys,
        /* @Nullable */ Integer size) {

    public Pagination {
        keys = Objects.requireNonNullElse(keys, List.of());
    }
}
