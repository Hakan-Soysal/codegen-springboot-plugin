package techgen.core.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * operations.json kök şeması (davranış sözleşmesi §2, Contract.cs:8-15).
 * TÜM alanlar @Nullable (toleranslı) ve KASITLI olarak normalize EDİLMEZ:
 * TestPlanBuilder {@code processes == null || flows == null} → boş TestPlan semantiği
 * null/boş-liste ayrımına dayanır. {@code relations} toleranslı ham JSON (tüketilmiyor).
 * {@code schedule}/{@code delegation} şu an tüketilmiyor (bilinmeyen alan olarak ignore).
 */
public record ContractFile(
        /* @Nullable */ ContractMeta meta,
        /* @Nullable */ List<ContractOp> operations,
        /* @Nullable */ List<ContractEntity> entities,
        /* @Nullable */ List<ContractActor> actors,
        /* @Nullable */ List<JsonNode> relations,
        /* @Nullable */ List<ProcessJson> processes,
        /* @Nullable */ List<FlowJson> flows) {
}
