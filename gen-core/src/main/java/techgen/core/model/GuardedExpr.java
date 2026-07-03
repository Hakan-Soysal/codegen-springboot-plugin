package techgen.core.model;

/** Manifest.cs:34 — validation/rule/invariant elemanı. guardRef @Nullable. */
public record GuardedExpr(
        String text,
        ExprNode ast,
        /* @Nullable */ String guardRef) {
}
