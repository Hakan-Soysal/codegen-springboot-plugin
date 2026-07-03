package techgen.core.model;

/** Contract.cs:39 — {@code target} op-id referansı (RunSequence kaynağı). */
public record FlowStep(
        String type,
        String name,
        /* @Nullable */ String target,
        boolean optional,
        boolean repeat,
        /* @Nullable */ String using) {
}
