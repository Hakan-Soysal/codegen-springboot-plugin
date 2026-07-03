package techgen.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Manifest.cs:43-49 — anahtar Id, 21 alan sözleşme sırasıyla.
 * Kritik: {@code realizes} = business-contract op id'sine join anahtarı (tekil; @Nullable).
 * {@code emits} = string[] (event adları) — hedef ConsumerOpRef manifest'te taşınmaz.
 * Not: JSON alanı {@code "throws"} Java'da rezerve kelime olduğundan bileşen adı
 * {@code throwsList}'tir; JSON adı {@code @JsonProperty} ile birebir korunur.
 */
public record OperationJson(
        String id,
        String module,
        String visibility,
        /* @Nullable */ String realizes,
        SignatureJson signature,
        List<ServingJson> serving,
        List<String> roles,
        /* @Nullable */ String ownership,
        AccessJson access,
        List<GuardedExpr> validation,
        List<GuardedExpr> rule,
        /* @Nullable */ String note,
        /* @Nullable */ String businessNote,
        Consistency consistency,
        /* @Nullable */ Abac abac,
        List<String> scopes,
        @JsonProperty("throws") List<String> throwsList,
        /* @Nullable */ Idempotent idempotent,
        List<String> emits,
        /* @Nullable */ Pagination pagination,
        /* @Nullable */ List<ExtJson> ext) {

    public OperationJson {
        serving = Objects.requireNonNullElse(serving, List.of());
        roles = Objects.requireNonNullElse(roles, List.of());
        validation = Objects.requireNonNullElse(validation, List.of());
        rule = Objects.requireNonNullElse(rule, List.of());
        scopes = Objects.requireNonNullElse(scopes, List.of());
        throwsList = Objects.requireNonNullElse(throwsList, List.of());
        emits = Objects.requireNonNullElse(emits, List.of());
    }
}
