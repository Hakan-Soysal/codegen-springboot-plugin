package techgen.core.model;

import java.util.List;
import java.util.Objects;

/** Contract.cs:27-30 — anahtar Id. signature/description/domain @Nullable. */
public record ContractOp(
        String id,
        String kind,
        /* @Nullable */ ContractSignature signature,
        /* @Nullable */ String description,
        List<ContractGuard> guards,
        List<ContractEffect> effects,
        ContractAccess access,
        List<String> flows,
        List<String> processes,
        /* @Nullable */ String domain) {

    public ContractOp {
        guards = Objects.requireNonNullElse(guards, List.of());
        effects = Objects.requireNonNullElse(effects, List.of());
        flows = Objects.requireNonNullElse(flows, List.of());
        processes = Objects.requireNonNullElse(processes, List.of());
    }
}
