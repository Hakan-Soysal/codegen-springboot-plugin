package techgen.core.model;

import java.util.List;
import java.util.Objects;

/** Manifest.cs:26 — anahtar Name. ext @Nullable. */
public record Deployable(
        String name,
        List<String> units,
        /* @Nullable */ List<ExtJson> ext) {

    public Deployable {
        units = Objects.requireNonNullElse(units, List.of());
    }
}
