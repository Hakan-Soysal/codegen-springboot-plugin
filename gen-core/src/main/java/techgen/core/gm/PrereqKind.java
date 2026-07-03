package techgen.core.gm;

/**
 * Ön-gereksinim sınıflaması (davranış sözleşmesi §6). {@code creators[entity].size()}: 1→SINGLE,
 * &gt;1→AMBIGUOUS, 0→MISSING. AMBIGUOUS/MISSING'de creator ASLA otomatik seçilmez.
 */
public enum PrereqKind {
    SINGLE, AMBIGUOUS, MISSING
}
