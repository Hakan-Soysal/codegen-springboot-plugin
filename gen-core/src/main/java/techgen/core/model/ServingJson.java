package techgen.core.model;

import java.util.List;
import java.util.Objects;

/** Manifest.cs:32 — census'ta {owner}:{protocol} ile anahtarlanır. */
public record ServingJson(
        String protocol,
        List<ServingArg> args,
        String raw) {

    public ServingJson {
        args = Objects.requireNonNullElse(args, List.of());
    }
}
