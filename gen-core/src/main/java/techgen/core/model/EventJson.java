package techgen.core.model;

import java.util.List;
import java.util.Objects;

/** Manifest.cs:63. */
public record EventJson(
        String id,
        String module,
        List<FieldJson> payload) {

    public EventJson {
        payload = Objects.requireNonNullElse(payload, List.of());
    }
}
