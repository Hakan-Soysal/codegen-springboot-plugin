package techgen.core.model;

import java.util.List;
import java.util.Objects;

/** Manifest.cs:78. */
public record Coverage(
        List<String> unrealizedBusinessOps,
        List<String> uncoveredEntities) {

    public Coverage {
        unrealizedBusinessOps = Objects.requireNonNullElse(unrealizedBusinessOps, List.of());
        uncoveredEntities = Objects.requireNonNullElse(uncoveredEntities, List.of());
    }
}
