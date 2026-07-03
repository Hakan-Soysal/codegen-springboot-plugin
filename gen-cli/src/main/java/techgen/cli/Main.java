package techgen.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import techgen.core.errors.JoinError;
import techgen.core.errors.LoadError;
import techgen.core.gm.GenerationModel;
import techgen.core.model.ContractFile;
import techgen.core.model.ManifestJson;
import techgen.core.pipeline.GmBuilder;
import techgen.core.pipeline.Loader;
import techgen.core.report.BuildReport;
import techgen.core.report.Completeness;

import techgen.spring.GenConfig;
import techgen.spring.SpringEmitter;

/**
 * gen-cli uçtan-uca akış (SPEC §7; referans {@code docs/referans/gen-dotnet-emisyon-sozlesmesi.md}
 * §6; CoreTemplate1 {@code Gen.Cli/Program.cs} karşılığı, Java'ya uyarlanmış).
 *
 * <p>Akış: {@code load → join/GM → config → emit → Completeness.check → build-report.json yaz →
 * exit}. Exit sözleşmesi (INV-7): {@code 0} = temiz (silentDrop yok), {@code 1} = ≥1 silentDrop,
 * {@code 2} = kullanım/girdi hatası (eksik arg, LoadError, JoinError) — LoadError/JoinError ASLA
 * exit 1'e eşlenmez (drop-exit'i kirletmez). {@code Unsupported} (ör. non-REST serving, whitelist-dışı
 * dbProvider) exit'i ETKİLEMEZ.</p>
 *
 * <p>.NET'ten farkla: arg'lar zorunlu (default-arg davranışı taşınmaz — SPEC §7 ile uyumlu bilinçli
 * sapma).</p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** Test-edilebilir çekirdek — {@code System.exit} YALNIZ {@link #main}'de. */
    static int run(String[] args) {
        if (args.length < 2) {
            System.err.println("kullanım: java -jar gen-cli.jar <manifest.json> <outDir>");
            return 2;
        }
        Path manifestPath = Path.of(args[0]);
        String outDirArg = args[1];
        Path outDir = Path.of(outDirArg);

        try {
            ManifestJson manifest = Loader.loadManifest(manifestPath);
            ContractFile contract = Loader.loadContract(manifestPath, manifest.contract());
            GenerationModel gm = GmBuilder.build(manifest, contract);

            Path manifestDir = manifestPath.toAbsolutePath().normalize().getParent();
            if (manifestDir == null) {
                manifestDir = Path.of(".");
            }
            GenConfig config = GenConfig.load(manifestDir.resolve("gen.config.json"));

            BuildReport report = new BuildReport();
            SpringEmitter.emit(gm, outDir, report, config);
            Completeness.check(manifest, report);
            report.writeTo(outDir.resolve("build-report.json"));

            List<BuildReport.BuildEntry> drops = report.silentDrops();
            System.out.println("emit → " + outDirArg + "  (clean=" + report.clean()
                    + ", constructs=" + report.entries().size() + ", silentDrops=" + drops.size() + ")");
            for (BuildReport.BuildEntry d : drops) {
                System.out.println("  ⚠ SESSİZ DROP: " + d.construct() + " / " + d.id());
            }
            return drops.isEmpty() ? 0 : 1;
        } catch (LoadError | JoinError e) {
            System.err.println("hata: " + e.getMessage());
            return 2;
        } catch (IOException e) {
            System.err.println("I/O hatası: " + e.getMessage());
            return 2;
        }
    }
}
