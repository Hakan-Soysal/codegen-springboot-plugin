package techgen.spring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import techgen.core.Json;
import techgen.core.errors.LoadError;

/**
 * {@code gen.config.json} (dile-özel, SPEC §6.6) — şema birebir iki alan (referans
 * {@code docs/referans/gen-dotnet-emisyon-sozlesmesi.md} §4, CoreTemplate1 {@code GenConfig.cs}
 * karşılığı).
 *
 * <p>{@code dbProvider} whitelist: {@code postgres}/{@code sqlserver}/{@code h2}/{@code inmemory}
 * (→H2 in-memory). {@code null} → seam yorumu (rapor entry'si YOK); whitelist-dışı →
 * {@code Unsupported("dbProvider", ...)} + seam davranışı. {@code testDbProvider} default
 * {@code inmemory} — bu default {@code load} tarafından DEĞİL, tüketen tarafından (T7.1) uygulanır
 * (CoreTemplate1 {@code TestProvider} paritesi: {@code config?.TestDbProvider ?? "inmemory"}).</p>
 */
public record GenConfig(String dbProvider, String testDbProvider) {

    /**
     * {@code gen.config.json}'u manifest dizininden yükler. Dosya yoksa {@code new GenConfig(null,
     * null)} döner (throw YOK — opsiyonel dosya, mevcut/seam davranışı). Var ise merkezî {@link Json}
     * yapılandırmasıyla (camelCase, bilinmeyen-alan toleranslı) parse edilir; bozuksa {@link LoadError}
     * (kullanım/girdi hatası — çağıran exit 2'ye eşler).
     */
    public static GenConfig load(Path genConfigJson) {
        if (!Files.exists(genConfigJson)) {
            return new GenConfig(null, null);
        }
        try {
            String content = Files.readString(genConfigJson);
            return Json.parse(content, GenConfig.class);
        } catch (IOException e) {
            throw new LoadError("gen.config.json ayrıştırılamadı: " + e.getMessage());
        }
    }
}
