package techgen.spring;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

import techgen.core.Json;

/**
 * {@code provenance.json} okuma/yazma (davranış sözleşmesi §5, CoreTemplate1
 * {@code Provenance.cs} karşılığı).
 *
 * <p>Yalnız IO sorumluluğu taşır: hangi dosyaların {@code Generated} sınıfına ait olduğuna
 * karar vermek (filtreleme) ve sha256 hesaplamak çağıranın (emit yazım altyapısı, T3.1)
 * işidir — bu sınıf yalnız verilen {@link Provenance}'ı atomik şekilde diske yazar ve okur.</p>
 */
public final class ProvenanceIo {

    /** provenance dosyasının sabit adı, outDir kökünde. */
    public static final String FILE_NAME = "provenance.json";

    private ProvenanceIo() {
    }

    /**
     * {@code provenance} nesnesini {@code outDir/provenance.json}'a atomik şekilde yazar.
     * Girdi sırasından bağımsız olarak {@code files} alanı {@code path}'e göre ordinal
     * sıralanmış şekilde yazılır. İçerik UTF-8; sonunda tek {@code \n}.
     *
     * <p>Atomiklik: aynı dizinde geçici dosyaya yazılır, ardından
     * {@link Files#move(Path, Path, java.nio.file.CopyOption...)} ile
     * {@code ATOMIC_MOVE}+{@code REPLACE_EXISTING} kullanılarak hedefe taşınır — okuyucular
     * asla yarım yazılmış içerik görmez.</p>
     */
    public static void write(Path outDir, Provenance provenance) throws IOException {
        List<ProvenanceEntry> sorted = provenance.files().stream()
                .sorted(Comparator.comparing(ProvenanceEntry::path))
                .toList();
        Provenance ordered = new Provenance(provenance.generator(), provenance.version(), sorted);
        String content = Json.mapper().writeValueAsString(ordered) + "\n";

        Files.createDirectories(outDir);
        Path target = outDir.resolve(FILE_NAME);
        Path tmp = Files.createTempFile(outDir, "provenance", ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    /**
     * {@code outDir/provenance.json}'u okumayı dener. Dosya yoksa VEYA parse edilemiyorsa
     * (bozuk JSON, beklenmeyen şema) {@code null} döner — throw YOK. Çağıran (prune mantığı)
     * {@code null} durumunda pruning'i atlamalıdır (canlı dosya yanlışlıkla silinmesin).
     */
    public static Provenance tryRead(Path outDir) {
        Path target = outDir.resolve(FILE_NAME);
        if (!Files.exists(target)) {
            return null;
        }
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            return Json.mapper().readValue(content, Provenance.class);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
