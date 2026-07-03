package techgen.core.report;

import org.junit.jupiter.api.Test;

import techgen.core.model.ManifestJson;
import techgen.core.pipeline.Loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T2.3 — Completeness.census + gate testleri (davranış sözleşmesi §7).
 * fixture (manifest.json) üzerinden ≥20 beklenen (construct, owner) çifti tek tek assert edilir;
 * gate testleri (boş report → hepsi drop; tam-kapsanmış report → sıfır drop).
 */
class CensusTest {

    private static Path fixture(String name) {
        Path fromModuleDir = Path.of("../fixtures", name);
        if (Files.exists(fromModuleDir)) {
            return fromModuleDir;
        }
        return Path.of("fixtures", name);
    }

    private static Map.Entry<String, String> pair(String construct, String owner) {
        return new AbstractMap.SimpleEntry<>(construct, owner);
    }

    // --- Pozitif: fixture census — ≥20 beklenen çift tek tek assert ----------------------------

    @Test
    void census_fixtureManifest_containsExpectedPairs() {
        ManifestJson m = Loader.loadManifest(fixture("manifest.json"));
        List<Map.Entry<String, String>> census = Completeness.census(m);

        List<Map.Entry<String, String>> expected = List.of(
                pair("deployable", "BillingService"),
                pair("module", "Ops"),
                pair("@audit.module", "Ops"),
                pair("error", "DuplicateInvoice"),
                pair("boundary-op", "PaymentGateway.charge"),
                pair("serving", "PaymentGateway.charge:rest"),
                pair("subscription", "InvoiceCreated"),
                pair("calls", "CreateInvoice"),
                pair("compensate", "CreateInvoice"),
                pair("composite", "Money"),
                pair("enum", "InvoiceStatus"),
                pair("concurrency", "Invoice"),
                pair("invariant", "Invoice"),
                pair("sourceOfTruth", "AuditLog.invoiceRef"),
                pair("throws", "CreateInvoice->DuplicateInvoice"),
                pair("emits", "CreateInvoice->InvoiceCreated"),
                pair("serving", "CreateInvoice:grpc"),
                pair("visibility", "WriteAuditLog"),
                pair("permit", "WriteAuditLog"),
                pair("guardRef", "WriteAuditLog"),
                pair("idempotent", "CreateInvoice"),
                pair("pagination", "ListInvoices"),
                pair("consistency", "WriteAuditLog"),
                pair("@trigger.cron", "WriteAuditLog"));

        for (Map.Entry<String, String> e : expected) {
            assertTrue(census.contains(e), "census eksik: (" + e.getKey() + ", " + e.getValue() + ")");
        }
    }

    @Test
    void census_fixtureManifest_totalCount() {
        ManifestJson m = Loader.loadManifest(fixture("manifest.json"));
        List<Map.Entry<String, String>> census = Completeness.census(m);
        // Toplam census sayısı task raporuna (tasks/raporlar/T2-3.md) yazıldı: 77.
        assertEquals(77, census.size());
    }

    @Test
    void census_isDistinct_noDuplicatePairs() {
        ManifestJson m = Loader.loadManifest(fixture("manifest.json"));
        List<Map.Entry<String, String>> census = Completeness.census(m);
        assertEquals(census.size(), census.stream().distinct().count());
    }

    // --- Gate: boş report → hepsi drop; tam-kapsanmış report → sıfır drop -----------------------

    @Test
    void check_emptyReport_allCensusEntriesDropped() {
        ManifestJson m = Loader.loadManifest(fixture("manifest.json"));
        List<Map.Entry<String, String>> census = Completeness.census(m);
        BuildReport report = new BuildReport();
        Completeness.check(m, report);
        assertEquals(census.size(), report.silentDrops().size());
    }

    @Test
    void check_fullyCoveredReport_zeroDrops() {
        ManifestJson m = Loader.loadManifest(fixture("manifest.json"));
        List<Map.Entry<String, String>> census = Completeness.census(m);
        BuildReport report = new BuildReport();
        for (Map.Entry<String, String> pair : census) {
            report.realized(pair.getKey(), pair.getValue());
        }
        Completeness.check(m, report);
        assertEquals(0, report.silentDrops().size());
    }

    @Test
    void check_partiallyCoveredReport_dropsOnlyMissing() {
        ManifestJson m = Loader.loadManifest(fixture("manifest.json"));
        BuildReport report = new BuildReport();
        report.realized("deployable", "BillingService");
        Completeness.check(m, report);
        assertTrue(report.silentDrops().size() > 0);
        assertTrue(report.silentDrops().stream()
                .noneMatch(e -> e.construct().equals("deployable") && e.id().equals("BillingService")));
    }
}
