package techgen.core.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Contract.cs:24. {@code target} gerçek contract'ta string ("biz.Invoice") VEYA path dizisi
 * (["Appointment","status"]) — bu yüzden toleranslı raw {@link JsonNode} (String YASAK — §8).
 * {@code expr} = calculate effect ExprNode; {@code text} = ham literal metni.
 * İkisi de @Nullable → eski/değersiz effect'ler bozulmaz. Field-ASSERT bunları okur.
 */
public record ContractEffect(
        String kind,
        /* @Nullable */ JsonNode target,
        /* @Nullable */ ExprNode expr,
        /* @Nullable */ String text) {
}
