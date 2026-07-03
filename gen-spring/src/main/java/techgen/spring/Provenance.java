package techgen.spring;

import java.util.List;
import java.util.Objects;

/**
 * {@code provenance.json} kök şeması (davranış sözleşmesi §5, {@code Provenance.cs} karşılığı:
 * {@code Provenance(Generator, Version, Files[])}).
 *
 * @param generator üreteci tanımlayan sabit ad (örn. {@code "techgen-spring"}).
 * @param version   üreteç artefakt sürümü.
 * @param files     bu run'da yazılan {@code Generated} sınıfı dosyaların listesi.
 */
public record Provenance(String generator, String version, List<ProvenanceEntry> files) {

    public Provenance {
        files = Objects.requireNonNullElse(files, List.of());
    }
}
