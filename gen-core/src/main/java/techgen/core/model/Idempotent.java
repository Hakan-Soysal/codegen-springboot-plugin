package techgen.core.model;

import java.util.List;
import java.util.Objects;

/** Manifest.cs:37. */
public record Idempotent(List<String> keys) {

    public Idempotent {
        keys = Objects.requireNonNullElse(keys, List.of());
    }
}
