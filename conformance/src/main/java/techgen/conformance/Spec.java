package techgen.conformance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Dil-nötr conformance SPEC (davranış sözleşmesi §A.1; referans {@code Spec.cs:9-14}; aile
 * (T3.3) sahibi — bu modül TÜKETİR, ÜRETMEZ). Şekil: {@code {construct, opId, arrange, act,
 * assert}}. {@code assert_} bileşeni Java'da {@code assert} anahtar sözcük olduğundan
 * yeniden adlandırılmıştır; JSON alan adı hâlâ {@code "assert"}dir ({@link JsonProperty}).
 *
 * <p>Her dosya TAM BİR {@code Spec}'tir — array parser yok (§5.2). Parse tolerans/ayarları
 * {@link SpecJson}'da merkezîleştirilmiştir (case-insensitive, yorum/trailing-comma).</p>
 */
public record Spec(
        String construct,
        String opId,
        JsonNode arrange,
        SpecAct act,
        @JsonProperty("assert") SpecAssert assert_) {
}
