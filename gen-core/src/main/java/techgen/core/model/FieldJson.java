package techgen.core.model;

import java.util.List;

/** Manifest.cs:59. ext @Nullable. */
public record FieldJson(
        String name,
        String type,
        boolean collection,
        /* @Nullable */ List<ExtJson> ext) {
}
