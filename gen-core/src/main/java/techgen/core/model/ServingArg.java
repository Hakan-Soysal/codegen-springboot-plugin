package techgen.core.model;

import java.util.List;

/** Manifest.cs:31. params @Nullable. */
public record ServingArg(
        String kind,
        String value,
        /* @Nullable */ List<String> params) {
}
