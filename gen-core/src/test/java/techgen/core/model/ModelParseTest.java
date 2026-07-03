package techgen.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.junit.jupiter.api.Test;

import techgen.core.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T1.2 — Manifest/Contract POJO'ları + techgen.core.Json yapılandırması testleri.
 * Fixture çiftleri (fixtures/ + studyo ölçek çifti) merkezî Json.parse ile kayıpsız
 * parse edilmelidir (davranış sözleşmesi §1/§2/§4).
 */
class ModelParseTest {

    private static Path fixture(String name) {
        Path fromModuleDir = Path.of("../fixtures", name);
        if (Files.exists(fromModuleDir)) {
            return fromModuleDir;
        }
        return Path.of("fixtures", name);
    }

    private static String read(String name) throws IOException {
        return Files.readString(fixture(name));
    }

    private static ManifestJson fixtureManifest() throws IOException {
        return Json.parse(read("manifest.json"), ManifestJson.class);
    }

    // --- 5.4: fixture manifest sayımları -------------------------------------------------

    @Test
    void manifestFixture_topLevelCounts() throws IOException {
        ManifestJson m = fixtureManifest();
        assertEquals("linked", m.mode());
        assertEquals(4, m.operations().size());
        assertEquals(2, m.entities().size());
        assertEquals(4, m.types().size());
        assertEquals(1, m.errors().size());
        assertEquals(1, m.events().size());
        assertEquals(1, m.subscriptions().size());
        assertEquals(1, m.externals().size());
        assertEquals(1, m.uncharted().size());
        assertEquals(2, m.callEdges().size());
    }

    @Test
    void manifestFixture_createInvoiceDetails() throws IOException {
        ManifestJson m = fixtureManifest();
        OperationJson op = m.operations().get(0);
        assertEquals("CreateInvoice", op.id());
        assertEquals(List.of("Invoice"), op.access().creates());
        assertEquals(List.of("DuplicateInvoice"), op.throwsList());
        assertNotNull(op.idempotent());
        assertEquals(List.of("customerId"), op.idempotent().keys());
        assertNotNull(op.ext());
        assertEquals(2, op.ext().size());
        assertEquals(1, op.validation().size());
        assertTrue(op.validation().get(0).ast() instanceof BinaryNode,
                "validation[0].ast BinaryNode olmalı");
    }

    @Test
    void manifestFixture_accessJsonHasFourKeys() throws IOException {
        // AccessJson 4-liste (reads/creates/updates/deletes) — ContractAccess 2-liste asimetrisi.
        ManifestJson m = fixtureManifest();
        AccessJson access = m.operations().get(0).access();
        assertEquals(List.of(), access.reads());
        assertEquals(List.of("Invoice"), access.creates());
        assertEquals(List.of(), access.updates());
        assertEquals(List.of(), access.deletes());
    }

    // --- 5.4: contract --------------------------------------------------------------------

    @Test
    void contractFixture_createInvoiceAccessTwoKey() throws IOException {
        ContractFile c = Json.parse(read("operations.json"), ContractFile.class);
        assertNotNull(c.operations());
        ContractOp op = c.operations().stream()
                .filter(o -> o.id().equals("biz.CreateInvoice"))
                .findFirst()
                .orElseThrow();
        // ContractAccess 2-anahtarlı business access modeli.
        assertEquals(List.of("biz.Invoice"), op.access().writes());
        assertEquals(List.of(), op.access().reads());
    }

    @Test
    void contractEffect_targetAcceptsString() throws JsonProcessingException {
        ContractEffect e = Json.parse(
                "{\"kind\":\"create\",\"target\":\"biz.Invoice\"}", ContractEffect.class);
        assertNotNull(e.target());
        assertTrue(e.target().isTextual(), "string target JsonNode textual olmalı");
        assertEquals("biz.Invoice", e.target().asText());
    }

    @Test
    void contractEffect_targetAcceptsPathArray() throws JsonProcessingException {
        ContractEffect e = Json.parse(
                "{\"kind\":\"set\",\"target\":[\"Appointment\",\"status\"]}", ContractEffect.class);
        assertNotNull(e.target());
        assertTrue(e.target().isArray(), "path-array target JsonNode array olmalı");
        assertEquals(2, e.target().size());
        assertEquals("Appointment", e.target().get(0).asText());
        assertEquals("status", e.target().get(1).asText());
    }

    // --- 5.4: studyo ölçek çifti ------------------------------------------------------------

    @Test
    void studyoManifest_parsesWith43Operations() throws IOException {
        ManifestJson m = Json.parse(read("studyo.manifest.json"), ManifestJson.class);
        assertEquals(43, m.operations().size());
    }

    @Test
    void studyoContract_parsesWithoutException() throws IOException {
        ContractFile c = Json.parse(read("studyo.operations.json"), ContractFile.class);
        assertNotNull(c.operations());
        assertTrue(c.operations().size() > 0);
    }

    // --- 5.4: tolerans + Json yapılandırması -------------------------------------------------

    @Test
    void unknownFieldsAreTolerated() throws JsonProcessingException {
        // Bilinmeyen alan sessizce yok sayılır (FAIL_ON_UNKNOWN_PROPERTIES=false).
        ManifestJson m = Json.parse(
                "{\"mode\":\"linked\",\"fooUnknown\":1,\"operations\":[]}", ManifestJson.class);
        assertEquals("linked", m.mode());
        assertEquals(List.of(), m.operations());
    }

    @Test
    void nullDeserializeThrows() {
        // Acceptance 6.3: null-deserialize yasak.
        assertThrows(Exception.class, () -> Json.parse("null", ManifestJson.class));
    }

    @Test
    void missingListsNormalizeToEmpty() throws JsonProcessingException {
        // Eksik liste alanları ctor'da List.of()'a normalize edilir.
        ManifestJson m = Json.parse("{\"mode\":\"standalone\"}", ManifestJson.class);
        assertEquals(List.of(), m.deployables());
        assertEquals(List.of(), m.modules());
        assertEquals(List.of(), m.operations());
        assertEquals(List.of(), m.entities());
        assertEquals(List.of(), m.types());
        assertEquals(List.of(), m.errors());
        assertEquals(List.of(), m.events());
        assertEquals(List.of(), m.subscriptions());
        assertEquals(List.of(), m.externals());
        assertEquals(List.of(), m.uncharted());
        assertEquals(List.of(), m.callEdges());
        assertNull(m.contract());
    }

    @Test
    void caseInsensitivePropertiesAccepted() throws JsonProcessingException {
        // ACCEPT_CASE_INSENSITIVE_PROPERTIES=true.
        ManifestJson m = Json.parse("{\"Mode\":\"linked\"}", ManifestJson.class);
        assertEquals("linked", m.mode());
    }

    @Test
    void contractFile_nullableSectionsStayNull() throws JsonProcessingException {
        // ContractFile alanları normalize EDİLMEZ: processes/flows null semantiği
        // TestPlanBuilder'ın boş-TestPlan kısa devresi için korunmalı.
        ContractFile c = Json.parse("{\"meta\":{\"schemaVersion\":2}}", ContractFile.class);
        assertNotNull(c.meta());
        assertEquals(2, c.meta().schemaVersion());
        assertNull(c.operations());
        assertNull(c.processes());
        assertNull(c.flows());
    }
}
