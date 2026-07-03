package techgen.conformance;

/**
 * Üretilen app'in döndürdüğü Result&lt;T&gt; objesinin yapısal görünümü: alt-tip basit adı +
 * (varsa) kod-taşıyan erişimcinin değeri (davranış sözleşmesi §A.3; referans
 * {@code GeneratedApp.cs:118-131} {@code ResultShape}). BU DEĞERLER burada assert EDİLMEZ —
 * yalnızca {@link SpecRunner#assertAgainstSpec} tarafından spec'in beklentisiyle
 * karşılaştırılmak üzere okunur (A3 değişmezi).
 */
public record ResultShape(String resultType, String code) {
}
