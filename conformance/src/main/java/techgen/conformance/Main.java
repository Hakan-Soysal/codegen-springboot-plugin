package techgen.conformance;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * BUNDLED CONSOLE CONFORMANCE RUNNER (davranış sözleşmesi §A.4; referans {@code Program.cs} —
 * .NET {@code dotnet Conformance.dll <appDllPath> <specsPath>} karşılığı). Dil-nötr SPEC'leri
 * yükler, {@link SpecRunner} ile üretilmiş app'e karşı koşar, PASS/FAIL/SKIP satırları + özet
 * basar. A3 değişmezi: bu sınıf hiçbir beklenen resultType/kod literal'i GÖMMEZ — assertion
 * SPEC'te ({@link SpecRunner#run}).
 *
 * <p>Kullanım: {@code java -jar conformance.jar <appClasspath> <specsPath>}</p>
 * <ul>
 *   <li>{@code appClasspath} — üretilmiş app'in {@code :}-ayrık classpath'i (Java eşlemesi;
 *   .NET tek dll yerine sınıf yolu listesi — davranış sözleşmesi §A.3 "Java eşlemesi").</li>
 *   <li>{@code specsPath} — tek bir {@code *.json} dosyası (=1 spec) VEYA bir dizin (recursive
 *   {@code *.json}, ordinal-sıralı; her dosya TAM BİR spec — array parser yok).</li>
 * </ul>
 * <p>Exit: 0 — tüm non-skip spec PASS; 1 — ≥1 FAIL; 2 — usage/parse/load hatası.</p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /** Testable çekirdek: exit code döner, I/O akışları enjekte edilir (davranış sözleşmesi §A.4). */
    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 2) {
            err.println("usage: java -jar conformance.jar <appClasspath> <specsPath>");
            err.println("  <appClasspath>  üretilmiş app'in ':'-ayrık classpath'i");
            err.println("  <specsPath>     *.json spec dizini (recursive) VEYA tek bir spec dosyası");
            return 2;
        }

        String appClasspath = args[0];
        String specsPath = args[1];

        List<String> specJsons;
        try {
            specJsons = readSpecJsons(specsPath);
        } catch (Exception ex) {
            err.println("ERROR: specsPath okunamadı: " + specsPath + " (" + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage() + ")");
            return 2;
        }
        if (specJsons.isEmpty()) {
            err.println("ERROR: specsPath altında *.json spec bulunamadı: " + specsPath);
            return 2;
        }

        List<Spec> specs = new ArrayList<>();
        try {
            for (String json : specJsons) {
                specs.add(SpecJson.parse(json));
            }
        } catch (Exception ex) {
            err.println("ERROR: spec parse edilemedi: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return 2;
        }

        List<SpecResult> results = new ArrayList<>();
        try (GeneratedApp app = GeneratedApp.load(appClasspath)) {
            SpecRunner runner = new SpecRunner();
            for (Spec spec : specs) {
                results.add(runner.run(spec, app));
            }
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            err.println("ERROR: app yüklenemedi/koşulamadı '" + appClasspath + "': "
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            return 2;
        }

        int pass = 0;
        int fail = 0;
        int skip = 0;
        for (SpecResult r : results) {
            String tag = switch (r.status()) {
                case PASS -> "PASS";
                case FAIL -> "FAIL";
                case SKIPPED -> "SKIP";
            };
            if (r.isPass()) {
                pass++;
            } else if (r.isFail()) {
                fail++;
            } else {
                skip++;
            }
            out.println("[" + tag + "] " + r.spec().construct() + "/" + r.spec().opId() + ": " + r.detail());
        }
        out.println("conformance: " + pass + " pass, " + fail + " fail, " + skip + " skip");

        return fail == 0 ? 0 : 1;
    }

    /** {@code specsPath} tek dosya → 1 spec metni; dizin → recursive {@code *.json}, ordinal-sıralı. */
    private static List<String> readSpecJsons(String specsPath) throws IOException {
        Path path = Path.of(specsPath);
        if (Files.isRegularFile(path)) {
            return List.of(Files.readString(path));
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                List<Path> jsonFiles = walk
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();
                List<String> out = new ArrayList<>();
                for (Path p : jsonFiles) {
                    out.add(Files.readString(p));
                }
                return out;
            }
        }
        throw new IOException("specsPath dosya veya dizin değil: " + specsPath);
    }
}
