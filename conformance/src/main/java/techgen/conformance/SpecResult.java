package techgen.conformance;

/**
 * Bir spec'in koşum sonucu (davranış sözleşmesi §A.2; referans {@code SpecRunner.cs:181-190}).
 * {@code detail} insan-okunur kanıt metnidir (assert edilmez — yalnız rapor amaçlı).
 */
public record SpecResult(Spec spec, SpecStatus status, String detail) {

    public static SpecResult pass(Spec spec, String detail) {
        return new SpecResult(spec, SpecStatus.PASS, detail);
    }

    public static SpecResult fail(Spec spec, String detail) {
        return new SpecResult(spec, SpecStatus.FAIL, detail);
    }

    public static SpecResult skipped(Spec spec, String detail) {
        return new SpecResult(spec, SpecStatus.SKIPPED, detail);
    }

    public boolean isPass() {
        return status == SpecStatus.PASS;
    }

    public boolean isFail() {
        return status == SpecStatus.FAIL;
    }

    @Override
    public String toString() {
        return "[" + status + "] " + spec.construct() + "/" + spec.opId() + ": " + detail;
    }
}
