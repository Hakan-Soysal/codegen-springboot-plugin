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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3.4 — Entity→JPA + repository + PersistenceConfig emisyon testleri (davranış sözleşmesi §6.3
 * satırı "AppDbContext.g.cs"/"Entities.g.cs"; referans {@code docs/referans/gen-dotnet-emisyon-sozlesmesi.md}
 * §1 (`:755-787`, `:1189-1212`) + §7 (entity/concurrency/sourceOfTruth satırları); fixture entity'leri
 * {@code fixtures/manifest.json}'daki Invoice (Billing, concurrency=optimistic) ve AuditLog (Ops,
 * concurrency=optimistic, invoiceRef alanı sourceOfTruth=Billing.Invoice) üzerinden doğrulanır.
 */
class JpaEmitTest {

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

    private static GenConfig h2Config() {
        return new GenConfig("h2", "inmemory");
    }

    private static String read(Path outDir, String relPath) throws IOException {
        return Files.readString(outDir.resolve(relPath), StandardCharsets.UTF_8);
    }

    // ── Step 5.1 — Invoice entity: @Entity/@Table/@Id/@Version/@Enumerated + alan tipleri ──────

    @Test
    void invoiceEntity_hasEntityTableIdVersionAndFieldTypes(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/Invoice.java");
        assertTrue(content.contains("package app.billing;"), "paket adı yanlış");
        assertTrue(content.contains("@Entity"), "@Entity eksik");
        assertTrue(content.contains("@Table(name = \"Invoice\")"), "@Table(name=...) eksik/yanlış");
        assertTrue(content.contains("import jakarta.persistence.Id;"), "Id import eksik");
        assertTrue(content.contains("@Id\n    private String id;"), "@Id alanı id üstünde olmalı");
        assertTrue(content.contains("import jakarta.persistence.Version;"), "Version import eksik");
        assertTrue(content.contains("@Version\n    private long version;"), "@Version alanı eksik/yanlış");
        assertTrue(content.contains("import java.math.BigDecimal;"), "BigDecimal import eksik");
        assertTrue(content.contains("private BigDecimal amount;"), "amount alanı BigDecimal olmalı");
        assertTrue(content.contains("import jakarta.persistence.Enumerated;")
                        && content.contains("import jakarta.persistence.EnumType;"),
                "Enumerated/EnumType import eksik");
        assertTrue(content.contains("@Enumerated(EnumType.STRING)\n    private InvoiceStatus status;"),
                "status alanı @Enumerated(EnumType.STRING) ile işaretli InvoiceStatus olmalı");
        assertTrue(content.contains("import java.time.Instant;"), "Instant import eksik");
        assertTrue(content.contains("private Instant createdAt;"), "createdAt alanı Instant olmalı");
    }

    // ── Step 5.1 — mutable sınıf (record DEĞİL) + getter/setter ────────────────────────────────

    @Test
    void invoiceEntity_isMutableClassNotRecord_withGettersAndSetters(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/Invoice.java");
        assertTrue(content.contains("public class Invoice {"), "JPA mutable sınıf olmalı");
        assertFalse(content.contains("record Invoice"), "entity record OLMAMALI — JPA mutable sınıf ister");
        assertTrue(content.contains("public BigDecimal getAmount() {"), "amount getter eksik");
        assertTrue(content.contains("public void setAmount(BigDecimal amount) {"), "amount setter eksik");
        assertTrue(content.contains("public long getVersion() {") && content.contains("public void setVersion(long version) {"),
                "@Version alanı setter'lı olmalı (anti-pattern §8)");
    }

    // ── Step 5.1 — AuditLog: sourceOfTruth yorumu + navigasyon AÇILMAZ (pozitif+negatif) ────────

    @Test
    void auditLogEntity_sourceOfTruthCommentPresent_andNoNavigationFieldOrRelationAnnotation(@TempDir Path outDir)
            throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/ops/AuditLog.java");
        assertTrue(content.contains(
                "// sourceOfTruth: Billing.Invoice — cross-module FK; navigasyon AÇILMAZ"),
                "sourceOfTruth yorumu eksik/yanlış");
        assertTrue(content.contains("private String invoiceRef;"), "invoiceRef String ID olarak kalmalı");

        // negatif — navigasyon AÇILMAZ: ne Invoice tipinde alan ne de ilişki anotasyonu var.
        assertFalse(content.contains("private Invoice "),
                "AuditLog içinde Invoice tipinde navigasyon alanı OLMAMALI");
        assertFalse(content.contains("@ManyToOne"), "@ManyToOne OLMAMALI (navigasyon açılmaz kuralı)");
        assertFalse(content.contains("@OneToOne"), "@OneToOne OLMAMALI (navigasyon açılmaz kuralı)");
        assertFalse(content.contains("@OneToMany"), "@OneToMany OLMAMALI (navigasyon açılmaz kuralı)");
        assertFalse(content.contains("@ManyToMany"), "@ManyToMany OLMAMALI (navigasyon açılmaz kuralı)");
        assertFalse(content.contains("@JoinColumn"), "@JoinColumn OLMAMALI (navigasyon açılmaz kuralı)");
    }

    // ── Step 5.2 — Repository (entity başına) ───────────────────────────────────────────────────

    @Test
    void repositories_extendJpaRepositoryForBothEntities(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String invoiceRepo = read(outDir, "gen/java/app/billing/InvoiceRepository.java");
        assertTrue(invoiceRepo.contains("package app.billing;"));
        assertTrue(invoiceRepo.contains("import org.springframework.data.jpa.repository.JpaRepository;"));
        assertTrue(invoiceRepo.contains("public interface InvoiceRepository extends JpaRepository<Invoice, String> {"));

        String auditLogRepo = read(outDir, "gen/java/app/ops/AuditLogRepository.java");
        assertTrue(auditLogRepo.contains("package app.ops;"));
        assertTrue(auditLogRepo.contains(
                "public interface AuditLogRepository extends JpaRepository<AuditLog, String> {"));
    }

    // ── Step 5.2 — PersistenceConfig + Bootstrap @Import ────────────────────────────────────────

    @Test
    void persistenceConfig_emittedWithEnableJpaRepositoriesAndEntityScan_andBootstrapImportsIt(
            @TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/PersistenceConfig.java");
        assertTrue(content.contains("package app;"));
        assertTrue(content.contains("@Configuration"));
        assertTrue(content.contains("@EnableJpaRepositories(basePackages = \"app\")"));
        assertTrue(content.contains("@EntityScan(basePackages = \"app\")"));
        assertTrue(content.contains("public class PersistenceConfig {"));

        String bootstrap = read(outDir, "gen/java/app/GeneratedBootstrap.java");
        assertTrue(bootstrap.contains("PersistenceConfig.class"), "Bootstrap PersistenceConfig'i @Import etmeli");
    }

    // ── Step 5.3 — build-report realized çağrıları ──────────────────────────────────────────────

    @Test
    void buildReport_realizesEntityConcurrencyAndSourceOfTruth(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, h2Config());

        assertRealized(report, "entity", "Invoice");
        assertRealized(report, "concurrency", "Invoice");
        assertRealized(report, "concurrency", "AuditLog");
        assertRealized(report, "sourceOfTruth", "AuditLog.invoiceRef");
    }

    private static void assertRealized(BuildReport report, String construct, String id) {
        assertTrue(report.entries().stream().anyMatch(e -> e.construct().equals(construct) && e.id().equals(id)
                        && e.status() == BuildReport.ConstructStatus.REALIZED),
                "(%s,%s) realized olarak raporlanmalı".formatted(construct, id));
    }
}
