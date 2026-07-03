package techgen.core.gm;

import java.util.List;
import java.util.Map;

/**
 * Tip ortamı (davranış sözleşmesi §5, {@code GenerationModel.cs:30-56} karşılığı): predicate
 * path'lerini manifest tiplerine çözer → adapter'lar DİL-TİPLİ predicate emit eder (dynamic YOK).
 * Çözülemeyen path = tipli seam (null döner).
 *
 * @param opParams     opId → (paramName → manifestType)
 * @param entityFields entityId → (fieldName → manifestType)
 */
public record TypeEnv(
        Map<String, Map<String, String>> opParams,
        Map<String, Map<String, String>> entityFields) {

    /**
     * Op'un yazma-hedefi entity'si (resource.* çözümü için): {@code creates ∪ updates ∪ deletes}
     * içinden İLK eleman (sırayla creates, updates, deletes listeleri gezilir) — sözleşme "en
     * mantıklı entity" DEĞİL, İLK eleman der (deterministik, anti-pattern §8). Yoksa null.
     */
    public static String writeTarget(GmOperation op) {
        var access = op.op().access();
        if (!access.creates().isEmpty()) {
            return access.creates().get(0);
        }
        if (!access.updates().isEmpty()) {
            return access.updates().get(0);
        }
        if (!access.deletes().isEmpty()) {
            return access.deletes().get(0);
        }
        return null;
    }

    /**
     * Bir predicate path'ini NÖTR manifest tipine çözer (dil-bağımsız).
     * {@code actor.*} → "String" (opak claim); {@code resource.*} → write-hedefi entity alanı;
     * bare path → önce op param (guard), sonra entity alanı (invariant). Çözülemeyen → null.
     */
    public String resolvePath(GenerationModel gm, List<String> path, /* @Nullable */ String opId,
            /* @Nullable */ String entityId) {
        if (path.isEmpty()) {
            return null;
        }
        if ("actor".equals(path.get(0))) {
            return "String";
        }
        if ("resource".equals(path.get(0)) && opId != null) {
            GmOperation op = gm.operations().stream()
                    .filter(o -> o.id().equals(opId))
                    .findFirst()
                    .orElse(null);
            String wt = op == null ? null : writeTarget(op);
            if (wt != null && path.size() >= 2) {
                Map<String, String> fields = entityFields.get(wt);
                if (fields != null) {
                    return fields.get(path.get(1));
                }
            }
            return null;
        }
        if (opId != null) {
            Map<String, String> ps = opParams.get(opId);
            if (ps != null && ps.containsKey(path.get(0))) {
                return ps.get(path.get(0));
            }
        }
        if (entityId != null) {
            Map<String, String> fs = entityFields.get(entityId);
            if (fs != null && fs.containsKey(path.get(0))) {
                return fs.get(path.get(0));
            }
        }
        return null;
    }
}
