package techgen.core.model;

/**
 * Manifest.cs:68 — sıralama (from, to.system, to.op). Census: her edge → ("calls", from);
 * compensate != null → ("compensate", from). compensate @Nullable.
 */
public record CallEdgeJson(
        String from,
        CallTarget to,
        String kind,
        /* @Nullable */ CallTarget compensate) {
}
