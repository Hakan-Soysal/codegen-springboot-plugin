package techgen.core.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;

/**
 * Canonical Expr AST. Discriminated union, JSON'da ayrımcı üç alan: {@code node}
 * (binary/agg/call), {@code path} (PathNode), {@code kind} (literal/duration).
 * Ayrım alan-VARLIĞINA göredir; standart polimorfizm anotasyonları ({@code @JsonTypeInfo}/
 * {@code @JsonSubTypes}) bunu ifade edemez — bkz. {@link ExprNodeDeserializer}.
 */
@JsonDeserialize(using = ExprNodeDeserializer.class)
@JsonSerialize(using = ExprNodeSerializer.class)
public sealed interface ExprNode permits BinaryNode, AggNode, CallNode, PathNode, LiteralNode, DurationNode {}

/** node ∈ and|or|cmp|add|sub|mul|div. op yalnız cmp/arith'te dolu (nullable). */
record BinaryNode(String nodeKind, String op, ExprNode left, ExprNode right) implements ExprNode {}

/** aggregate. */
record AggNode(String fn, List<String> path) implements ExprNode {}

record CallNode(String name, List<ExprNode> args) implements ExprNode {}

record PathNode(List<String> path) implements ExprNode {}

/** kind ∈ string|number|boolean. value: String|Double|Boolean. */
record LiteralNode(String litKind, Object value) implements ExprNode {}

record DurationNode(double value, String unit, String text) implements ExprNode {}
