package techgen.core.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T2.3 — BuildReport testleri (davranış sözleşmesi §7).
 * Covers/IdMatches (compound-id örtme + 4 sınır-ayracı + prefix-çakışması-örtmez),
 * Clean, SilentDrop sabit reason'ı, JSON determinizmi + null-reason alan-atlama.
 */
class ReportTest {

    // --- Covers / IdMatches: compound-id örtme, sınır-ayracı bazında (#, -, :, ' ') -------------

    @Test
    void covers_compoundId_hashSeparator_matches() {
        BuildReport report = new BuildReport();
        report.realized("validation", "CreateInvoice#Validation0");
        assertTrue(report.covers("validation", "CreateInvoice"));
    }

    @Test
    void covers_compoundId_dashSeparator_matches() {
        BuildReport report = new BuildReport();
        report.realized("throws", "CreateInvoice->DuplicateInvoice");
        assertTrue(report.covers("throws", "CreateInvoice"));
    }

    @Test
    void covers_compoundId_colonSeparator_matches() {
        BuildReport report = new BuildReport();
        report.realized("serving", "CreateInvoice:grpc");
        assertTrue(report.covers("serving", "CreateInvoice"));
    }

    @Test
    void covers_compoundId_spaceSeparator_matches() {
        BuildReport report = new BuildReport();
        report.realized("op", "Foo bar");
        assertTrue(report.covers("op", "Foo"));
    }

    @Test
    void covers_prefixWithoutBoundarySeparator_doesNotMatch() {
        BuildReport report = new BuildReport();
        report.realized("operation", "GetInvoice");
        assertFalse(report.covers("operation", "Get"));
    }

    @Test
    void covers_exactIdMatch_matches() {
        BuildReport report = new BuildReport();
        report.realized("operation", "GetInvoice");
        assertTrue(report.covers("operation", "GetInvoice"));
    }

    @Test
    void covers_constructCaseInsensitive_matches() {
        BuildReport report = new BuildReport();
        report.realized("Operation", "GetInvoice");
        assertTrue(report.covers("operation", "GetInvoice"));
    }

    @Test
    void covers_silentDropEntry_doesNotCountAsCovered() {
        BuildReport report = new BuildReport();
        report.silentDrop("operation", "GetInvoice");
        assertFalse(report.covers("operation", "GetInvoice"));
    }

    // --- Clean ------------------------------------------------------------------------------

    @Test
    void clean_allRealized_true() {
        BuildReport report = new BuildReport();
        report.realized("operation", "GetInvoice");
        report.realized("operation", "ListInvoices");
        assertTrue(report.clean());
    }

    @Test
    void clean_unsupportedPresent_false() {
        BuildReport report = new BuildReport();
        report.realized("operation", "GetInvoice");
        report.unsupported("rule", "SomeRule", "desteklenmiyor");
        assertFalse(report.clean());
    }

    // --- SilentDrop sabit reason + silentDrops() -----------------------------------------------

    @Test
    void silentDrop_usesFixedInv7ReasonConstant() {
        BuildReport report = new BuildReport();
        report.silentDrop("operation", "GetInvoice");
        BuildReport.BuildEntry entry = report.entries().get(0);
        assertEquals("manifest'te var; ne emit ne rapor (INV-7)", entry.reason());
        assertEquals(BuildReport.ConstructStatus.SILENT_DROP, entry.status());
    }

    @Test
    void silentDrops_returnsOnlySilentDropStatusEntries() {
        BuildReport report = new BuildReport();
        report.realized("operation", "GetInvoice");
        report.silentDrop("operation", "ListInvoices");
        report.unsupported("rule", "SomeRule", "desteklenmiyor");
        assertEquals(1, report.silentDrops().size());
        assertEquals("ListInvoices", report.silentDrops().get(0).id());
    }

    // --- JSON determinizm + null-reason alan-atlama --------------------------------------------

    @Test
    void toJson_deterministic_orderIndependentOfInsertionSequence() {
        BuildReport reportA = new BuildReport();
        reportA.realized("operation", "GetInvoice");
        reportA.unsupported("rule", "SomeRule", "desteklenmiyor");
        reportA.policy("idPolicy", "guid");
        reportA.policy("consistencyPolicy", "strong");

        BuildReport reportB = new BuildReport();
        reportB.policy("consistencyPolicy", "strong");
        reportB.unsupported("rule", "SomeRule", "desteklenmiyor");
        reportB.policy("idPolicy", "guid");
        reportB.realized("operation", "GetInvoice");

        assertEquals(reportA.toJson(), reportB.toJson());
    }

    @Test
    void toJson_omitsReasonFieldWhenNull() {
        BuildReport report = new BuildReport();
        report.realized("operation", "GetInvoice");
        assertFalse(report.toJson().contains("\"reason\""));
    }

    @Test
    void toJson_includesReasonFieldWhenPresent() {
        BuildReport report = new BuildReport();
        report.unsupported("rule", "SomeRule", "desteklenmiyor");
        assertTrue(report.toJson().contains("\"reason\" : \"desteklenmiyor\""));
    }

    @Test
    void toJson_rootShapeAndOrdinalEntrySort() {
        BuildReport report = new BuildReport();
        report.realized("operation", "GetInvoice");
        report.realized("deployable", "BillingService");
        String json = report.toJson();
        assertTrue(json.indexOf("\"BillingService\"") < json.indexOf("\"GetInvoice\""),
                "deployable ('d'<'o' ordinal) GetInvoice'den önce gelmeli: " + json);
        assertTrue(json.trim().startsWith("{"));
        assertTrue(json.contains("\"constructs\""));
        assertTrue(json.contains("\"policies\""));
    }

    @Test
    void policy_treeMapOrdinalSortedRegardlessOfInsertionOrder() {
        BuildReport report = new BuildReport();
        report.policy("zPolicy", "z");
        report.policy("aPolicy", "a");
        String json = report.toJson();
        assertTrue(json.indexOf("\"aPolicy\"") < json.indexOf("\"zPolicy\""));
    }

    // --- writeTo: dosya sonuna \n ----------------------------------------------------------

    @Test
    void writeTo_appendsTrailingNewline(@TempDir Path tempDir) throws IOException {
        BuildReport report = new BuildReport();
        report.realized("operation", "GetInvoice");
        Path out = tempDir.resolve("build-report.json");
        report.writeTo(out);
        String written = Files.readString(out);
        assertTrue(written.endsWith("\n"));
        assertEquals(report.toJson() + "\n", written);
    }
}
