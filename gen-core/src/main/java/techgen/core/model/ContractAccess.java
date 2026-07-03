package techgen.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Contract.cs:25 — business access 2-anahtarlı (manifest {@link AccessJson} 4-anahtarlı —
 * kritik asimetri; IsCommand ve prereq/WriteSet manifest'in 4-anahtarından türer).
 */
public record ContractAccess(
        List<String> writes,
        List<String> reads) {

    public ContractAccess {
        writes = Objects.requireNonNullElse(writes, List.of());
        reads = Objects.requireNonNullElse(reads, List.of());
    }
}
