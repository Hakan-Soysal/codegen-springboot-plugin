package techgen.core.gm;

/**
 * Bir ön-gereksinim adımı: {@code entity} üretilmeli. {@code creatorOp} yalnız
 * {@link PrereqKind#SINGLE} iken doludur — AMBIGUOUS/MISSING'de her zaman {@code null}
 * (davranış sözleşmesi §6: "ASLA creator seçilmez").
 */
public record PrereqStep(String entity, String creatorOp, PrereqKind kind) {
}
