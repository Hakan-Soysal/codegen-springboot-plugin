package techgen.core.gm;

import java.util.List;

/**
 * Bir sürecin testi: sıralı çağrı zinciri (RunSequence, Items sırası — ordinal DEĞİL) +
 * ön-gereksinim (arrange) + (Kapı 0) yazma-kümesi (assert kaynağı).
 * Davranış sözleşmesi §6, CoreTemplate1 {@code Gm/TestPlan.cs:ProcessTest} karşılığı.
 */
public record ProcessTest(
        String processId,
        String entity,
        List<String> runSequence,
        List<PrereqStep> prerequisites,
        List<String> writeSet) {
}
