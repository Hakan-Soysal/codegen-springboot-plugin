package techgen.spring;

import org.junit.jupiter.api.Test;

import techgen.core.errors.UnsupportedConstruct;
import techgen.core.model.AggNode;
import techgen.core.model.BinaryNode;
import techgen.core.model.ExprNode;
import techgen.core.model.LiteralNode;
import techgen.core.model.PathNode;
import techgen.core.predicate.ExprWalk;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T3.5 §5.4 — {@link JavaPredicateRenderer} birim testleri (SPEC §6.5; INV-4'ün Java'ya özgü kritik
 * kısmı). Üç render formu (BigDecimal compareTo · String equals · primitif doğal operatör) birebir
 * string-assert'li; iki Unsupported yolu; distinct path + parantezleme.
 */
class PredicateRenderTest {

    private static PathNode path(String... segs) {
        return new PathNode(List.of(segs));
    }

    private static LiteralNode num(double v) {
        return new LiteralNode("number", v);
    }

    private static LiteralNode str(String v) {
        return new LiteralNode("string", v);
    }

    private static BinaryNode cmp(String op, ExprNode l, ExprNode r) {
        return new BinaryNode("cmp", op, l, r);
    }

    /** resolveType stub: kilitli bir tip haritası. */
    private static Function<List<String>, String> types(Map<List<String>, String> m) {
        return m::get;
    }

    // ── compareTo formu: literal karşılaştırması (Decimal path vs sayı literal) ──────────────

    @Test
    void decimalPathVsNumberLiteral_rendersCompareToWithBigDecimalStringCtor() {
        ExprNode ast = cmp(">", path("amount"), num(0));
        ExprWalk.Result<String> res = JavaPredicateRenderer.render(ast, types(Map.of(List.of("amount"), "Decimal")));

        assertEquals("(input.amount().compareTo(new BigDecimal(\"0\")) > 0)", res.expr());
        assertEquals(List.of(List.of("amount")), res.paths());
        // alan tipi BigDecimal (compareTo ile tutarlı — derlenebilirlik)
        assertEquals("BigDecimal",
                JavaPredicateRenderer.javaFieldType(List.of("amount"), types(Map.of(List.of("amount"), "Decimal")),
                        Map.of()));
    }

    // ── compareTo formu: path-path karşılaştırması (iki Decimal) + collision-safe camel-join ──

    @Test
    void twoDecimalPaths_rendersCompareTo_andInputFieldsAreBigDecimal() {
        ExprNode ast = cmp("<=", path("amount"), path("resource", "creditLimit"));
        Function<List<String>, String> rt = types(Map.of(
                List.of("amount"), "Decimal",
                List.of("resource", "creditLimit"), "Decimal"));
        ExprWalk.Result<String> res = JavaPredicateRenderer.render(ast, rt);

        assertEquals("(input.amount().compareTo(input.resourceCreditLimit()) <= 0)", res.expr());
        assertEquals(List.of(List.of("amount"), List.of("resource", "creditLimit")), res.paths());
        assertEquals("BigDecimal", JavaPredicateRenderer.javaFieldType(List.of("amount"), rt, Map.of()));
        assertEquals("BigDecimal",
                JavaPredicateRenderer.javaFieldType(List.of("resource", "creditLimit"), rt, Map.of()));
    }

    // ── primitif: Int yolu compareTo'ya SAPMAZ, doğal operatör ────────────────────────────────

    @Test
    void intPath_usesNaturalOperator_notCompareTo() {
        ExprNode ast = cmp(">", path("seq"), num(0));
        Function<List<String>, String> rt = types(Map.of(List.of("seq"), "Int"));
        ExprWalk.Result<String> res = JavaPredicateRenderer.render(ast, rt);

        assertEquals("(input.seq() > 0)", res.expr());
        assertEquals("int", JavaPredicateRenderer.javaFieldType(List.of("seq"), rt, Map.of()));
    }

    // ── String eşitliği: equals (== referans karşılaştırması DEĞİL) ───────────────────────────

    @Test
    void stringEquality_rendersEquals_viaLiteralHint() {
        // resolveType null → InferLiteralTypes: status ↔ "planli" → String
        ExprNode ast = cmp("=", path("status"), str("planli"));
        ExprWalk.Result<String> res = JavaPredicateRenderer.render(ast, null);

        assertEquals("(input.status().equals(\"planli\"))", res.expr());
        Map<String, String> hints = ExprWalk.inferLiteralTypes(ast);
        assertEquals("String", JavaPredicateRenderer.javaFieldType(List.of("status"), null, hints));
    }

    @Test
    void stringInequality_rendersNegatedEquals() {
        ExprNode ast = cmp("!=", path("status"), str("planli"));
        ExprWalk.Result<String> res = JavaPredicateRenderer.render(ast, null);
        assertEquals("(!input.status().equals(\"planli\"))", res.expr());
    }

    // ── and/or parantezleme + distinct path bir kez ───────────────────────────────────────────

    @Test
    void andOr_parenthesized_andDistinctPathSeenOnce() {
        // (amount > 0) and (amount <= resource.creditLimit)
        ExprNode left = cmp(">", path("amount"), num(0));
        ExprNode right = cmp("<=", path("amount"), path("resource", "creditLimit"));
        ExprNode ast = new BinaryNode("and", null, left, right);
        Function<List<String>, String> rt = types(Map.of(
                List.of("amount"), "Decimal",
                List.of("resource", "creditLimit"), "Decimal"));
        ExprWalk.Result<String> res = JavaPredicateRenderer.render(ast, rt);

        assertEquals(
                "((input.amount().compareTo(new BigDecimal(\"0\")) > 0)"
                        + " && (input.amount().compareTo(input.resourceCreditLimit()) <= 0))",
                res.expr());
        // amount iki kez geçse de distinct path listesinde bir kez
        assertEquals(List.of(List.of("amount"), List.of("resource", "creditLimit")), res.paths());
    }

    // ── Decimal literal String-ctor: ondalıklı değer birebir korunur ─────────────────────────

    @Test
    void decimalLiteral_usesStringCtor_preservingDecimalText() {
        ExprNode ast = cmp(">", path("amount"), num(0.5));
        ExprWalk.Result<String> res = JavaPredicateRenderer.render(ast, types(Map.of(List.of("amount"), "Decimal")));
        assertEquals("(input.amount().compareTo(new BigDecimal(\"0.5\")) > 0)", res.expr());
    }

    // ── Negatif: agg/call/duration + op'suz cmp → UnsupportedConstruct ────────────────────────

    @Test
    void aggNode_throwsUnsupportedConstruct() {
        ExprNode ast = cmp(">", new AggNode("count", List.of("items")), num(0));
        assertThrows(UnsupportedConstruct.class, () -> JavaPredicateRenderer.render(ast, null));
    }

    @Test
    void cmpWithoutOperator_throwsUnsupportedConstruct() {
        // nodeKind cmp iken op == null → op'suz karşılaştırma (ExprWalk INV-7 yolu)
        ExprNode ast = new BinaryNode("cmp", null, path("amount"), num(0));
        assertThrows(UnsupportedConstruct.class, () -> JavaPredicateRenderer.render(ast, null));
    }
}
