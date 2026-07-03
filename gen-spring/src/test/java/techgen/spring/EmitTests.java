package techgen.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import techgen.core.gm.GenerationModel;
import techgen.core.model.ContractFile;
import techgen.core.model.ManifestJson;
import techgen.core.pipeline.GmBuilder;
import techgen.core.pipeline.Loader;
import techgen.core.report.BuildReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T6.2 — {@code docs/referans/conformance-testler-skill-sozlesmesi.md} §B {@code EmitTests} portu
 * (CoreTemplate1 {@code tests/Gen.Tests/EmitTests.cs}). Bu sınıf yalnız o dosyanın Java
 * gen-spring'de HENÜZ dolaysız test edilmemiş kısmına odaklanır: {@code dbProvider} yapılandırma
 * yolları (SPEC §6.6; {@link SpringEmitter} {@code providerDependencyXml}/{@code applicationYml}).
 *
 * <p>C# {@code EmitTests.cs}'in kalan 8 metodu (app compile + 0-SilentDrop, request record/partial
 * handler, EF/JPA entity+RowVersion+DbContext, event/bus/auth, pagination Page+cursor/size,
 * boundary+idempotency+policies, error kataloğu+throws fabrikası, ResultHttp override hook, Logic
 * regen-korunumu, byte-determinizm) zaten gen-spring'in T3.x/T4.x/T6.1 test dosyalarında (ör.
 * {@code ZeroDropTest}, {@code GeneratedAppCompileTest}, {@code JpaEmitTest}, {@code ResultTypesEmitTest},
 * {@code IdempotencyPaginationEmitTest}, {@code BoundaryEmitTest}, {@code CharacterizationTest})
 * fixture üzerinden birebir kapsanmıştır — kapsam matrisi task raporunda listelenmiştir (mükerrer
 * dosya/test YARATILMADI).</p>
 */
class EmitTests {

    private static Path fixture(String name) {
        Path fromModuleDir = Path.of("../fixtures", name);
        if (Files.exists(fromModuleDir)) {
            return fromModuleDir;
        }
        return Path.of("fixtures", name);
    }

    private static GenerationModel fixtureGm() {
        Path manifestPath = fixture("manifest.json");
        ManifestJson manifest = Loader.loadManifest(manifestPath);
        ContractFile contract = Loader.loadContract(manifestPath, manifest.contract());
        return GmBuilder.build(manifest, contract);
    }

    private static String read(Path outDir, String relPath) throws IOException {
        return Files.readString(outDir.resolve(relPath), StandardCharsets.UTF_8);
    }

    private static final String PARENT_POM = "gen/parent/pom.xml";
    private static final String APPLICATION_YML = "src/main/resources/application.yml";

    // ── null config → seam yorumu, rapor entry'si YOK (EmitTests.cs Null_config_keeps_empty_provider_seam) ──

    @Test
    void nullConfig_keepsProviderSeamComment_noDbProviderReportEntry(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, null);

        String pom = read(outDir, PARENT_POM);
        assertTrue(pom.contains("<!-- dbProvider tanımlı değil: provider seam"),
                "config=null → provider seam yorumu eksik");
        String yml = read(outDir, APPLICATION_YML);
        assertTrue(yml.contains("# dbProvider tanımlı değil: datasource seam"),
                "config=null → application.yml seam yorumu eksik");
        assertFalse(report.entries().stream().anyMatch(e -> e.construct().equals("dbProvider")),
                "config=null → dbProvider rapor entry'si OLMAMALI");
    }

    // ── GenConfig(null,null) aynı seam davranışı (config nesnesi var ama dbProvider alanı null) ──

    @Test
    void genConfigWithNullDbProvider_keepsSameSeamBehaviorAsNullConfig(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, new GenConfig(null, null));

        String pom = read(outDir, PARENT_POM);
        assertTrue(pom.contains("<!-- dbProvider tanımlı değil: provider seam"));
        assertFalse(report.entries().stream().anyMatch(e -> e.construct().equals("dbProvider")));
    }

    // ── h2 whitelist → realized + h2 driver bağımlılığı (EmitTests.cs DbProvider_config_adds_provider_package_to_props) ──

    @Test
    void dbProviderH2_isRealized_andAddsH2DriverDependencyAndDatasourceComment(@TempDir Path outDir)
            throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, new GenConfig("h2", "inmemory"));

        assertTrue(report.covers("dbProvider", "h2"), "dbProvider=h2 realized olmalı");
        String pom = read(outDir, PARENT_POM);
        assertTrue(pom.contains("<groupId>com.h2database</groupId>"));
        String yml = read(outDir, APPLICATION_YML);
        assertTrue(yml.contains("# dbProvider=h2 -> spring.datasource.url=jdbc:h2:mem:app"));
    }

    // ── inmemory whitelist → h2 driver (ayrı id, aynı bağımlılık) ──

    @Test
    void dbProviderInmemory_isRealized_andAddsH2DriverDependency(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, new GenConfig("inmemory", null));

        assertTrue(report.covers("dbProvider", "inmemory"));
        assertFalse(report.covers("dbProvider", "h2"), "id 'inmemory' olarak kayıtlı olmalı, 'h2' değil");
        String pom = read(outDir, PARENT_POM);
        assertTrue(pom.contains("<groupId>com.h2database</groupId>"));
    }

    // ── postgres whitelist → realized + postgres driver bağımlılığı + datasource-by-reference yorumu ──

    @Test
    void dbProviderPostgres_isRealized_andAddsPostgresDriverDependency(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, new GenConfig("postgres", null));

        assertTrue(report.covers("dbProvider", "postgres"));
        String pom = read(outDir, PARENT_POM);
        assertTrue(pom.contains("<groupId>org.postgresql</groupId>"));
        assertTrue(pom.contains("<artifactId>postgresql</artifactId>"));
        String yml = read(outDir, APPLICATION_YML);
        assertTrue(yml.contains("# dbProvider=postgres -> spring.datasource.url=jdbc:postgresql://<host>:5432/<db>"));
    }

    // ── sqlserver whitelist → realized + mssql driver bağımlılığı ──

    @Test
    void dbProviderSqlserver_isRealized_andAddsMssqlDriverDependency(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, new GenConfig("sqlserver", null));

        assertTrue(report.covers("dbProvider", "sqlserver"));
        String pom = read(outDir, PARENT_POM);
        assertTrue(pom.contains("<groupId>com.microsoft.sqlserver</groupId>"));
        assertTrue(pom.contains("<artifactId>mssql-jdbc</artifactId>"));
    }

    // ── whitelist-dışı → Unsupported + seam fallback (EmitTests.cs Unknown_provider_recorded_unsupported_with_seam_fallback) ──

    @Test
    void dbProviderUnknown_isUnsupported_withSeamFallbackComment_inParentPom(@TempDir Path outDir)
            throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, new GenConfig("mongodb", null));

        assertTrue(report.entries().stream().anyMatch(e -> e.construct().equals("dbProvider")
                && e.id().equals("mongodb") && e.status() == BuildReport.ConstructStatus.UNSUPPORTED),
                "whitelist-dışı dbProvider UNSUPPORTED olmalı");
        String pom = read(outDir, PARENT_POM);
        assertTrue(pom.contains("<!-- dbProvider 'mongodb' whitelist dışı: driver eklenmedi -->"),
                "seam fallback yorumu eksik");
        // Unsupported clean()'i false yapar ama SilentDrop DEĞİLDİR (dbProvider census construct'ı değil).
        assertFalse(report.silentDrops().stream().anyMatch(e -> e.construct().equals("dbProvider")));
        assertEquals(1, report.entries().stream()
                .filter(e -> e.construct().equals("dbProvider")).count(),
                "dbProvider TAM 1 kez raporlanmalı");
    }

    // ── dbProvider whitelist entry'si TAM 1 kez raporlanır (h2 tarafında da mongodb ile simetrik doğrulama) ──

    @Test
    void dbProviderH2_reportedExactlyOnce_perEmit(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, new GenConfig("h2", "inmemory"));

        assertEquals(1, report.entries().stream().filter(e -> e.construct().equals("dbProvider")).count(),
                "dbProvider TAM 1 kez raporlanmalı");
        assertEquals(BuildReport.ConstructStatus.REALIZED, report.entries().stream()
                .filter(e -> e.construct().equals("dbProvider")).findFirst().orElseThrow().status());
    }
}
