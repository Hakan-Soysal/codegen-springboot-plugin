package techgen.core.model;

import java.util.List;

/** Manifest.cs:29. ext @Nullable. */
public record ParamJson(
        String name,
        String type,
        boolean collection,
        /* @Nullable */ List<ExtJson> ext) {
}
