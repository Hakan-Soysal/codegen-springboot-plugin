package techgen.core.model;

import java.util.List;
import java.util.Objects;

/** Manifest.cs:70 — anahtar Name. */
public record ExternalJson(
        String name,
        boolean generated,
        List<BoundaryOpJson> operations) {

    public ExternalJson {
        operations = Objects.requireNonNullElse(operations, List.of());
    }
}
