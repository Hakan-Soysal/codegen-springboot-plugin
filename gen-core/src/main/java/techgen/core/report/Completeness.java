package techgen.core.report;

import techgen.core.model.BoundaryOpJson;
import techgen.core.model.CallEdgeJson;
import techgen.core.model.Deployable;
import techgen.core.model.EntityFieldJson;
import techgen.core.model.EntityJson;
import techgen.core.model.ErrorJson;
import techgen.core.model.EventJson;
import techgen.core.model.ExternalJson;
import techgen.core.model.ExtJson;
import techgen.core.model.FieldJson;
import techgen.core.model.ManifestJson;
import techgen.core.model.ModuleDecl;
import techgen.core.model.OperationJson;
import techgen.core.model.ParamJson;
import techgen.core.model.ServingJson;
import techgen.core.model.SubscriptionJson;
import techgen.core.model.TypeJson;
import techgen.core.model.UnchartedEntity;
import techgen.core.model.UnchartedJson;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manifest construct census = davranış sözleşmesi §7 traceability tablosunun kod-karşılığı
 * (tek-kaynak, Completeness.cs karşılığı). Manifest'te VAR olan her construct örneğini
 * (construct, owner) çifti olarak sayar. Build-time-only construct'lar (standalone/contract/
 * import/rolemap/extension-decl/realizes/access/pureTechnical; returnAnnotations, emits-target
 * ConsumerOpRef, {@code @internal} token, external/uncharted/error/event üstü {@code @ns.name})
 * census'a GİRMEZ (§7 N/A listesi).
 */
public final class Completeness {

    private Completeness() {
    }

    /** Manifest'teki TAM construct census'unu üretir (INV-7 no-silent-loss'un ölçüm noktası). */
    public static List<Map.Entry<String, String>> census(ManifestJson m) {
        List<Map.Entry<String, String>> x = new ArrayList<>();

        for (Deployable d : m.deployables()) {
            x.add(entry("deployable", d.name()));
            addExt(x, d.ext(), d.name());
        }
        for (ModuleDecl mod : m.modules()) {
            x.add(entry("module", mod.name()));
            addExt(x, mod.ext(), mod.name());
        }
        for (ErrorJson e : m.errors()) {
            x.add(entry("error", e.id()));
        }
        for (ExternalJson ext : m.externals()) {
            x.add(entry("external", ext.name()));
            for (BoundaryOpJson b : ext.operations()) {
                addBoundaryOp(x, ext.name(), b);
            }
        }
        for (UnchartedJson u : m.uncharted()) {
            x.add(entry("uncharted", u.name()));
            for (BoundaryOpJson b : u.operations()) {
                addBoundaryOp(x, u.name(), b);
            }
            for (UnchartedEntity en : u.entities()) {
                if ("optimistic".equals(en.concurrency())) {
                    x.add(entry("concurrency", u.name() + "." + en.id()));
                }
            }
        }
        for (SubscriptionJson s : m.subscriptions()) {
            x.add(entry("subscription", s.event().name()));
        }
        for (CallEdgeJson ce : m.callEdges()) {
            x.add(entry("calls", ce.from()));
            if (ce.compensate() != null) {
                x.add(entry("compensate", ce.from()));
            }
        }
        for (EventJson ev : m.events()) {
            x.add(entry("event", ev.id()));
            for (FieldJson f : ev.payload()) {
                addExt(x, f.ext(), ev.id() + "." + f.name());
            }
        }
        for (TypeJson t : m.types()) {
            x.add(entry(t.kind(), t.id()));
            addExt(x, t.ext(), t.id());
            if (t.fields() != null) {
                for (FieldJson f : t.fields()) {
                    addExt(x, f.ext(), t.id() + "." + f.name());
                }
            }
        }

        for (EntityJson en : m.entities()) {
            x.add(entry("entity", en.id()));
            addExt(x, en.ext(), en.id());
            if ("optimistic".equals(en.concurrency())) {
                x.add(entry("concurrency", en.id()));
            }
            if (!en.invariants().isEmpty()) {
                x.add(entry("invariant", en.id()));
            }
            if (en.invariants().stream().anyMatch(g -> g.guardRef() != null)) {
                x.add(entry("guardRef", en.id()));
            }
            for (EntityFieldJson f : en.fields()) {
                if (f.sourceOfTruth() != null) {
                    x.add(entry("sourceOfTruth", en.id() + "." + f.name()));
                }
                addExt(x, f.ext(), en.id() + "." + f.name());
            }
        }

        for (OperationJson op : m.operations()) {
            x.add(entry("operation", op.id()));
            x.add(entry("visibility", op.id()));
            if (!op.roles().isEmpty()) {
                x.add(entry("roles", op.id()));
            }
            if (!op.scopes().isEmpty()) {
                x.add(entry("scopes", op.id()));
            }
            if (op.ownership() != null) {
                x.add(entry("ownership", op.id()));
            }
            if (!op.validation().isEmpty()) {
                x.add(entry("validation", op.id()));
            }
            if (!op.rule().isEmpty()) {
                x.add(entry("rule", op.id()));
            }
            boolean guardRef = op.validation().stream().anyMatch(g -> g.guardRef() != null)
                    || op.rule().stream().anyMatch(g -> g.guardRef() != null);
            if (guardRef) {
                x.add(entry("guardRef", op.id()));
            }
            if (op.abac() != null) {
                x.add(entry("permit", op.id()));
            }
            if (op.note() != null) {
                x.add(entry("note", op.id()));
            }
            for (String t : op.throwsList()) {
                x.add(entry("throws", op.id() + "->" + t));
            }
            if (op.idempotent() != null) {
                x.add(entry("idempotent", op.id()));
            }
            if (op.pagination() != null) {
                x.add(entry("pagination", op.id()));
            }
            for (String ev : op.emits()) {
                x.add(entry("emits", op.id() + "->" + ev));
            }
            if (op.consistency() != null
                    && (op.consistency().mode() != null || "eventual".equals(op.consistency().risk()))) {
                x.add(entry("consistency", op.id()));
            }
            for (ServingJson s : op.serving()) {
                x.add(entry("serving", op.id() + ":" + s.protocol()));
            }
            for (ParamJson p : op.signature().params()) {
                addExt(x, p.ext(), op.id() + "." + p.name());
            }
            addExt(x, op.ext(), op.id());
        }

        return x.stream().distinct().toList();
    }

    private static void addBoundaryOp(List<Map.Entry<String, String>> x, String extName, BoundaryOpJson b) {
        x.add(entry("boundary-op", extName + "." + b.id()));
        if (b.validation() != null && !b.validation().isEmpty()) {
            x.add(entry("validation", extName + "." + b.id()));
        }
        if (b.serving() != null) {
            for (ServingJson s : b.serving()) {
                x.add(entry("serving", extName + "." + b.id() + ":" + s.protocol()));
            }
        }
        for (ParamJson p : b.signature().params()) {
            addExt(x, p.ext(), extName + "." + b.id() + "." + p.name());
        }
    }

    private static void addExt(List<Map.Entry<String, String>> x, List<ExtJson> ext, String owner) {
        if (ext == null) {
            return;
        }
        for (ExtJson e : ext) {
            x.add(entry("@" + e.ns() + "." + e.name(), owner));
        }
    }

    private static Map.Entry<String, String> entry(String construct, String owner) {
        return new AbstractMap.SimpleEntry<>(construct, owner);
    }

    /**
     * Gate: census'taki her (construct, owner) çifti build-report'ta kayıtlı mı
     * ({@link BuildReport#covers})? Değilse {@link BuildReport#silentDrop} (INV-7).
     */
    public static void check(ManifestJson m, BuildReport report) {
        for (Map.Entry<String, String> pair : census(m)) {
            if (!report.covers(pair.getKey(), pair.getValue())) {
                report.silentDrop(pair.getKey(), pair.getValue());
            }
        }
    }
}
