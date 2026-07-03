package techgen.conformance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import techgen.core.gm.GenerationModel;
import techgen.core.model.ContractFile;
import techgen.core.model.ManifestJson;
import techgen.core.pipeline.GmBuilder;
import techgen.core.pipeline.Loader;
import techgen.core.report.BuildReport;
import techgen.spring.GenConfig;
import techgen.spring.SpringEmitter;

/**
 * TEST-FIXTURE SCAFFOLDING (production DEĞİL; davranış sözleşmesi §A.5; referans
 * {@code conformance-adapter/Tests/AppFixture.cs}). Fixture manifest'inden gerçek bir app emit
 * eder, {@code CreateInvoice} seam'ini THROWAWAY bir impl ile doldurur (M5 değil — yalnız
 * adapter'ı koşturmak için), gerçek {@code mvn compile} ile build eder ve
 * {@link GeneratedApp#load}'un beklediği {@code :}-ayrık classpath'i döndürür.
 *
 * <p>{@link SpecRunner}/{@link GeneratedApp} bu scaffold'a BAĞIMLI DEĞİL — zaten-build edilmiş
 * classpath + spec listesi tüketirler (referans notu: "Runner scaffold'a bağımlı DEĞİL").</p>
 */
final class AppFixture {

    private AppFixture() {
    }

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

    /** app'i emit eder (seam'ler boş = UnsupportedOperationException); h2 in-memory config. */
    static void emit(Path dir) throws IOException {
        SpringEmitter.emit(fixtureGm(), dir, new BuildReport(), new GenConfig("h2", "inmemory"));
    }

    /**
     * {@code CreateInvoice} seam'ini doldurur (throwaway). {@code executeBody} = {@code execute}
     * gövdesi (return ifadeleri). MARKER-convention: doldurulmuş seam {@code "doldurulacak"}
     * substring'i İÇERMEZ (referans {@code AppFixture.cs.FillCreateInvoiceSeam}).
     */
    static void fillCreateInvoiceSeam(Path dir, String executeBody) throws IOException {
        Path handler = dir.resolve("src/main/java/app/billing/createinvoice/CreateInvoiceHandler.java");
        String content = """
                package app.billing.createinvoice;

                import app.Result;
                import app.billing.InvoiceRepository;
                import app.billing.Invoice;
                import app.IdempotencyStore;
                import app.EventBus;
                import app.boundary.PaymentGateway;
                import app.NotValid;
                import app.Success;
                import app.ServerError;

                import java.math.BigDecimal;
                import java.util.Map;
                import java.util.Set;
                import java.util.concurrent.ConcurrentHashMap;

                public class CreateInvoiceHandler extends CreateInvoiceHandlerBase {

                    // throwaway test-fixture impl (T8.2 acceptance; production DEĞİL). Dedup için instance-state.
                    private final Set<String> seen = ConcurrentHashMap.newKeySet();

                    public CreateInvoiceHandler(InvoiceRepository invoiceRepository, IdempotencyStore idempotencyStore, EventBus eventBus, PaymentGateway paymentGateway) {
                        super(invoiceRepository, idempotencyStore, eventBus, paymentGateway);
                    }

                    @Override
                    public Result<Invoice> execute(CreateInvoiceCommand request) {
                %s
                    }
                }
                """.formatted(executeBody);
        Files.writeString(handler, content, StandardCharsets.UTF_8);
    }

    /** app'i gerçek {@code mvn compile} ile derler. exit≠0 → exception (build kanıtı). */
    static void build(Path dir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("mvn", "-q", "-f", dir.resolve("pom.xml").toString(), "compile");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("Üretilen app build edilmedi (exit=" + exit + "):\n" + output);
        }
    }

    /**
     * app'in çalışma zamanı classpath'ini ({@code target/classes} + tüm bağımlılıklar) döner —
     * {@link GeneratedApp#load}'un beklediği {@code :}-ayrık biçimde ({@code maven-dependency-plugin}
     * ile hesaplanır; Java eşlemesi — .NET tek {@code App.dll} yerine sınıf yolu listesi).
     */
    static String classpath(Path dir) throws Exception {
        Path cpFile = dir.resolve("cp.txt");
        ProcessBuilder pb = new ProcessBuilder("mvn", "-q", "-f", dir.resolve("pom.xml").toString(),
                "dependency:build-classpath", "-Dmdep.outputFile=" + cpFile);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        if (exit != 0 || !Files.exists(cpFile)) {
            throw new IllegalStateException("classpath hesaplanamadı (exit=" + exit + "):\n" + output);
        }
        String deps = Files.readString(cpFile).strip();
        return dir.resolve("target/classes").toString() + ":" + deps;
    }

    /** {@link #build}+{@link #classpath}'i tek adımda yapar → {@link GeneratedApp#load} için hazır classpath. */
    static String buildAndClasspath(Path dir) throws Exception {
        build(dir);
        return classpath(dir);
    }
}
