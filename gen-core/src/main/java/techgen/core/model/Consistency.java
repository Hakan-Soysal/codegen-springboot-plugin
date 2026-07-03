package techgen.core.model;

/**
 * Manifest.cs:35. mode @Nullable — census'ta {@code mode != null || risk == "eventual"} ise
 * sayılır (Completeness.cs:84).
 */
public record Consistency(
        String risk,
        /* @Nullable */ String mode) {
}
