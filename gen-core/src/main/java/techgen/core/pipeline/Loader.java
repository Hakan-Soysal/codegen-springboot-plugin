package techgen.core.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import techgen.core.Json;
import techgen.core.errors.LoadError;
import techgen.core.model.ContractFile;
import techgen.core.model.ManifestJson;

/**
 * Aşama 1 (Load): manifest + opsiyonel operations.json'u tipli modele ayrıştırır
 * (davranış sözleşmesi §4/§8, CoreTemplate1 {@code Pipeline/Loader.cs} karşılığı).
 *
 * <p>Manifest hatası HER ZAMAN fatal ({@link LoadError}). Contract eksik/bozuk ise
 * sessizce {@code null} döner (throw YOK) — hata join aşamasına (GmBuilder, T2.1)
 * devredilir: linked modda {@code manifest.contract != null} iken contract null ise
 * JoinError orada üretilir.</p>
 */
public final class Loader {

    private Loader() {
    }

    /**
     * Manifest'i yükler. Dosya yoksa veya parse edilemezse {@link LoadError} fırlatır.
     */
    public static ManifestJson loadManifest(Path path) {
        if (!Files.exists(path)) {
            throw new LoadError("manifest bulunamadı: " + path);
        }
        try {
            String content = Files.readString(path);
            return Json.parse(content, ManifestJson.class);
        } catch (IOException e) {
            throw new LoadError("manifest ayrıştırılamadı: " + e.getMessage());
        }
    }

    /**
     * Contract'ı (operations.json) yükler. {@code contractPath} null ise (standalone mod)
     * {@code null} döner. Yol {@code manifestPath}'in dizinine göreli çözülür. Dosya yoksa
     * VEYA parse hatası oluşursa {@code null} döner — throw YOK (join aşamasına devredilir).
     */
    public static ContractFile loadContract(Path manifestPath, String contractPath) {
        if (contractPath == null) {
            return null;
        }
        Path dir = manifestPath.getParent();
        if (dir == null) {
            dir = Path.of(".");
        }
        Path resolved = dir.resolve(contractPath).normalize();
        if (!Files.exists(resolved)) {
            return null;
        }
        try {
            String content = Files.readString(resolved);
            return Json.parse(content, ContractFile.class);
        } catch (IOException e) {
            return null;
        }
    }
}
