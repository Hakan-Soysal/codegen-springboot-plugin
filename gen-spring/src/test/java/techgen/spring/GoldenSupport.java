package techgen.spring;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * T6.1 — golden snapshot altyapısı (davranış sözleşmesi §9; referans
 * {@code docs/referans/gen-dotnet-emisyon-sozlesmesi.md} §8; CoreTemplate1
 * {@code CharacterizationTests.cs} {@code Snapshot}/{@code GoldenPath} karşılığı, Java hedefine
 * uyarlanmış).
 *
 * <p>Emit ağacının TÜM dosyalarını (üreteç metadata'sı — {@code provenance.json} /
 * {@code build-report.json} — HARİÇ) {@code relpath\tsha256hex} satırları olarak, {@code /}
 * ayraçlı ve ordinal ({@link String#compareTo}) sıralı döndürür. {@code UPDATE_GOLDEN=1} env
 * değişkeni set'liyse (veya golden dosyası yoksa) snapshot'ı golden dosyasına yazar; aksi halde
 * hiçbir dosya yazmaz — karşılaştırma çağıranın (characterization testi) sorumluluğundadır.</p>
 */
public final class GoldenSupport {

    /** Snapshot'a hiç girmeyen üreteç run-metadata dosyaları (INV-S: run-metadata, golden dışı). */
    private static final Set<String> METADATA = Set.of("provenance.json", "build-report.json");

    private GoldenSupport() {
    }

    /** Golden dosyasının konumu: {@code gen-spring/src/test/resources/golden/emit-snapshot.txt}. */
    public static Path goldenPath() {
        return Path.of("src", "test", "resources", "golden", "emit-snapshot.txt");
    }

    /**
     * {@code root} altındaki emit ağacını {@code relpath\tsha256\n} satırları halinde, {@code /}
     * ayraçlı ve ordinal sıralı döndürür. {@code provenance.json}/{@code build-report.json} hariç.
     */
    public static String snapshot(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            List<String> lines = walk.filter(Files::isRegularFile)
                    .map(f -> root.relativize(f).toString().replace('\\', '/'))
                    .filter(rel -> !METADATA.contains(fileNameOf(rel)))
                    .sorted(Comparator.naturalOrder())
                    .map(rel -> rel + "\t" + sha256Hex(root.resolve(rel)))
                    .toList();
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Golden'ı üretir/karşılaştırır. {@code UPDATE_GOLDEN=1} set'liyse (veya golden yoksa)
     * {@code actual}'ı golden dosyasına yazar ve döner ({@code null} = yazıldı, uyuşmazlık yok).
     * Aksi halde golden'ı okuyup {@code actual} ile karşılaştırır; eşitse {@code null}, farklıysa
     * eklenen/kaybolan satırları içeren insan-okur bir diff mesajı döner.
     */
    public static String compareOrUpdate(String actual) {
        Path golden = goldenPath();
        boolean update = "1".equals(System.getenv("UPDATE_GOLDEN"));
        try {
            if (update || Files.notExists(golden)) {
                Files.createDirectories(golden.getParent());
                Files.writeString(golden, actual, StandardCharsets.UTF_8);
                return null;
            }
            String expected = Files.readString(golden, StandardCharsets.UTF_8).replace("\r\n", "\n");
            String normalizedActual = actual.replace("\r\n", "\n");
            if (expected.equals(normalizedActual)) {
                return null;
            }
            Set<String> expectedLines = Set.of(expected.split("\n", -1));
            Set<String> actualLines = Set.of(normalizedActual.split("\n", -1));
            List<String> added = actualLines.stream().filter(l -> !expectedLines.contains(l) && !l.isEmpty())
                    .sorted().toList();
            List<String> removed = expectedLines.stream().filter(l -> !actualLines.contains(l) && !l.isEmpty())
                    .sorted().toList();
            return "Emit ağacı golden'dan saptı (kasıtlıysa UPDATE_GOLDEN=1 ile yenile).\n"
                    + "YENİ/DEĞİŞEN:\n  " + String.join("\n  ", added) + "\n"
                    + "KAYIP/ESKİ:\n  " + String.join("\n  ", removed);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String fileNameOf(String relPath) {
        int idx = relPath.lastIndexOf('/');
        return idx < 0 ? relPath : relPath.substring(idx + 1);
    }

    private static String sha256Hex(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algoritması bulunamadı (JVM garantisi ihlali)", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
