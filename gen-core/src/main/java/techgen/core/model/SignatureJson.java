package techgen.core.model;

import java.util.List;
import java.util.Objects;

/** Manifest.cs:30 — returns sadece tip adı stringi. */
public record SignatureJson(
        List<ParamJson> params,
        String returns) {

    public SignatureJson {
        params = Objects.requireNonNullElse(params, List.of());
    }
}
