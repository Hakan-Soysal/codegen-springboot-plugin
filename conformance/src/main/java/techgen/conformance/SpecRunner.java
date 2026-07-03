package techgen.conformance;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * TEK generic conformance harness (davranış sözleşmesi §A.2; referans {@code SpecRunner.cs} —
 * per-construct sınıf YOK). Her spec için: {@code stub} kontrolü → handler resolve → (arrange
 * varsa) seed çağrısı → act → assert.
 *
 * <p><b>A3 değişmezi:</b> ASSERTION SPEC'TEN gelir, runner'dan DEĞİL. {@link #assertAgainstSpec}
 * içinde hiçbir beklenen resultType/kod literal'i gömülü değildir — {@code spec.assert_()}
 * alanları ile gözlenen {@link ResultShape} karşılaştırılır.</p>
 *
 * <p><b>Invariant (property-test) dalı — bu task'ta İSKELET:</b> {@code construct=="invariant"}
 * spec'leri şimdilik SKIPPED döner; N-turlu rastgele girdi üreteci gövdesi T8.2'de eklenir
 * (task §5.4).</p>
 */
public final class SpecRunner {

    /** Bir spec'i koşar → pass/fail/skipped (davranış sözleşmesi §A.2; referans {@code SpecRunner.cs:23-63}). */
    public SpecResult run(Spec spec, GeneratedApp app) {
        if (spec.assert_().stub()) {
            return SpecResult.skipped(spec, "stub (v1, ertelenmiş): " + spec.assert_().expected());
        }

        try {
            Object handler = app.resolveHandler(spec.opId());

            // invariant (property test): T8.2'de gövde eklenecek — bu task'ta metot iskeleti.
            if ("invariant".equals(spec.construct())) {
                return SpecResult.skipped(spec,
                        "invariant property koşumu T8.2'de tamamlanacak (iskelet; deferred-with-coverage)");
            }

            // ── arrange ── dil-nötr kurulum talimatı, kind'a göre yorumlanır (construct'a göre değil).
            if ("duplicate".equals(arrangeKind(spec.arrange()))) {
                Object seedRequest = GeneratedApp.buildRequest(handler, spec.act().with());
                GeneratedApp.act(handler, seedRequest);
            }

            // ── act ──
            Object request = GeneratedApp.buildRequest(handler, spec.act().with());
            Object resultObj = GeneratedApp.act(handler, request);

            // ── assert ──
            ResultShape observed = GeneratedApp.inspect(resultObj);
            return assertAgainstSpec(spec, observed);
        } catch (Exception ex) {
            // Herhangi bir execution hatası (UnsupportedOperationException dahil, boş seam) = FAIL.
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return SpecResult.fail(spec, "execution exception: " + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());
        }
    }

    /**
     * ASSERTION BURADA — ama beklenen değerler SPEC'TEN okunur, hiçbir literal gömülmez
     * (davranış sözleşmesi §A.2/A3; referans {@code SpecRunner.cs:66-86}).
     */
    static SpecResult assertAgainstSpec(Spec spec, ResultShape observed) {
        String expectedType = spec.assert_().resultType();
        if (expectedType == null) {
            return SpecResult.fail(spec, "spec.assert.resultType yok — koşulamaz (stub değil)");
        }
        if (!observed.resultType().equals(expectedType)) {
            return SpecResult.fail(spec,
                    "resultType beklenen='" + expectedType + "' (spec), gözlenen='" + observed.resultType() + "'");
        }
        String expectedCode = spec.assert_().code();
        if (expectedCode != null && !expectedCode.equals(observed.code())) {
            return SpecResult.fail(spec, "code beklenen='" + expectedCode + "' (spec), gözlenen='"
                    + (observed.code() == null ? "<null>" : observed.code()) + "'");
        }
        return SpecResult.pass(spec, "resultType='" + observed.resultType() + "'"
                + (observed.code() != null ? ", code='" + observed.code() + "'" : ""));
    }

    private static String arrangeKind(JsonNode arrange) {
        if (arrange != null && arrange.isObject() && arrange.hasNonNull("kind") && arrange.get("kind").isTextual()) {
            return arrange.get("kind").asText();
        }
        return null;
    }
}
