package techgen.core.model;

import java.util.List;

/**
 * Contract.cs:36 (TestPlan IR girdisi). entity/note/items @Nullable — items KASITLI
 * normalize edilmez (nullable sözleşme; TestPlanBuilder toleranslı gezer).
 */
public record ProcessJson(
        String id,
        /* @Nullable */ String entity,
        /* @Nullable */ String note,
        /* @Nullable */ List<ProcessStage> items) {
}
