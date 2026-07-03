package techgen.conformance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * TEK generic conformance harness (davranış sözleşmesi §A.2; referans {@code SpecRunner.cs} —
 * per-construct sınıf YOK). Her spec için: {@code stub} kontrolü → handler resolve → (arrange
 * varsa) seed çağrısı → act → assert.
 *
 * <p><b>A3 değişmezi:</b> ASSERTION SPEC'TEN gelir, runner'dan DEĞİL. {@link #assertAgainstSpec}
 * içinde hiçbir beklenen resultType/kod literal'i gömülü değildir — {@code spec.assert_()}
 * alanları ile gözlenen {@link ResultShape} karşılaştırılır.</p>
 *
 * <p><b>Invariant (property-test) dalı (T8.2; referans {@code SpecRunner.cs:91-121}):</b>
 * {@code construct=="invariant"} spec'leri {@link #runInvariantProperty} ile 50 turluk
 * deterministik rastgele-girdi üreteci koşturur; ilk ihlal karşı-örnek olarak FAIL döner.</p>
 */
public final class SpecRunner {

    private static final int INVARIANT_ROUNDS = 50;
    private static final long INVARIANT_SEED = 20260625L;
    private static final BigDecimal INVARIANT_MIN = BigDecimal.ZERO;
    private static final BigDecimal INVARIANT_MAX = new BigDecimal("1000");

    /** Bir spec'i koşar → pass/fail/skipped (davranış sözleşmesi §A.2; referans {@code SpecRunner.cs:23-63}). */
    public SpecResult run(Spec spec, GeneratedApp app) {
        if (spec.assert_().stub()) {
            return SpecResult.skipped(spec, "stub (v1, ertelenmiş): " + spec.assert_().expected());
        }

        try {
            Object handler = app.resolveHandler(spec.opId());

            // invariant (property test): rastgele girdi üreteci ile N tur → persist edilen entity
            // invariant'ı İHLAL ETMEMELİ. Beklenti (field/op/bound) SPEC'ten okunur (gömülü değil).
            if ("invariant".equals(spec.construct())) {
                return runInvariantProperty(spec, handler);
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

    /**
     * Invariant property koşumu (davranış sözleşmesi §A.2; referans
     * {@code SpecRunner.cs:91-121} {@code RunInvariantPropertyAsync}): {@code assert.field/op/bound}
     * eksikse SKIPPED; aksi halde 50 tur — her turda {@code act.with}'in ilk sayısal alanı
     * deterministik rastgele bir {@code [0,1000)} BigDecimal ile değiştirilir, handler çağrılır,
     * {@code Success.value.{field}} okunur (okunamazsa tur skip — invariant gözlenecek bir şey
     * yok), predicate ihlal edilirse İLK karşı-örnekle FAIL döner. Tüm turlar ihlalsiz geçerse PASS.
     */
    private SpecResult runInvariantProperty(Spec spec, Object handler) throws ReflectiveOperationException {
        String field = spec.assert_().field();
        String op = spec.assert_().op();
        BigDecimal bound = spec.assert_().bound();
        if (field == null || op == null || bound == null) {
            return SpecResult.skipped(spec,
                    "invariant assert eksik (field/op/bound) — property koşulamadı (deferred-with-coverage)");
        }

        JsonNode with = spec.act().with();
        String numericKey = firstNumericKey(with);
        List<BigDecimal> samples = randomDecimals(INVARIANT_ROUNDS, INVARIANT_MIN, INVARIANT_MAX, INVARIANT_SEED);

        for (int i = 0; i < samples.size(); i++) {
            JsonNode roundWith = replaceNumeric(with, numericKey, samples.get(i));
            Object request = GeneratedApp.buildRequest(handler, roundWith);
            Object resultObj = GeneratedApp.act(handler, request);

            Optional<BigDecimal> persisted = GeneratedApp.tryGetSuccessFieldDecimal(resultObj, field);
            if (persisted.isEmpty()) {
                continue; // bu tur Success/field üretmedi — invariant gözlemlenecek bir şey yok.
            }
            if (!satisfiesPredicate(persisted.get(), op, bound)) {
                return SpecResult.fail(spec, "invariant ihlali: persist '" + field + "'=" + persisted.get()
                        + " '" + op + "' " + bound + " sağlamadı (karşı-örnek tur #" + i + ")");
            }
        }
        return SpecResult.pass(spec, INVARIANT_ROUNDS + " property turunda '" + field + "' '" + op + "' " + bound
                + " invariant'ı korundu");
    }

    /** İlk sayısal alanın adı ({@code with} JSON insertion-order'ında); yoksa {@code null}. */
    private static String firstNumericKey(JsonNode with) {
        if (with == null || !with.isObject()) {
            return null;
        }
        var it = with.fields();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue().isNumber()) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** {@code with}'in bir kopyasını, {@code key} alanı {@code value} ile değiştirilmiş döner ({@code key} null ise değişmeden). */
    private static JsonNode replaceNumeric(JsonNode with, String key, BigDecimal value) {
        if (key == null || with == null || !with.isObject()) {
            return with;
        }
        ObjectNode copy = SpecJson.mapper().createObjectNode();
        var it = with.fields();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getKey().equals(key)) {
                copy.put(entry.getKey(), value);
            } else {
                copy.set(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }

    private static boolean satisfiesPredicate(BigDecimal lhs, String op, BigDecimal rhs) {
        int cmp = lhs.compareTo(rhs);
        return switch (op) {
            case ">=" -> cmp >= 0;
            case ">" -> cmp > 0;
            case "<=" -> cmp <= 0;
            case "<" -> cmp < 0;
            case "==", "=" -> cmp == 0;
            default -> throw new IllegalArgumentException("desteklenmeyen invariant op: " + op);
        };
    }

    /** Deterministik {@code [min,max)} BigDecimal üreteci ({@code new Random(seed)}; davranış sözleşmesi §A.2). */
    static List<BigDecimal> randomDecimals(int count, BigDecimal min, BigDecimal max, long seed) {
        Random rng = new Random(seed);
        BigDecimal range = max.subtract(min);
        List<BigDecimal> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(min.add(BigDecimal.valueOf(rng.nextDouble()).multiply(range)));
        }
        return out;
    }
}
