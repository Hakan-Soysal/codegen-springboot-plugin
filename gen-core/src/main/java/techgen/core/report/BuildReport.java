package techgen.core.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectWriter;

import techgen.core.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * INV-7/INV-8 falsifiye-edilebilir kaydı (davranış sözleşmesi §7, BuildReport.cs karşılığı).
 * Her construct'ın durumu + çözülen §8 politikaları. Emitter'lar buraya append eder;
 * deterministik JSON yazılır. {@link Completeness#check} gate'i census'taki her (construct,
 * owner) çiftini burada arar — bulunamayan her çift INV-7 ihlali olarak {@link #silentDrop}
 * ile işaretlenir.
 */
public final class BuildReport {

    /** Bir construct örneğinin build sonucundaki durumu. */
    public enum ConstructStatus { REALIZED, UNSUPPORTED, EMIT_CONFLICT, SILENT_DROP }

    /** Tek bir construct örneğinin rapor kaydı. reason @Nullable — yalnız Realized dışı durumlarda dolu. */
    public record BuildEntry(String construct, String id, ConstructStatus status, /* @Nullable */ String reason) {
    }

    /** Completeness gate tarafından silentDrop'a yazılan sabit gerekçe (INV-7). */
    static final String SILENT_DROP_REASON = "manifest'te var; ne emit ne rapor (INV-7)";

    private final List<BuildEntry> entries = new ArrayList<>();
    private final TreeMap<String, String> policies = new TreeMap<>();

    public void realized(String construct, String id) {
        entries.add(new BuildEntry(construct, id, ConstructStatus.REALIZED, null));
    }

    public void unsupported(String construct, String id, String reason) {
        entries.add(new BuildEntry(construct, id, ConstructStatus.UNSUPPORTED, reason));
    }

    public void conflict(String construct, String id, String reason) {
        entries.add(new BuildEntry(construct, id, ConstructStatus.EMIT_CONFLICT, reason));
    }

    /** Completeness gate: manifest'te VAR ama ne emit ne rapor edilmiş construct (INV-7 ihlali). */
    public void silentDrop(String construct, String id) {
        entries.add(new BuildEntry(construct, id, ConstructStatus.SILENT_DROP, SILENT_DROP_REASON));
    }

    public void policy(String name, String decision) {
        policies.put(name, decision);
    }

    /** Ordinal-sıralı (TreeMap) politika kayıtları — salt-okunur görünüm. */
    public Map<String, String> policies() {
        return policies;
    }

    /** Eklenme sırasıyla tüm entry'ler — salt-okunur görünüm. */
    public List<BuildEntry> entries() {
        return List.copyOf(entries);
    }

    /** Bir construct örneği zaten kayıtlı mı (Realized/Unsupported/EmitConflict) — gate matching için. */
    public boolean covers(String construct, String owner) {
        for (BuildEntry e : entries) {
            if (e.construct().equalsIgnoreCase(construct)
                    && e.status() != ConstructStatus.SILENT_DROP
                    && idMatches(e.id(), owner)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Census owner ↔ rapor Id eşleşmesi: tam eşit VEYA owner + sınır-ayracı önek
     * (compound Id'ler: {@code "{op}#Validation0"}, {@code "{op}->{err}"}, {@code "{op}:{proto}"}).
     * Substring DEĞİL — "Get" census'u "GetInvoice" raporunu YANLIŞ örtmesin (INV-7 soundness).
     */
    static boolean idMatches(String id, String owner) {
        if (id.equals(owner)) {
            return true;
        }
        if (!id.startsWith(owner) || id.length() <= owner.length()) {
            return false;
        }
        char boundary = id.charAt(owner.length());
        return boundary == '#' || boundary == '-' || boundary == ':' || boundary == ' ';
    }

    /** Tüm entry'ler Realized ise true. */
    public boolean clean() {
        return entries.stream().allMatch(e -> e.status() == ConstructStatus.REALIZED);
    }

    /** Gate sonrası sessiz-düşen construct'lar (boşsa INV-7 temiz). */
    public List<BuildEntry> silentDrops() {
        return entries.stream().filter(e -> e.status() == ConstructStatus.SILENT_DROP).toList();
    }

    private static String tag(ConstructStatus s) {
        return switch (s) {
            case REALIZED -> "realized";
            case UNSUPPORTED -> "unsupported";
            case SILENT_DROP -> "silentDrop";
            case EMIT_CONFLICT -> "emitConflict";
        };
    }

    /**
     * Deterministik JSON: entry'ler (construct,id) ordinal sıralı; kök {@code {constructs,policies}};
     * pretty (2-space indent, LF); {@code reason} null ise alan hiç yazılmaz.
     */
    public String toJson() {
        List<BuildEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(BuildEntry::construct, String::compareTo)
                .thenComparing(BuildEntry::id, String::compareTo));

        List<Map<String, Object>> constructs = new ArrayList<>();
        for (BuildEntry e : sorted) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("construct", e.construct());
            m.put("id", e.id());
            m.put("status", tag(e.status()));
            if (e.reason() != null) {
                m.put("reason", e.reason());
            }
            constructs.add(m);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("constructs", constructs);
        root.put("policies", policies);

        try {
            DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
            pp.indentObjectsWith(new DefaultIndenter("  ", "\n"));
            pp.indentArraysWith(new DefaultIndenter("  ", "\n"));
            ObjectWriter writer = Json.mapper().writer(pp);
            return writer.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("BuildReport JSON serileştirilemedi", e);
        }
    }

    /** Dosyaya yaz; içerik sonuna {@code "\n"} eklenir. */
    public void writeTo(Path path) throws IOException {
        Files.writeString(path, toJson() + "\n");
    }
}
