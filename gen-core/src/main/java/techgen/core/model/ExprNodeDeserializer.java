package techgen.core.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ExprNode ayrımcı sırası (docs/referans/gen-core-davranis-sozlesmesi.md §3):
 * 1. {@code node} alanı varsa: "agg"→AggNode, "call"→CallNode, default→BinaryNode
 *    (bilinmeyen NodeKind TOLERANSLI — patlamaz).
 * 2. yoksa {@code path} alanı varsa → PathNode.
 * 3. yoksa {@code kind} alanı varsa: "duration"→DurationNode; string/number/boolean→LiteralNode;
 *    başka kind → JsonMappingException (FATAL — asimetri kasıtlı).
 * 4. hiçbiri → JsonMappingException("unknown ExprNode shape").
 */
public final class ExprNodeDeserializer extends JsonDeserializer<ExprNode> {

    @Override
    public ExprNode deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectCodec codec = p.getCodec();
        JsonNode node = codec.readTree(p);
        return fromNode(node, p);
    }

    static ExprNode fromNode(JsonNode el, JsonParser p) throws IOException {
        if (el.has("node")) {
            String nodeKind = el.get("node").asText();
            switch (nodeKind) {
                case "agg" -> {
                    String fn = el.get("fn").asText();
                    List<String> path = readStrings(el.get("path"));
                    return new AggNode(fn, path);
                }
                case "call" -> {
                    String name = el.get("name").asText();
                    List<ExprNode> args = new ArrayList<>();
                    for (JsonNode a : el.get("args")) {
                        args.add(fromNode(a, p));
                    }
                    return new CallNode(name, args);
                }
                default -> {
                    String op = (el.has("op") && !el.get("op").isNull()) ? el.get("op").asText() : null;
                    ExprNode left = fromNode(el.get("left"), p);
                    ExprNode right = fromNode(el.get("right"), p);
                    return new BinaryNode(nodeKind, op, left, right);
                }
            }
        }
        if (el.has("path")) {
            return new PathNode(readStrings(el.get("path")));
        }
        if (el.has("kind")) {
            String kind = el.get("kind").asText();
            if (kind.equals("duration")) {
                double value = el.get("value").asDouble();
                String unit = el.get("unit").asText();
                String text = el.get("text").asText();
                return new DurationNode(value, unit, text);
            }
            JsonNode v = el.get("value");
            Object val = switch (kind) {
                case "string" -> v.asText();
                case "number" -> v.asDouble();
                case "boolean" -> v.asBoolean();
                default -> throw JsonMappingException.from(p, "unknown literal kind: " + kind);
            };
            return new LiteralNode(kind, val);
        }
        throw JsonMappingException.from(p, "unknown ExprNode shape");
    }

    private static List<String> readStrings(JsonNode arr) {
        List<String> list = new ArrayList<>();
        for (JsonNode e : arr) {
            list.add(e.asText());
        }
        return list;
    }
}
