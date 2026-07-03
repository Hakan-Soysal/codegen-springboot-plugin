package techgen.core.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * ExprNodeDeserializer'ın okuduğu şekle birebir (round-trip simetrik) geri yazar:
 * Binary→{node,op?,left,right}, Agg→{node:"agg",fn,path}, Call→{node:"call",name,args},
 * Path→{path}, Literal→{kind,value}, Duration→{kind:"duration",value,unit,text}.
 * {@code op} null ise alan yazılmaz.
 */
public final class ExprNodeSerializer extends JsonSerializer<ExprNode> {

    @Override
    public void serialize(ExprNode value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        writeFields(value, gen, serializers);
        gen.writeEndObject();
    }

    private void writeFields(ExprNode value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        switch (value) {
            case BinaryNode b -> {
                gen.writeStringField("node", b.nodeKind());
                if (b.op() != null) {
                    gen.writeStringField("op", b.op());
                }
                gen.writeFieldName("left");
                serialize(b.left(), gen, serializers);
                gen.writeFieldName("right");
                serialize(b.right(), gen, serializers);
            }
            case AggNode a -> {
                gen.writeStringField("node", "agg");
                gen.writeStringField("fn", a.fn());
                gen.writeFieldName("path");
                writeStrings(a.path(), gen);
            }
            case CallNode c -> {
                gen.writeStringField("node", "call");
                gen.writeStringField("name", c.name());
                gen.writeFieldName("args");
                gen.writeStartArray();
                for (ExprNode arg : c.args()) {
                    serialize(arg, gen, serializers);
                }
                gen.writeEndArray();
            }
            case PathNode pn -> {
                gen.writeFieldName("path");
                writeStrings(pn.path(), gen);
            }
            case LiteralNode l -> {
                gen.writeStringField("kind", l.litKind());
                gen.writeFieldName("value");
                writeLiteralValue(l.value(), gen);
            }
            case DurationNode d -> {
                gen.writeStringField("kind", "duration");
                gen.writeNumberField("value", d.value());
                gen.writeStringField("unit", d.unit());
                gen.writeStringField("text", d.text());
            }
        }
    }

    private void writeLiteralValue(Object value, JsonGenerator gen) throws IOException {
        switch (value) {
            case String s -> gen.writeString(s);
            case Boolean bo -> gen.writeBoolean(bo);
            case Double d -> gen.writeNumber(d);
            default -> gen.writeObject(value);
        }
    }

    private void writeStrings(Iterable<String> items, JsonGenerator gen) throws IOException {
        gen.writeStartArray();
        for (String s : items) {
            gen.writeString(s);
        }
        gen.writeEndArray();
    }
}
