package techgen.core.gm;

import java.util.List;

/**
 * Deterministik test IR (davranış sözleşmesi §6, CoreTemplate1 {@code Gm/TestPlan.cs} karşılığı).
 * Tüm listeler ordinal-sıralı ({@link techgen.core.pipeline.TestPlanBuilder} tarafından üretilir).
 *
 * <p>Üç test türü: containment (process→flow→op) testleri + iki orphan (flow/op) senaryo türü.</p>
 */
public record TestPlan(
        List<ProcessTest> processTests,
        List<ScenarioTest> orphanFlowTests,
        List<ScenarioTest> orphanOpTests) {

    /** Boş test planı — contract/processes/flows eksikse (standalone/eski) {@link techgen.core.pipeline.TestPlanBuilder} bunu döner. */
    public static TestPlan empty() {
        return new TestPlan(List.of(), List.of(), List.of());
    }
}
