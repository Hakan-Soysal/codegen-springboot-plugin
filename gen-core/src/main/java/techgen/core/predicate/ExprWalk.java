package techgen.core.predicate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import techgen.core.errors.UnsupportedConstruct;
import techgen.core.model.AggNode;
import techgen.core.model.BinaryNode;
import techgen.core.model.CallNode;
import techgen.core.model.DurationNode;
import techgen.core.model.ExprNode;
import techgen.core.model.LiteralNode;
import techgen.core.model.PathNode;

/**
 * ExprNode → DİL-NÖTR yürüyüş (davranış sözleşmesi §3, {@code Predicate/ExprBuild.cs} karşılığı).
 * INV-4: "predicate'in şekli yapısal, tipli kod (dynamic yok)" seam'inin dil-ortak çekirdeği.
 *
 * <p><b>Dil-nötr kalma sözleşmesi (anti-pattern §8):</b> bu sınıf hiçbir Java kod parçacığı
 * üretmez (string-concat ile {@code &&}/{@code ==}/{@code BigDecimal} vb. YASAK). {@code and}/
 * {@code or}/{@code cmp}/{@code add}/{@code sub}/{@code mul}/{@code div} node-kind'i ve (yalnız
 * cmp/arith'te dolu) ham operatör sembolü {@link Render} callback'ine olduğu gibi iletilir;
 * hedef-dile özel eşleme (ör. Java'da {@code and→&&}, {@code =→==}, decimal-context BigDecimal
 * literal biçimi) render katmanının (T3.5) işidir.</p>
 *
 * <p>Desteklenen: {@link BinaryNode} (her zaman parantezli), {@link PathNode}, {@link LiteralNode}.
 * {@link AggNode}/{@link CallNode}/{@link DurationNode} → {@link UnsupportedConstruct} (INV-7).
 * Op'suz cmp/arith (nodeKind cmp/add/sub/mul/div iken {@code op == null}) → {@link UnsupportedConstruct}.</p>
 */
public final class ExprWalk {

    private ExprWalk() {
    }

    /** Yürüyüş sonucu: render edilmiş ifade + referans verilen path'ler (görülme sırasıyla, distinct). */
    public record Result<T>(T expr, List<List<String>> paths) {
    }

    /**
     * Hedef-dile render callback'i. {@code nodeKind} ham AST değeridir
     * ({@code and|or|cmp|add|sub|mul|div}); {@code op} yalnız cmp/arith'te dolu (and/or'da null).
     */
    public interface Render<T> {
        /**
         * {@code left op right} birleşimini render eder. Sözleşme: implementasyon SONUCU HER
         * ZAMAN parantezli üretmelidir (nesting derinliğinden bağımsız) — CoreTemplate1
         * {@code ExprBuild.WalkBinary}'nin {@code "({left} {op} {right})"} kuralıyla birebir.
         * {@code ExprWalk} bunu kendisi biçimlendirmez (dil-nötr kalır); parantezleme render
         * katmanının (T3.5) sorumluluğudur.
         */
        T binary(String nodeKind, /* @Nullable */ String op, T left, T right);

        T path(List<String> path, String propName);

        T literal(String litKind, Object value, boolean decimalContext);
    }

    /** {@code resolveType} verilmeden yürüyüş (dil-nötr, tip-baskınlığı devre dışı). */
    public static <T> Result<T> walk(ExprNode root, Render<T> render) {
        return walk(root, render, null);
    }

    /**
     * {@code resolveType}: path → nötr manifest tipi ("Decimal"/"Int"/…). Verilirse cmp/arith
     * operandlarından biri "Decimal" ise alt-ifadeler decimal bağlamında render edilir (Literal
     * callback'ine {@code decimalContext=true} geçilir) — CoreTemplate1 {@code ExprBuild.Build}
     * ile birebir (yalnız render Java'ya özel BigDecimal suffix'i DEĞİL, callback'e bırakılır).
     */
    public static <T> Result<T> walk(ExprNode root, Render<T> render, /* @Nullable */ Function<List<String>, String> resolveType) {
        List<List<String>> paths = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        T expr = walkNode(root, false, render, resolveType, paths, seen);
        return new Result<>(expr, List.copyOf(paths));
    }

    private static <T> T walkNode(ExprNode n, boolean decimalCtx, Render<T> render,
            Function<List<String>, String> resolveType, List<List<String>> paths, Set<String> seen) {
        return switch (n) {
            case BinaryNode b -> walkBinary(b, render, resolveType, paths, seen);
            case PathNode p -> walkPath(p, render, paths, seen);
            case LiteralNode l -> render.literal(l.litKind(), l.value(), decimalCtx);
            case AggNode ignored -> throw new UnsupportedConstruct("unsupported expr node: AggNode");
            case CallNode ignored -> throw new UnsupportedConstruct("unsupported expr node: CallNode");
            case DurationNode ignored -> throw new UnsupportedConstruct("unsupported expr node: DurationNode");
        };
    }

    private static boolean isArith(String nodeKind) {
        return switch (nodeKind) {
            case "add", "sub", "mul", "div" -> true;
            default -> false;
        };
    }

    private static boolean isBoolConnective(String nodeKind) {
        return "and".equals(nodeKind) || "or".equals(nodeKind);
    }

    private static <T> T walkBinary(BinaryNode b, Render<T> render, Function<List<String>, String> resolveType,
            List<List<String>> paths, Set<String> seen) {
        String nodeKind = b.nodeKind();
        if (!isBoolConnective(nodeKind) && (isArith(nodeKind) || "cmp".equals(nodeKind)) && b.op() == null) {
            throw new UnsupportedConstruct("unsupported expr node: '" + nodeKind + "' op'suz (cmp/arith operatörsüz)");
        }
        // cmp/aritmetik operandlarından biri Decimal ise alt-ifadeler decimal bağlamında render edilir.
        boolean childDecimal = resolveType != null && !isBoolConnective(nodeKind)
                && ("Decimal".equals(typeOf(b.left(), resolveType)) || "Decimal".equals(typeOf(b.right(), resolveType)));
        T left = walkNode(b.left(), childDecimal, render, resolveType, paths, seen);
        T right = walkNode(b.right(), childDecimal, render, resolveType, paths, seen);
        return render.binary(nodeKind, b.op(), left, right);
    }

    private static <T> T walkPath(PathNode p, Render<T> render, List<List<String>> paths, Set<String> seen) {
        String prop = propName(p.path());
        if (seen.add(prop)) {
            paths.add(p.path());
        }
        return render.path(p.path(), prop);
    }

    /**
     * Bottom-up nötr sayısal tip: Decimal &gt; Double &gt; Int baskınlığı (add/sub/mul/div için);
     * literal tam-sayı değerli double → "Int". {@code resolveType} null ise path'ler için null döner.
     */
    public static String typeOf(ExprNode n, /* @Nullable */ Function<List<String>, String> resolveType) {
        return switch (n) {
            case PathNode p -> resolveType == null ? null : resolveType.apply(p.path());
            case LiteralNode l when l.value() instanceof Double d -> (d == Math.floor(d)) ? "Int" : "Double";
            case BinaryNode b when isArith(b.nodeKind()) -> {
                String lt = typeOf(b.left(), resolveType);
                String rt = typeOf(b.right(), resolveType);
                if ("Decimal".equals(lt) || "Decimal".equals(rt)) {
                    yield "Decimal";
                }
                if ("Double".equals(lt) || "Double".equals(rt)) {
                    yield "Double";
                }
                yield "Int";
            }
            default -> null;
        };
    }

    /**
     * Karşılaştırma bağlamından path tip-ipucu (davranış sözleşmesi §3, {@code InferLiteralTypes}):
     * bir path bir literal ile kıyaslanıyorsa (status == "planli", count &gt; 5) o literal'in nötr
     * tipi (String/Bool/Int/Decimal) PropName'e map'lenir — ilk eşleşme kazanır.
     */
    public static Map<String, String> inferLiteralTypes(ExprNode root) {
        Map<String, String> hints = new LinkedHashMap<>();
        inferRec(root, hints);
        return hints;
    }

    private static void inferRec(ExprNode n, Map<String, String> hints) {
        if (!(n instanceof BinaryNode b)) {
            return;
        }
        if (!isBoolConnective(b.nodeKind())) {
            hint(b.left(), b.right(), hints);
            hint(b.right(), b.left(), hints);
        }
        inferRec(b.left(), hints);
        inferRec(b.right(), hints);
    }

    private static void hint(ExprNode pathSide, ExprNode otherSide, Map<String, String> hints) {
        if (pathSide instanceof PathNode p) {
            String t = litType(otherSide);
            if (t != null) {
                hints.putIfAbsent(propName(p.path()), t);
            }
        }
    }

    private static String litType(ExprNode n) {
        if (!(n instanceof LiteralNode l)) {
            return null;
        }
        Object v = l.value();
        if (v instanceof String) {
            return "String";
        }
        if (v instanceof Boolean) {
            return "Bool";
        }
        if (v instanceof Double d) {
            return d == Math.floor(d) ? "Int" : "Decimal";
        }
        return null;
    }

    /** Path → collision-safe camel-join: {@code ["resource","creditLimit"]} → {@code "resourceCreditLimit"}. */
    public static String propName(List<String> path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            sb.append(i == 0 ? decapitalize(path.get(i)) : capitalize(path.get(i)));
        }
        return sb.toString();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String decapitalize(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
