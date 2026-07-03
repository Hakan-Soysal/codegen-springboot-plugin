package techgen.spring;

/**
 * Bir üretilmiş dosyanın provenance kaydı (davranış sözleşmesi §5, {@code Provenance.cs}
 * karşılığı: {@code ProvenanceEntry(Path, Class, Sha256)}).
 *
 * @param path   outDir-göreli, {@code /} ayraçlı dosya yolu.
 * @param clazz  dosya sınıfı etiketi (örn. {@code "Generated"}) — yalnız {@code Generated}
 *               sınıfına ait dosyalar provenance'a eklenir; bu filtreleme çağıran tarafından
 *               (emit yazım altyapısı) yapılır.
 * @param sha256 dosya içeriğinin UTF-8 → SHA-256 → lowercase hex özeti.
 */
public record ProvenanceEntry(String path, String clazz, String sha256) {
}
