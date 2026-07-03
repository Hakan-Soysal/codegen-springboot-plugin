package techgen.core.model;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T1.1 — ExprNode AST + polymorphic (de)serializer testleri.
 * Test-lokal ObjectMapper kullanılır (global techgen.core.Json yapılandırması T1.2 kapsamındadır).
 */
class ExprNodeTest {

    private static ObjectMapper mapper() {
        return new ObjectMapper();
    }

    private static Path fixturesManifestPath() {
        Path fromModuleDir = Path.of("../fixtures/manifest.json");
        if (Files.exists(fromModuleDir)) {
            return fromModuleDir;
        }
        return Path.of("fixtures/manifest.json");
    }

    @Test
    void parsesCmpPathNumber_directJson() throws IOException {
        String json = """
                {"node":"cmp","op":">","left":{"path":["amount"]},"right":{"kind":"number","value":0}}
                """;
        ExprNode result = mapper().readValue(json, ExprNode.class);
        ExprNode expected = new BinaryNode("cmp", ">",
                new PathNode(List.of("amount")),
                new LiteralNode("number", 0.0));
        assertEquals(expected, result);
    }

    @Test
    void parsesFixtureAst_operations0Validation0_matchesSpecExample() throws IOException {
        // Acceptance 6.2: fixtures/manifest.json -> operations[0].validation[0].ast
        ObjectMapper mapper = mapper();
        JsonNode root = mapper.readTree(fixturesManifestPath().toFile());
        JsonNode astNode = root.get("operations").get(0).get("validation").get(0).get("ast");
        ExprNode result = mapper.treeToValue(astNode, ExprNode.class);
        ExprNode expected = new BinaryNode("cmp", ">",
                new PathNode(List.of("amount")),
                new LiteralNode("number", 0.0));
        assertEquals(expected, result);
    }

    @Test
    void parsesNestedAndOr() throws IOException {
        String json = """
                {
                  "node": "and",
                  "left": {"node":"cmp","op":">","left":{"path":["amount"]},"right":{"kind":"number","value":0}},
                  "right": {
                    "node": "or",
                    "left": {"node":"cmp","op":"<=","left":{"path":["amount"]},"right":{"path":["resource","creditLimit"]}},
                    "right": {"kind":"boolean","value":true}
                  }
                }
                """;
        ExprNode result = mapper().readValue(json, ExprNode.class);
        ExprNode expectedLeft = new BinaryNode("cmp", ">", new PathNode(List.of("amount")), new LiteralNode("number", 0.0));
        ExprNode expectedRight = new BinaryNode("or",
                null,
                new BinaryNode("cmp", "<=", new PathNode(List.of("amount")), new PathNode(List.of("resource", "creditLimit"))),
                new LiteralNode("boolean", true));
        ExprNode expected = new BinaryNode("and", null, expectedLeft, expectedRight);
        assertEquals(expected, result);
    }

    @Test
    void parsesAggNode() throws IOException {
        String json = """
                {"node":"agg","fn":"sum","path":["items","amount"]}
                """;
        ExprNode result = mapper().readValue(json, ExprNode.class);
        assertEquals(new AggNode("sum", List.of("items", "amount")), result);
    }

    @Test
    void parsesCallNode() throws IOException {
        String json = """
                {"node":"call","name":"now","args":[{"path":["createdAt"]}]}
                """;
        ExprNode result = mapper().readValue(json, ExprNode.class);
        assertEquals(new CallNode("now", List.of(new PathNode(List.of("createdAt")))), result);
    }

    @Test
    void parsesDurationNode() throws IOException {
        String json = """
                {"kind":"duration","value":30.0,"unit":"days","text":"30 days"}
                """;
        ExprNode result = mapper().readValue(json, ExprNode.class);
        assertEquals(new DurationNode(30.0, "days", "30 days"), result);
    }

    @Test
    void roundTripIsLosslessUnderRecordEquality() throws IOException {
        ObjectMapper mapper = mapper();
        ExprNode original = new BinaryNode("and", null,
                new BinaryNode("cmp", ">", new PathNode(List.of("amount")), new LiteralNode("number", 0.0)),
                new CallNode("now", List.of(
                        new AggNode("sum", List.of("items", "amount")),
                        new DurationNode(30.0, "days", "30 days"),
                        new LiteralNode("string", "planli"),
                        new LiteralNode("boolean", Boolean.TRUE))));

        String serialized = mapper.writeValueAsString(original);
        ExprNode reparsed = mapper.readValue(serialized, ExprNode.class);

        assertEquals(original, reparsed, "round-trip parse->serialize->parse must be record-equal");
    }

    @Test
    void unknownLiteralKindThrowsException() {
        String json = """
                {"kind":"weird","value":1}
                """;
        assertThrows(JsonMappingException.class, () -> mapper().readValue(json, ExprNode.class));
    }

    @Test
    void unknownShapeThrowsException() {
        String json = """
                {"foo":1}
                """;
        assertThrows(JsonMappingException.class, () -> mapper().readValue(json, ExprNode.class));
    }

    @Test
    void unknownNodeKindIsTolerated_producesBinaryNode() throws IOException {
        String json = """
                {"node":"xor","left":{"path":["a"]},"right":{"path":["b"]}}
                """;
        ExprNode result = mapper().readValue(json, ExprNode.class);
        ExprNode expected = new BinaryNode("xor", null, new PathNode(List.of("a")), new PathNode(List.of("b")));
        assertEquals(expected, result);
        assertTrue(result instanceof BinaryNode);
    }
}
