package techgen.core.model;

import java.util.List;

/**
 * Manifest.cs:52-54. targetModule/crossModule/sourceOfTruth/ext @Nullable —
 * fixture'da görünmese de şemadadır (anti-pattern §8: alan atlama yasak).
 */
public record EntityFieldJson(
        String name,
        String type,
        boolean collection,
        String cardinality,
        String ref,
        /* @Nullable */ String targetModule,
        /* @Nullable */ Boolean crossModule,
        /* @Nullable */ SourceOfTruth sourceOfTruth,
        /* @Nullable */ List<ExtJson> ext) {
}
