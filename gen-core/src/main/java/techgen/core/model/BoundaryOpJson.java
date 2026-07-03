package techgen.core.model;

import java.util.List;

/** Manifest.cs:69. serving/validation @Nullable (sözleşmede nullable — normalize edilmez). */
public record BoundaryOpJson(
        String id,
        SignatureJson signature,
        /* @Nullable */ List<ServingJson> serving,
        /* @Nullable */ List<GuardedExpr> validation) {
}
