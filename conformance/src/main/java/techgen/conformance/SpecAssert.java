package techgen.conformance;

import java.math.BigDecimal;

/**
 * SPEC şemasının {@code assert} bölümü (davranış sözleşmesi §A.1; referans
 * {@code conformance-adapter/Spec.cs} {@code SpecAssert}). A3 değişmezi: bu alanların hepsi
 * SPEC dosyasından okunur — runner/adapter kodu HİÇBİR beklenen değeri literal olarak
 * gömmez (bkz. {@link SpecRunner#assertAgainstSpec}).
 *
 * <ul>
 *   <li>{@code resultType} — beklenen Result alt-tipinin basit adı ({@code null} ise
 *   "koşulamaz" FAIL'i tetikler; stub değilse).</li>
 *   <li>{@code code} — beklenen kod (kod-taşıyan Result alt-tipi için); {@code null} ise
 *   kod karşılaştırılmaz.</li>
 *   <li>{@code violated} — insan-okunur not; assert edilmez.</li>
 *   <li>{@code stub} — {@code true} ise runner bu spec'i koşturmadan SKIPPED döner.</li>
 *   <li>{@code expected}/{@code source} — provenance/insan-okunur beklenti notu.</li>
 *   <li>{@code field}/{@code op}/{@code bound} — invariant (property-test) üçlüsü; T8.2'de
 *   property koşumunda kullanılır (bu task'ta yalnız DTO alanı olarak taşınır).</li>
 * </ul>
 */
public record SpecAssert(
        String resultType,
        String code,
        String violated,
        boolean stub,
        String expected,
        String source,
        String field,
        String op,
        BigDecimal bound) {
}
