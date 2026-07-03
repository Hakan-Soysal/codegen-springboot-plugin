package techgen.core.model;

/** Contract.cs:19. role/calendar/text/ast @Nullable. */
public record ContractGuard(
        String id,
        String kind,
        /* @Nullable */ String role,
        /* @Nullable */ String calendar,
        /* @Nullable */ String text,
        /* @Nullable */ ExprNode ast) {
}
