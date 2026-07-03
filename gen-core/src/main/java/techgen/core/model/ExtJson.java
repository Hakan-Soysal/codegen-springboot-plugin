package techgen.core.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Objects;

/**
 * Manifest.cs:41 — census'ta {@code @{ns}.{name}} construct adıyla sayılır
 * (Completeness.cs:95). args toleranslı raw-JSON (tipli sınıf YOK — sözleşme).
 */
public record ExtJson(
        String ns,
        String name,
        Map<String, JsonNode> args) {

    public ExtJson {
        args = Objects.requireNonNullElse(args, Map.of());
    }
}
