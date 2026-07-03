package techgen.conformance;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * SPEC şemasının {@code act} bölümü (davranış sözleşmesi §A.1; referans
 * {@code conformance-adapter/Spec.cs} {@code SpecAct}): {@code call} — op çağrı anahtarı
 * (bilgi amaçlı; asıl resolve {@link Spec#opId()} ile yapılır); {@code with} — dil-nötr
 * (camelCase) istek payload'ı.
 */
public record SpecAct(String call, JsonNode with) {
}
