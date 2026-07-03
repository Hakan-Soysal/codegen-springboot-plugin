package techgen.core.model;

import java.util.List;

/** Manifest.cs:74. fields/values/ext @Nullable (sözleşmede nullable — normalize edilmez). */
public record UnchartedType(
        String id,
        String kind,
        /* @Nullable */ List<FieldJson> fields,
        /* @Nullable */ List<String> values,
        /* @Nullable */ List<ExtJson> ext) {
}
