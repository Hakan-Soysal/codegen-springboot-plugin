package techgen.core.model;

import java.util.List;

/** Manifest.cs:27 — anahtar Name. ext @Nullable. */
public record ModuleDecl(
        String name,
        boolean pureTechnical,
        /* @Nullable */ List<ExtJson> ext) {
}
