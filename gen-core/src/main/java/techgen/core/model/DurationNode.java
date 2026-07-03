package techgen.core.model;

/**
 * T1.1'den taşındı (bkz. {@link ExprNode} javadoc) — public, {@code techgen.core.predicate}
 * paketinden pattern-match edilebilsin diye.
 */
public record DurationNode(double value, String unit, String text) implements ExprNode {}
