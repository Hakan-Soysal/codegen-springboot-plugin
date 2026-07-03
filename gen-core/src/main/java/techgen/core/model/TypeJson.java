package techgen.core.model;

import java.util.List;

/**
 * Manifest.cs:60 — anahtar Id. {@code kind} census'ta construct adının KENDİSİ olur
 * (Completeness.cs:52; composite/enum vb.). {@code values} enum değerleri.
 * fields/values/ext @Nullable (sözleşmede nullable — normalize edilmez).
 */
public record TypeJson(
        String id,
        String module,
        String kind,
        /* @Nullable */ List<FieldJson> fields,
        /* @Nullable */ List<String> values,
        /* @Nullable */ List<ExtJson> ext) {
}
