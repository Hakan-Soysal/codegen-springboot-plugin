package techgen.core.model;

import java.util.List;

/**
 * aggregate. T1.1'den taşındı (bkz. {@link ExprNode} javadoc) — public, {@code techgen.core.predicate}
 * paketinden pattern-match edilebilsin diye.
 */
public record AggNode(String fn, List<String> path) implements ExprNode {}
