package techgen.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import techgen.core.Json;
import techgen.core.gm.GenerationModel;
import techgen.core.model.ContractFile;
import techgen.core.model.ManifestJson;
import techgen.core.pipeline.GmBuilder;
import techgen.core.pipeline.Loader;
import techgen.core.report.BuildReport;
import techgen.core.report.Completeness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T6.2 — {@code docs/referans/conformance-testler-skill-sozlesmesi.md} §B {@code CompletenessTests}
 * portu (CoreTemplate1 {@code tests/Gen.Tests/CompletenessTests.cs}): {@code SilentDrop} seti
 * {@code KnownDebt} allowlist'ini (Phase-E hedefinde BOŞ) aşamaz — ratchet. Fixture'ın kendisi
 * için bu semantik olarak {@link ZeroDropTest#fixtureEmit_hasZeroSilentDrops} ile ÖRTÜŞÜR (boş
 * allowlist'e göre "aşmama" == "sıfır"); bu sınıf AYRICA ratchet mekanizmasının GERÇEKTEN
 * yanlış-negatif vermediğini (yeni bir drop eklenirse testin KIRILDIĞINI) kanıtlar — yalnız
 * "şu an temiz" değil, "gelecekte bir drop eklenirse yakalanır" iddiası doğrulanır.
 */
class CompletenessTests {

    private static Path fixture(String name) {
        Path fromModuleDir = Path.of("../fixtures", name);
        if (Files.exists(fromModuleDir)) {
            return fromModuleDir;
        }
        return Path.of("fixtures", name);
    }

    private static GenConfig h2Config() {
        return new GenConfig("h2", "inmemory");
    }

    // Ratchet: bilinen-borç allowlist'i. Her fix bu seti küçültür; YENİ drop (allowlist dışı) testi kırar.
    // Set boşalınca = gate tam yeşil (Phase E hedefi; C# CompletenessTests.cs KnownDebt paritesi).
    private static final Set<String> KNOWN_DEBT = new HashSet<>();

    @Test
    void noSilentDropsBeyondKnownDebtAllowlist_ratchet(@TempDir Path outDir) throws IOException {
        ManifestJson manifest = Loader.loadManifest(fixture("manifest.json"));
        ContractFile contract = Loader.loadContract(fixture("manifest.json"), manifest.contract());
        GenerationModel gm = GmBuilder.build(manifest, contract);
        BuildReport report = new BuildReport();

        SpringEmitter.emit(gm, outDir, report, h2Config());
        Completeness.check(manifest, report);

        Set<String> actual = report.silentDrops().stream()
                .map(d -> d.construct() + "/" + d.id()).collect(Collectors.toSet());
        actual.removeAll(KNOWN_DEBT);
        assertTrue(actual.isEmpty(), "YENİ SESSİZ DROP (allowlist dışı):\n" + String.join("\n", actual));
    }

    // ── Ratchet'in gerçekten yakaladığını kanıtla: manifest'e emitter'ın hiç ziyaret etmediği
    // (bilmediği modüle bağlı) yapay bir construct eklenince ratchet testi KIRILMALI (allowlist
    // BOŞ olduğundan bu yeni drop'u ASLA örtmez) ──────────────────────────────────────────────────

    @Test
    void ratchet_catchesNewUnexpectedDrop_whenManifestGainsOrphanConstruct(@TempDir Path outDir) throws IOException {
        JsonNode root = Json.mapper().readTree(Files.readString(fixture("manifest.json")));
        ArrayNode types = (ArrayNode) root.get("types");
        ObjectNode ghost = Json.mapper().createObjectNode();
        ghost.put("id", "GhostRatchetType");
        ghost.put("module", "NoSuchModule");
        ghost.put("kind", "composite");
        types.add(ghost);

        ManifestJson manifest = Json.mapper().treeToValue(root, ManifestJson.class);
        ContractFile contract = Loader.loadContract(fixture("manifest.json"), manifest.contract());
        GenerationModel gm = GmBuilder.build(manifest, contract);
        BuildReport report = new BuildReport();

        SpringEmitter.emit(gm, outDir, report, h2Config());
        Completeness.check(manifest, report);

        Set<String> actual = report.silentDrops().stream()
                .map(d -> d.construct() + "/" + d.id()).collect(Collectors.toSet());
        actual.removeAll(KNOWN_DEBT);
        assertFalse(actual.isEmpty(), "ratchet YAKALAMALIYDI: GhostRatchetType hiçbir modülde emit edilemez");
        assertTrue(actual.contains("composite/GhostRatchetType"));
    }
}
