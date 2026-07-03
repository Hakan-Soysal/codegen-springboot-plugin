package techgen.core.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import techgen.core.errors.LoadError;
import techgen.core.model.ContractFile;
import techgen.core.model.ManifestJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T1.3 — Loader (fatal vs sessiz-null) testleri (davranış sözleşmesi §4/§8).
 * 4 pozitif (fixture manifest+contract, studyo çifti) + 4 negatif
 * (yok-dosya→LoadError, bozuk-json→LoadError, contract-yok→null, contract-bozuk→null).
 */
class LoaderTest {

    private static Path fixture(String name) {
        Path fromModuleDir = Path.of("../fixtures", name);
        if (Files.exists(fromModuleDir)) {
            return fromModuleDir;
        }
        return Path.of("fixtures", name);
    }

    // --- Pozitif: fixture manifest çifti ---------------------------------------------------

    @Test
    void loadManifest_fixtureManifest_parsesSuccessfully() {
        ManifestJson m = Loader.loadManifest(fixture("manifest.json"));
        assertEquals("linked", m.mode());
        assertEquals(4, m.operations().size());
    }

    @Test
    void loadContract_fixtureManifestContractPair_resolvesRelativeAndParses() {
        Path manifestPath = fixture("manifest.json");
        ContractFile c = Loader.loadContract(manifestPath, "./operations.json");
        assertNotNull(c);
        assertNotNull(c.operations());
        assertTrue(c.operations().size() > 0);
    }

    // --- Pozitif: studyo ölçek çifti --------------------------------------------------------

    @Test
    void loadManifest_studyoManifest_parsesSuccessfully() {
        ManifestJson m = Loader.loadManifest(fixture("studyo.manifest.json"));
        assertEquals(43, m.operations().size());
    }

    @Test
    void loadContract_studyoManifestContractPair_resolvesRelativeAndParses() {
        Path manifestPath = fixture("studyo.manifest.json");
        ContractFile c = Loader.loadContract(manifestPath, "./studyo.operations.json");
        assertNotNull(c);
        assertNotNull(c.operations());
        assertTrue(c.operations().size() > 0);
    }

    // --- Negatif: manifest yok-dosya → LoadError (mesaj "bulunamadı") -----------------------

    @Test
    void loadManifest_missingFile_throwsLoadErrorWithBulunamadiMessage(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.json");
        LoadError err = assertThrows(LoadError.class, () -> Loader.loadManifest(missing));
        assertTrue(err.getMessage().contains("bulunamadı"), "mesaj 'bulunamadı' içermeli: " + err.getMessage());
        assertTrue(err.getMessage().contains(missing.toString()), "mesaj path'i içermeli: " + err.getMessage());
    }

    // --- Negatif: manifest bozuk-json → LoadError (mesaj "ayrıştırılamadı") -----------------

    @Test
    void loadManifest_malformedJson_throwsLoadErrorWithAyristirilamadiMessage(@TempDir Path tempDir) throws IOException {
        Path bad = tempDir.resolve("bad-manifest.json");
        Files.writeString(bad, "{ this is not valid json ");
        LoadError err = assertThrows(LoadError.class, () -> Loader.loadManifest(bad));
        assertTrue(err.getMessage().contains("ayrıştırılamadı"), "mesaj 'ayrıştırılamadı' içermeli: " + err.getMessage());
    }

    // --- Negatif: contract yok-dosya → null (throw YOK) -------------------------------------

    @Test
    void loadContract_missingContractFile_returnsNull() {
        Path manifestPath = fixture("manifest.json");
        ContractFile c = Loader.loadContract(manifestPath, "./does-not-exist-operations.json");
        assertNull(c);
    }

    // --- Negatif: contract bozuk-json → null (throw YOK) ------------------------------------

    @Test
    void loadContract_malformedContractFile_returnsNull(@TempDir Path tempDir) throws IOException {
        Path manifestPath = tempDir.resolve("manifest.json");
        Files.writeString(manifestPath, "{}");
        Path badContract = tempDir.resolve("bad-operations.json");
        Files.writeString(badContract, "{ this is not valid json ");

        ContractFile c = Loader.loadContract(manifestPath, "bad-operations.json");
        assertNull(c);
    }
}
