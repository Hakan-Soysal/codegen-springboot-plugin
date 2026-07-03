package techgen.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import techgen.core.model.ExprNode;
import techgen.core.model.ExprNodeDeserializer;
import techgen.core.model.ExprNodeSerializer;

/**
 * Merkezî JSON yapılandırması (davranış sözleşmesi §4, CoreTemplate1 Json.cs karşılığı):
 * camelCase (Jackson default), case-insensitive, bilinmeyen alan sessiz, ExprNode
 * (de)serializer'ları modül olarak kayıtlı.
 *
 * <p>Not: {@link ExprNode} tip-seviyesinde {@code @JsonDeserialize}/{@code @JsonSerialize}
 * anotasyonları da taşır; modül kaydı aynı sınıfları kullanır — iki mekanizma aynı davranışı
 * verir, çakışma yoktur (testle doğrulanır).</p>
 */
public final class Json {

    private static final ObjectMapper MAPPER = build();

    private Json() {
    }

    /** Merkezî, paylaşılan ObjectMapper. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    private static ObjectMapper build() {
        SimpleModule exprModule = new SimpleModule("techgen-exprnode");
        exprModule.addSerializer(ExprNode.class, new ExprNodeSerializer());
        exprModule.addDeserializer(ExprNode.class, new ExprNodeDeserializer());
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .addModule(exprModule)
                .build();
    }

    /**
     * JSON parse; null deserialize yasak — {@code "null"} girdisi exception fırlatır
     * (CoreTemplate1 {@code Json.Parse<T>} paritesi).
     */
    public static <T> T parse(String json, Class<T> type) throws JsonProcessingException {
        T value = MAPPER.readValue(json, type);
        if (value == null) {
            throw new JsonMappingException(null, "null deserialize: " + type.getSimpleName());
        }
        return value;
    }

    /** JSON yaz (merkezî ayarlarla; indent yok). */
    public static <T> String write(T value) throws JsonProcessingException {
        return MAPPER.writeValueAsString(value);
    }
}
