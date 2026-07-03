package techgen.core.gm;

/**
 * T2.2 (TestPlanBuilder) YER TUTUCUSU — GEÇİCİ boş record.
 *
 * <p><b>Karar (T2.1 kapsamında):</b> davranış sözleşmesi §6 / {@code tasks/T2-2-testplan.md}
 * TestPlan IR'ını (processTests/orphanFlowTests/orphanOpTests + ProcessTest/ScenarioTest/
 * PrereqStep/PrereqKind) tanımlar; bu task (T2.1) yalnız {@link techgen.core.gm.GenerationModel}
 * derlenebilsin diye BOŞ bir yer tutucu tip ekler. T2.2 bu dosyayı GERÇEK IR ile
 * DEĞİŞTİRECEK/GENİŞLETECEKTİR. {@link techgen.core.pipeline.GmBuilder}, T2.2 gelene kadar
 * {@code testPlan} alanını {@link #empty()} ile doldurur (gerçek {@code TestPlanBuilder.build(...)}
 * çağrısı T2.2'nin işidir).</p>
 */
public record TestPlan() {

    /** Boş test planı — T2.2 gelene kadar {@link techgen.core.pipeline.GmBuilder} bunu kullanır. */
    public static TestPlan empty() {
        return new TestPlan();
    }
}
