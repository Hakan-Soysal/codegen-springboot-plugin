package techgen.core.model;

/**
 * kind ∈ string|number|boolean. value: String|Double|Boolean.
 * T1.1'den taşındı (bkz. {@link ExprNode} javadoc) — public, {@code techgen.core.predicate}
 * paketinden pattern-match edilebilsin diye.
 */
public record LiteralNode(String litKind, Object value) implements ExprNode {}
