package techgen.core.gm;

import java.util.List;

/**
 * Orphan flow / orphan op testi: tek-scope senaryo, ProcessTest ile aynı 3-faz (arrange/act/assert).
 * {@code scope} ∈ {@code "OrphanFlow"} | {@code "OrphanOp"}.
 * Davranış sözleşmesi §6, CoreTemplate1 {@code Gm/TestPlan.cs:ScenarioTest} karşılığı.
 */
public record ScenarioTest(
        String id,
        String scope,
        List<String> runSequence,
        List<PrereqStep> prerequisites,
        List<String> writeSet) {
}
