package techgen.core.model;

/**
 * node ∈ and|or|cmp|add|sub|mul|div. op yalnız cmp/arith'te dolu (nullable).
 * T1.1'den taşındı (bkz. {@link ExprNode} javadoc) — public, {@code techgen.core.predicate}
 * paketinden pattern-match edilebilsin diye.
 */
public record BinaryNode(String nodeKind, String op, ExprNode left, ExprNode right) implements ExprNode {}
