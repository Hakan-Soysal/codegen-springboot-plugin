package techgen.core.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T6.2 — {@code docs/referans/conformance-testler-skill-sozlesmesi.md} §B {@code ReportTests}
 * portu (CoreTemplate1 {@code tests/Gen.Tests/ReportTests.cs}, 4 method). 3/4'ü zaten
 * {@link ReportTest} (T2.3) tarafından fixture-ötesi birebir kapsanmıştı — bu sınıf yalnız
 * kalan/eksik iddiayı ("silentDrops diye ayrı bir JSON alanı yoktur; drop'lar da diğerleri gibi
 * {@code status} alanlı bir {@code constructs} kaydıdır — exit≠0 farkı çağıran tarafın (gen-cli
 * Main) işidir, JSON şemasının değil") ekler; diğer üçü referansta şu {@link ReportTest} metodlarınca
 * kapsanır: {@code Records_status_and_is_clean_only_when_all_realized} →
 * {@code clean_allRealized_true}/{@code clean_unsupportedPresent_false}; {@code
 * Covers_matches_compound_ids_but_not_prefix_collisions} → {@code covers_compoundId_*}/{@code
 * covers_prefixWithoutBoundarySeparator_doesNotMatch}; {@code Json_is_deterministically_ordered} →
 * {@code toJson_deterministic_orderIndependentOfInsertionSequence}/{@code
 * toJson_rootShapeAndOrdinalEntrySort}. gen-cli exit≠0 kanıtı ayrıca {@code gen-cli
 * MainTest#orphanModuleType_isRealSilentDrop_exitsOneWithDropLine} ile GERÇEK süreçle
 * doğrulanmıştır (mükerrer test YARATILMADI).
 */
class ReportTests {

    // ── 1:1 referans-metod adı taşıyan port (Records_status_and_is_clean_only_when_all_realized) —
    // ReportTest#clean_* ile ÖRTÜŞÜR (bkz. sınıf Javadoc'u); yine de C# metod-adı izlenebilirliği
    // için burada da tutuluyor (ucuz, zararsız). ────────────────────────────────────────────────

    @Test
    void recordsStatus_andIsClean_onlyWhenAllRealized() {
        BuildReport report = new BuildReport();
        report.realized("operation", "CreateInvoice");
        assertTrue(report.clean());
        report.unsupported("invariant", "Invoice", "no native receiver");
        assertFalse(report.clean());
    }

    @Test
    void toJson_hasNoSeparateSilentDropsField_onlyStatusFieldOnConstructs() {
        BuildReport report = new BuildReport();
        report.realized("operation", "GetInvoice");
        report.silentDrop("operation", "ListInvoices");
        report.unsupported("rule", "SomeRule", "desteklenmiyor");

        String json = report.toJson();
        assertFalse(json.contains("\"silentDrops\""),
                "şemada AYRI bir 'silentDrops' alanı OLMAMALI — drop'lar da constructs[].status ile taşınır");
        assertTrue(json.contains("\"status\" : \"silentDrop\""),
                "drop, constructs[] içinde status=\"silentDrop\" ile görünmeli");
        // silentDrops() Java API'si BuildReport üzerinde bir okuma-yardımcısıdır (JSON alanı DEĞİL) —
        // programatik erişim var, şema alanı yok; ikisini karıştırma referans notunun tam anlamı budur.
        assertTrue(report.silentDrops().size() == 1);
    }
}
