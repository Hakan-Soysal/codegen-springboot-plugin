package techgen.conformance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Merkezî SPEC JSON yapılandırması (davranış sözleşmesi §A.1; referans {@code Spec.cs:36-41}
 * {@code SpecJson}, gen-core {@code techgen.core.Json} karşılığı): case-insensitive, yorum +
 * trailing-comma toleranslı, bilinmeyen alan sessiz. Her dosya TAM BİR {@link Spec}'tir —
 * array parser YOK (§5.2).
 */
public final class SpecJson {

    private static final ObjectMapper MAPPER = build();

    private SpecJson() {
    }

    private static ObjectMapper build() {
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .build();
    }

    /** Paylaşılan ObjectMapper (GeneratedApp'in Jackson dönüşüm fallback'i için de kullanılır). */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** Bir JSON metnini TAM BİR {@link Spec} olarak ayrıştırır. */
    public static Spec parse(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, Spec.class);
    }

    /** Bir SPEC dosyasını TAM BİR {@link Spec} olarak ayrıştırır. */
    public static Spec parse(Path file) throws IOException {
        return MAPPER.readValue(Files.readString(file), Spec.class);
    }
}
