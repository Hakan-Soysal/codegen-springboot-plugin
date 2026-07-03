package techgen.core.model;

/** Contract.cs:37 — {@code flow} bir flow id referansı (containment anahtarı). */
public record ProcessStage(
        String type,
        String name,
        /* @Nullable */ String stageKind,
        /* @Nullable */ String flow,
        /* @Nullable */ String by) {
}
