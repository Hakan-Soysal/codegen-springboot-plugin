package techgen.core.model;

import java.util.List;

/** Contract.cs:38. actor/note/items @Nullable — items KASITLI normalize edilmez. */
public record FlowJson(
        String id,
        /* @Nullable */ String actor,
        /* @Nullable */ String note,
        /* @Nullable */ List<FlowStep> items) {
}
