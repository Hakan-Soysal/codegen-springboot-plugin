package techgen.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contract.cs:33. Not: JSON alanı {@code "extends"} Java'da rezerve kelime olduğundan
 * bileşen adı {@code extendsActor}'dur; JSON adı {@code @JsonProperty} ile birebir korunur.
 * extendsActor @Nullable.
 */
public record ContractActor(
        String id,
        @JsonProperty("extends") /* @Nullable */ String extendsActor) {
}
