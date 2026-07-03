package techgen.core.predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import techgen.core.errors.UnsupportedConstruct;
import techgen.core.model.AggNode;
import techgen.core.model.BinaryNode;
import techgen.core.model.CallNode;
import techgen.core.model.DurationNode;
import techgen.core.model.ExprNode;
import techgen.core.model.LiteralNode;
import techgen.core.model.PathNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T2.1 — ExprWalk (dil-nötr yürüyüş) testleri (davranış sözleşmesi §3).
 *
 * <p>Test-lokal {@link StringRender} bir örnek "render katmanı" gibi davranır — gerçek Java
 * render'ı (BigDecimal/equals/karşılaştırma) T3.5'in işi; burada yalnız ExprWalk'un dil-nötr
 * yürüyüşü ve callback sözleşmesi (her zaman parantezli, distinct-path, tip-baskınlığı)
 * doğrulanır.</p>
 */
class ExprWalkTest {

    /** Basit test-Render: and/or→AND/OR, cmp/arith→ham op sembolü; her binary parantezli. */
    private static final class StringRender implements ExprWalk.Render<String> {
        @Override
        public String binary(String nodeKind, String op, String left, String right) {
            String symbol = switch (nodeKind) {
                case "and" -> "AND";
                case "or" -> "OR";
                default -> op;
            };
            return "(" + left + " " + symbol + " " + right + ")";
        }

        @Override
        public String path(List<String> path, String propName) {
            return "input." + propName;
        }

        @Override
        public String literal(String litKind, Object value, boolean decimalContext) {
            return switch (litKind) {
                case "string" -> "\"" + value + "\"";
                case "boolean" -> value.toString();
                default -> (decimalContext ? "D:" : "") + value;
            };
        }
    }

    private static Path fixture(String name) {
        Path fromModuleDir = Path.of("../fixtures", name);
        if (Files.exists(fromModuleDir)) {
            return fromModuleDir;
        }
        return Path.of("fixtures", name);
    }

    // --- Negatif: AggNode/CallNode/DurationNode → UnsupportedConstruct -----------------------

    @Test
    void walk_aggNode_throwsUnsupportedConstruct() {
        ExprNode agg = new AggNode("sum", List.of("items", "amount"));
        assertThrows(UnsupportedConstruct.class, () -> ExprWalk.walk(agg, new StringRender()));
    }

    @Test
    void walk_callNode_throwsUnsupportedConstruct() {
        ExprNode call = new CallNode("now", List.of());
        assertThrows(UnsupportedConstruct.class, () -> ExprWalk.walk(call, new StringRender()));
    }

    @Test
    void walk_durationNode_throwsUnsupportedConstruct() {
        ExprNode duration = new DurationNode(30.0, "days", "30 days");
        assertThrows(UnsupportedConstruct.class, () -> ExprWalk.walk(duration, new StringRender()));
    }

    @Test
    void walk_nestedAggInsideBinary_stillThrowsUnsupportedConstruct() {
        ExprNode tree = new BinaryNode("and", null,
                new BinaryNode("cmp", ">", new PathNode(List.of("amount")), new LiteralNode("number", 0.0)),
                new AggNode("sum", List.of("items", "amount")));
        assertThrows(UnsupportedConstruct.class, () -> ExprWalk.walk(tree, new StringRender()));
    }

    // --- Negatif: op'suz cmp/arith → UnsupportedConstruct -------------------------------------

    @Test
    void walk_cmpWithoutOp_throwsUnsupportedConstruct() {
        ExprNode cmp = new BinaryNode("cmp", null, new PathNode(List.of("amount")), new LiteralNode("number", 0.0));
        assertThrows(UnsupportedConstruct.class, () -> ExprWalk.walk(cmp, new StringRender()));
    }

    @Test
    void walk_arithWithoutOp_throwsUnsupportedConstruct() {
        ExprNode add = new BinaryNode("add", null, new PathNode(List.of("amount")), new LiteralNode("number", 1.0));
        assertThrows(UnsupportedConstruct.class, () -> ExprWalk.walk(add, new StringRender()));
    }

    @Test
    void walk_andWithoutOp_doesNotThrow_opNotRequiredForBoolConnectives() {
        ExprNode and = new BinaryNode("and", null,
                new PathNode(List.of("a")), new PathNode(List.of("b")));
        // and/or hiçbir zaman op taşımaz (sözleşme) — op==null burada beklenen, hata DEĞİL.
        ExprWalk.Result<String> result = ExprWalk.walk(and, new StringRender());
        assertEquals("(input.a AND input.b)", result.expr());
    }

    // --- Pozitif: her zaman parantezli, nested and/or ------------------------------------------

    @Test
    void walk_nestedAndOr_alwaysParenthesized() {
        ExprNode tree = new BinaryNode("and", null,
                new BinaryNode("cmp", ">", new PathNode(List.of("amount")), new LiteralNode("number", 0.0)),
                new BinaryNode("or",
                        null,
                        new BinaryNode("cmp", "<=", new PathNode(List.of("amount")), new PathNode(List.of("resource", "creditLimit"))),
                        new LiteralNode("boolean", true)));

        ExprWalk.Result<String> result = ExprWalk.walk(tree, new StringRender());
        assertEquals("((input.amount > 0.0) AND ((input.amount <= input.resourceCreditLimit) OR true))", result.expr());
    }

    // --- Pozitif: distinct path (görülme sırasıyla, bir kez) -----------------------------------

    @Test
    void walk_distinctPath_collectedOnceInFirstSeenOrder() {
        ExprNode tree = new BinaryNode("and", null,
                new BinaryNode("cmp", ">", new PathNode(List.of("resource", "creditLimit")), new LiteralNode("number", 0.0)),
                new BinaryNode("cmp", "<=", new PathNode(List.of("amount")), new PathNode(List.of("resource", "creditLimit"))));

        ExprWalk.Result<String> result = ExprWalk.walk(tree, new StringRender());
        assertEquals(List.of(List.of("resource", "creditLimit"), List.of("amount")), result.paths(),
                "resource.creditLimit iki kez görülür ama bir kez toplanır; amount ikinci distinct path");
    }

    @Test
    void walk_fixtureRuleAst_amountLteResourceCreditLimit_endToEnd() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(fixture("manifest.json").toFile());
        JsonNode ruleAst = root.get("operations").get(0).get("rule").get(0).get("ast");
        // ExprNode custom (de)serializer'ı techgen.core.Json'a kayıtlı; burada techgen.core.model
        // paketindeki test-lokal mapper yerine gerçek modülü taşıyan mapper'ı kullanıyoruz.
        ExprNode ast = techgen.core.Json.mapper().treeToValue(ruleAst, ExprNode.class);

        ExprWalk.Result<String> result = ExprWalk.walk(ast, new StringRender());
        assertEquals("(input.amount <= input.resourceCreditLimit)", result.expr());
        assertEquals(List.of(List.of("amount"), List.of("resource", "creditLimit")), result.paths());
    }

    // --- typeOf: tip-baskınlığı (Decimal > Double > Int) ----------------------------------------

    @Test
    void typeOf_decimalDominatesDoubleAndInt() {
        java.util.function.Function<List<String>, String> resolveType = path ->
                path.equals(List.of("amount")) ? "Decimal" : path.equals(List.of("count")) ? "Int" : null;

        ExprNode addTree = new BinaryNode("add", "+", new PathNode(List.of("amount")), new PathNode(List.of("count")));
        assertEquals("Decimal", ExprWalk.typeOf(addTree, resolveType), "Decimal + Int → Decimal (baskın)");
    }

    @Test
    void typeOf_integerValuedDoubleLiteral_isInt() {
        assertEquals("Int", ExprWalk.typeOf(new LiteralNode("number", 5.0), null));
        assertEquals("Double", ExprWalk.typeOf(new LiteralNode("number", 5.5), null));
    }

    @Test
    void walk_decimalContextPropagatesToLiteralCallback() {
        java.util.function.Function<List<String>, String> resolveType = path -> "Decimal";
        ExprNode cmp = new BinaryNode("cmp", ">", new PathNode(List.of("amount")), new LiteralNode("number", 0.0));

        ExprWalk.Result<String> result = ExprWalk.walk(cmp, new StringRender(), resolveType);
        assertEquals("(input.amount > D:0.0)", result.expr(), "resolveType Decimal döndürünce literal decimalContext=true render edilmeli");
    }

    // --- inferLiteralTypes: karşılaştırma bağlamından path tip-ipucu ----------------------------

    @Test
    void inferLiteralTypes_stringComparison_hintsString() {
        ExprNode cmp = new BinaryNode("cmp", "=", new PathNode(List.of("status")), new LiteralNode("string", "planli"));
        Map<String, String> hints = ExprWalk.inferLiteralTypes(cmp);
        assertEquals("String", hints.get("status"));
    }

    @Test
    void inferLiteralTypes_numberComparison_intVsDecimal() {
        ExprNode intCmp = new BinaryNode("cmp", ">", new PathNode(List.of("count")), new LiteralNode("number", 5.0));
        ExprNode decCmp = new BinaryNode("cmp", ">", new PathNode(List.of("amount")), new LiteralNode("number", 5.5));

        assertEquals("Int", ExprWalk.inferLiteralTypes(intCmp).get("count"));
        assertEquals("Decimal", ExprWalk.inferLiteralTypes(decCmp).get("amount"));
    }

    @Test
    void inferLiteralTypes_firstHintWins_onConflictingComparisons() {
        ExprNode tree = new BinaryNode("and", null,
                new BinaryNode("cmp", "=", new PathNode(List.of("status")), new LiteralNode("string", "planli")),
                new BinaryNode("cmp", "=", new PathNode(List.of("status")), new LiteralNode("boolean", true)));
        Map<String, String> hints = ExprWalk.inferLiteralTypes(tree);
        assertEquals("String", hints.get("status"), "ilk eşleşme kazanır (String), ikinci Bool YOK SAYILIR");
    }

    // --- propName: collision-safe camel-join -----------------------------------------------------

    @Test
    void propName_camelJoinsPathSegments() {
        assertEquals("resourceCreditLimit", ExprWalk.propName(List.of("resource", "creditLimit")));
        assertEquals("amount", ExprWalk.propName(List.of("amount")));
        assertEquals("actorTenant", ExprWalk.propName(List.of("actor", "tenant")));
    }
}
