package techgen.core.gm;

import techgen.core.model.ContractOp;
import techgen.core.model.OperationJson;

/**
 * Operasyon + join'den gelen iş-bağlamı + türev komut/sorgu ayrımı
 * (davranış sözleşmesi §5, {@code GenerationModel.cs:59-63} karşılığı).
 * {@code business} standalone modda veya {@code realizes} çözülmediğinde null olabilir.
 * {@code isCommand}: manifest access 4-anahtarından (creates/updates/deletes) türer —
 * business {@code kind}'ından DEĞİL (anti-pattern §8).
 */
public record GmOperation(OperationJson op, /* @Nullable */ ContractOp business, boolean isCommand) {

    public String id() {
        return op.id();
    }

    public String module() {
        return op.module();
    }
}
