package techgen.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Manifest.cs:33 — 4-anahtarlı access modeli ("Kapı 0 authority").
 * Command/query ayrımı: isCommand = creates|updates|deletes boş değilse (GmBuilder.cs:67).
 * (Business tarafındaki {@link ContractAccess} 2-anahtarlıdır — kritik asimetri.)
 */
public record AccessJson(
        List<String> reads,
        List<String> creates,
        List<String> updates,
        List<String> deletes) {

    public AccessJson {
        reads = Objects.requireNonNullElse(reads, List.of());
        creates = Objects.requireNonNullElse(creates, List.of());
        updates = Objects.requireNonNullElse(updates, List.of());
        deletes = Objects.requireNonNullElse(deletes, List.of());
    }
}
