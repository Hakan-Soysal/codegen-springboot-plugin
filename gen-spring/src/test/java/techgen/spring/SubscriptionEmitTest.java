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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4.2 — EventBus + emits + Subscription consumer + seam (SPEC M4; referans
 * {@code docs/referans/gen-dotnet-emisyon-sozlesmesi.md} §1 Subscriptions.g.cs (`:1348-1398`) + §2
 * marker; CoreTemplate1 {@code DotnetEmitter.cs} `:39-48`). Fixture canlı örnek: CreateInvoice
 * (Billing, emits=["InvoiceCreated"]) → subscription → WriteAuditLog (Ops) consumer.
 */
class SubscriptionEmitTest {

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

    // ── Pozitif — consumer doğru modülde (Ops/WriteAuditLog slice'ı) ────────────────────────────────

    @Test
    void consumerBase_isEmittedInOpsWriteAuditLogSlice(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        Path base = outDir.resolve("gen/java/app/ops/writeauditlog/InvoiceCreatedToWriteAuditLogConsumerBase.java");
        assertTrue(Files.exists(base), "consumer base CONSUMER op'un slice'ında (app/ops/writeauditlog/) olmalı");

        String content = Files.readString(base, StandardCharsets.UTF_8);
        assertTrue(content.contains("package app.ops.writeauditlog;"));
        assertTrue(content.contains("import app.billing.InvoiceCreated;"), "event Billing modülünden import edilmeli");
        assertTrue(content.contains("public abstract class InvoiceCreatedToWriteAuditLogConsumerBase {"));
        assertTrue(content.contains("protected final WriteAuditLogHandler handler;"));
        assertTrue(content.contains("protected InvoiceCreatedToWriteAuditLogConsumerBase(WriteAuditLogHandler handler) {"));
        assertTrue(content.contains("public abstract void handle(InvoiceCreated event);"),
                "handle imzası tipli {Event} olmalı, Object DEĞİL");
    }

    // ── Negatif — consumer Billing'de (event'in modülünde) YOK ──────────────────────────────────────

    @Test
    void consumerBase_isNotEmittedUnderBillingModule(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        Path billingDir = outDir.resolve("gen/java/app/billing");
        assertTrue(Files.exists(billingDir), "billing modül dizini var olmalı (diğer op'lar için)");
        boolean foundInBilling;
        try (Stream<Path> walk = Files.walk(billingDir)) {
            foundInBilling = walk.filter(p -> p.toString().endsWith(".java"))
                    .anyMatch(SubscriptionEmitTest::containsConsumerName);
        }
        assertFalse(foundInBilling,
                "consumer sınıfı event'in modülüne (Billing) DEĞİL, consumer op'un modülüne (Ops) emit edilmeli");

        assertFalse(Files.exists(
                outDir.resolve("gen/java/app/billing/writeauditlog/InvoiceCreatedToWriteAuditLogConsumerBase.java")));
    }

    private static boolean containsConsumerName(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8).contains("InvoiceCreatedToWriteAuditLogConsumer");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    // ── Human consumer seam: marker'lı, @Component YOK, ikinci emit ezmiyor ─────────────────────────

    @Test
    void humanConsumer_hasExactMarker_andExtendsBase_noComponentAnnotation(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "src/main/java/app/ops/writeauditlog/InvoiceCreatedToWriteAuditLogConsumer.java");
        assertTrue(content.contains("public class InvoiceCreatedToWriteAuditLogConsumer "
                + "extends InvoiceCreatedToWriteAuditLogConsumerBase {"));
        assertTrue(content.contains(
                "throw new UnsupportedOperationException(\"InvoiceCreatedToWriteAuditLogConsumer.handle: doldurulacak\");"),
                "marker birebir SPEC §6.2 şablonuna uymalı");
        assertTrue(content.contains("@Override"));
        assertTrue(content.contains("public void handle(InvoiceCreated event) {"));
        assertFalse(content.contains("@Component"), "human consumer'a @Component KONMAZ — kayıt Subscriptions'ta");
    }

    @Test
    void humanConsumer_writeIfAbsent_doesNotOverwriteOnSecondEmit(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());
        Path humanFile = outDir.resolve("src/main/java/app/ops/writeauditlog/InvoiceCreatedToWriteAuditLogConsumer.java");
        Files.writeString(humanFile, "// elle bozuldu", StandardCharsets.UTF_8);

        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        assertEquals("// elle bozuldu", Files.readString(humanFile, StandardCharsets.UTF_8),
                "human consumer seam ikinci emit'te EZİLMEMELİ (writeIfAbsent)");
    }

    // ── emits → HandlerBase EventBus alanı: CreateInvoice VAR, GetInvoice YOK ───────────────────────

    @Test
    void createInvoiceHandlerBase_hasEventBusField(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceHandlerBase.java");
        assertTrue(content.contains("import app.EventBus;"));
        assertTrue(content.contains("protected final EventBus eventBus;"));
        assertTrue(content.contains("EventBus eventBus)"), "ctor param eksik");
        assertTrue(content.contains("emits: InvoiceCreated"), "Javadoc/yorum emit listesini içermeli");
    }

    @Test
    void getInvoiceHandlerBase_hasNoEventBusField(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String content = read(outDir, "gen/java/app/billing/getinvoice/GetInvoiceHandlerBase.java");
        assertFalse(content.contains("EventBus"), "GetInvoice emits=[] — EventBus alanı OLMAMALI");
    }

    // ── Ctor-senkron SIRA assert'i (M4 kuralı): HandlerBase == humanHandler super-args == Wiring bean args ──

    private static List<String> extractParamNames(String ctorSignatureLine) {
        Matcher m = Pattern.compile("\\(([^)]*)\\)").matcher(ctorSignatureLine);
        if (!m.find()) {
            throw new IllegalArgumentException("ctor imzası bulunamadı: " + ctorSignatureLine);
        }
        String inner = m.group(1).trim();
        if (inner.isEmpty()) {
            return List.of();
        }
        return Stream.of(inner.split(",\\s*"))
                .map(p -> p.contains(" ") ? p.substring(p.lastIndexOf(' ') + 1) : p)
                .toList();
    }

    private static String findLineContaining(String content, String needle) {
        return content.lines().filter(l -> l.contains(needle)).findFirst()
                .orElseThrow(() -> new AssertionError("satır bulunamadı: " + needle + "\n---\n" + content));
    }

    @Test
    void ctorOrder_handlerBase_humanHandler_wiring_allMatch_reposThenIdempotentThenEventBus(@TempDir Path outDir)
            throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String handlerBase = read(outDir, "gen/java/app/billing/createinvoice/CreateInvoiceHandlerBase.java");
        String handlerBaseCtorLine = findLineContaining(handlerBase, "protected CreateInvoiceHandlerBase(");
        List<String> handlerBaseParams = extractParamNames(handlerBaseCtorLine);

        String humanHandler = read(outDir, "src/main/java/app/billing/createinvoice/CreateInvoiceHandler.java");
        String humanCtorLine = findLineContaining(humanHandler, "public CreateInvoiceHandler(");
        List<String> humanCtorParams = extractParamNames(humanCtorLine);
        String superLine = findLineContaining(humanHandler, "super(");
        List<String> superArgs = extractParamNames(superLine.replace("super(", "x("));

        String wiring = read(outDir, "gen/java/app/billing/BillingWiring.java");
        String wiringBeanLine = findLineContaining(wiring, "public CreateInvoiceHandler createInvoiceHandler(");
        List<String> wiringParams = extractParamNames(wiringBeanLine);
        String wiringReturnLine = findLineContaining(wiring, "return new CreateInvoiceHandler(");
        List<String> wiringArgs = extractParamNames(wiringReturnLine.replace("return new CreateInvoiceHandler(",
                "x("));

        List<String> expected = List.of("invoiceRepository", "idempotencyStore", "eventBus");
        assertEquals(expected, handlerBaseParams, "HandlerBase ctor sırası: repos -> idempotent -> events");
        assertEquals(expected, humanCtorParams, "human Handler ctor param sırası HandlerBase ile AYNI olmalı");
        assertEquals(expected, superArgs, "human Handler super() arg sırası HandlerBase ile AYNI olmalı");
        assertEquals(expected, wiringParams, "Wiring bean param sırası HandlerBase ile AYNI olmalı");
        assertEquals(expected, wiringArgs, "Wiring bean return-arg sırası HandlerBase ile AYNI olmalı");
    }

    // ── Kök Subscriptions kaydı: Bootstrap'tan @Import ediliyor ─────────────────────────────────────

    @Test
    void subscriptionsRoot_isEmitted_andImportedFromBootstrap(@TempDir Path outDir) throws IOException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());

        String subs = read(outDir, "gen/java/app/Subscriptions.java");
        assertTrue(subs.contains("@Configuration"));
        assertTrue(subs.contains("@Bean"));
        assertTrue(subs.contains(
                "public InvoiceCreatedToWriteAuditLogConsumer invoiceCreatedToWriteAuditLogConsumer("
                        + "WriteAuditLogHandler handler) {"));
        assertTrue(subs.contains("return new InvoiceCreatedToWriteAuditLogConsumer(handler);"));
        assertTrue(subs.contains("import app.ops.writeauditlog.WriteAuditLogHandler;"));
        assertTrue(subs.contains("import app.ops.writeauditlog.InvoiceCreatedToWriteAuditLogConsumer;"));
        assertFalse(subs.contains("@ComponentScan"), "component-scan YASAK (SPEC §12/4 tam-açık kayıt)");

        String bootstrap = read(outDir, "gen/java/app/GeneratedBootstrap.java");
        assertTrue(bootstrap.contains("Subscriptions.class"), "Bootstrap Subscriptions'ı @Import etmeli");
    }

    // ── build-report realized çağrıları ──────────────────────────────────────────────────────────────

    @Test
    void buildReport_realizesEmitsAndSubscription(@TempDir Path outDir) throws IOException {
        BuildReport report = new BuildReport();
        SpringEmitter.emit(fixtureGm(), outDir, report, h2Config());

        assertTrue(report.entries().stream()
                        .anyMatch(e -> e.construct().equals("emits") && e.id().equals("CreateInvoice->InvoiceCreated")
                                && e.status() == BuildReport.ConstructStatus.REALIZED),
                "(\"emits\",\"CreateInvoice->InvoiceCreated\") realized olmalı");
        assertTrue(report.entries().stream()
                        .anyMatch(e -> e.construct().equals("subscription") && e.id().equals("InvoiceCreated")
                                && e.status() == BuildReport.ConstructStatus.REALIZED),
                "(\"subscription\",\"InvoiceCreated\") realized olmalı");
    }

    // ── E2E: üretilen app gerçek mvn compile (DoD) ──────────────────────────────────────────────────

    @org.junit.jupiter.api.Tag("e2e")
    @Test
    void generatedApp_realMvnCompile_exitsZero_afterT42(@TempDir Path outDir) throws IOException, InterruptedException {
        SpringEmitter.emit(fixtureGm(), outDir, new BuildReport(), h2Config());
        Path humanPom = outDir.resolve("pom.xml");

        ProcessBuilder pb = new ProcessBuilder("mvn", "-q", "-f", humanPom.toString(), "compile");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, "mvn compile exit 0 bekleniyor; çıktı:\n" + output);
    }
}
