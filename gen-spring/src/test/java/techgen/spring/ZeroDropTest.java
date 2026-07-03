package techgen.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4.5 — M4 kapanış süpürmesi: fixture'ın TAMAMI 0 {@code silentDrop} (INV-7 kanıtı; SPEC §6.7 /
 * §7 traceability). saga (calls/compensate) + deployable/Host + visibility/serving süpürmesi +
 * module-ext/param-ext (op/boundary-op/entity-field/type-field/event-payload) — bu task'ın kapsamı.
 * Non-rest serving (fixture'da {@code CreateInvoice @grpc()}) DROP değil, explicit
 * {@code UNSUPPORTED} olmalı (INV-7 unsupported≠drop felsefesi) — {@link BuildReport#clean()} bu
 * yüzden {@code false} kalır ama {@link BuildReport#silentDrops()} BOŞ olmalıdır.
 */
class ZeroDropTest {

    private static Path fixture(String name) {
        Path fromModuleDir = Path.of("../fixtures", name);
        if (Files.exists(fromModuleDir)) {
            return fromModuleDir;
        }
        return Path.of("fixtures", name);
    }

    private static ManifestJson fixtureManifest() {
        return Loader.loadManifest(fixture("manifest.json"));
    }

    private static GenerationModel fixtureGm(ManifestJson manifest) {
        ContractFile contract = Loader.loadContract(fixture("manifest.json"), manifest.contract());
        return GmBuilder.build(manifest, contract);
    }

    private static GenConfig h2Config() {
        return new GenConfig("h2", "inmemory");
    }

    @Test
    void fixtureEmit_hasZeroSilentDrops(@TempDir Path outDir) throws IOException {
        ManifestJson manifest = fixtureManifest();
        GenerationModel gm = fixtureGm(manifest);
        BuildReport report = new BuildReport();

        SpringEmitter.emit(gm, outDir, report, h2Config());
        Completeness.check(manifest, report);

        List<BuildReport.BuildEntry> drops = report.silentDrops();
        String diagnosis = drops.stream()
                .map(e -> e.construct() + " / " + e.id())
                .collect(Collectors.joining("\n"));
        assertTrue(drops.isEmpty(), "silentDrop OLMAMALI (INV-7); düşenler:\n" + diagnosis);
    }

    @Test
    void fixtureEmit_hasAllRequiredPolicyKeys(@TempDir Path outDir) throws IOException {
        ManifestJson manifest = fixtureManifest();
        GenerationModel gm = fixtureGm(manifest);
        BuildReport report = new BuildReport();

        SpringEmitter.emit(gm, outDir, report, h2Config());
        Completeness.check(manifest, report);

        List<String> requiredKeys = List.of(
                "deployment-topology", "saga-orchestration-state", "consistency-mode", "dedup-store",
                "pagination-strategy", "cursor-token", "trigger-wiring", "http-binding", "guard-linkage",
                "source-of-truth", "uncharted-realization", "visibility", "serving-grpc",
                "audit-realization", "metric-realization", "trigger-realization", "http-realization",
                "sensitivity-realization", "crypto-realization", "schema-realization", "deploy-realization",
                "ucparam-realization");
        for (String key : requiredKeys) {
            assertTrue(report.policies().containsKey(key), "policy eksik: " + key);
        }
    }

    @Test
    void grpcServing_isUnsupported_notSilentDrop(@TempDir Path outDir) throws IOException {
        ManifestJson manifest = fixtureManifest();
        GenerationModel gm = fixtureGm(manifest);
        BuildReport report = new BuildReport();

        SpringEmitter.emit(gm, outDir, report, h2Config());
        Completeness.check(manifest, report);

        assertTrue(report.entries().stream().anyMatch(e -> e.construct().equals("serving")
                && e.id().equals("CreateInvoice:grpc")
                && e.status() == BuildReport.ConstructStatus.UNSUPPORTED),
                "CreateInvoice:grpc UNSUPPORTED olmalı (REST-only binding; drop DEĞİL)");
        assertFalse(report.entries().stream().anyMatch(e -> e.construct().equals("serving")
                && e.id().equals("CreateInvoice:grpc")
                && e.status() == BuildReport.ConstructStatus.SILENT_DROP));
    }

    @Test
    void cleanIsFalse_dueToUnsupported_butSilentDropsEmpty(@TempDir Path outDir) throws IOException {
        // Clean≠exit ayrımının kanıtı (INV-7): unsupported var → clean()==false, AMA silentDrops
        // boş — unsupported raporlanmış bir durumdur, sessiz kayıp değildir.
        ManifestJson manifest = fixtureManifest();
        GenerationModel gm = fixtureGm(manifest);
        BuildReport report = new BuildReport();

        SpringEmitter.emit(gm, outDir, report, h2Config());
        Completeness.check(manifest, report);

        assertFalse(report.clean(), "grpc unsupported entry'si var → clean()==false bekleniyor");
        assertTrue(report.silentDrops().isEmpty(), "AMA silentDrops boş olmalı (unsupported≠drop)");
    }
}
