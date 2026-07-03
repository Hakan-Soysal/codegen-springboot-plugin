package techgen.spring;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import techgen.core.errors.UnsupportedConstruct;
import techgen.core.model.ExprNode;
import techgen.core.predicate.ExprWalk;

/**
 * ExprNode → <b>tipli Java</b> predicate render (SPEC §6.5; davranış sözleşmesi §3;
 * CoreTemplate1 {@code Predicate/ExprBuild.cs} + {@code DotnetEmitter} predicate katmanının
 * Java hedefine uyarlanması). Dil-nötr yürüyüş {@link ExprWalk}'ta; <b>render stratejisi
 * burada</b> (anti-pattern §8: render gen-core'a KONMAZ).
 *
 * <p>INV-4'ün Java'ya özgü kritik noktası: C#'ta operatör-aşırıyüklemesi {@code decimal}/{@code string}
 * karşılaştırmasını doğal operatörle çözer; Java'da {@code BigDecimal} karşılaştırması
 * {@code compareTo}, {@code String} eşitliği {@code equals} ister — yanlış render ya derlenmez ya
 * da sessiz-yanlış davranır ({@code ==} referans karşılaştırması). Bu yüzden karşılaştırma tip-duyarlı
 * render edilir ve render kararı ile input alan tipi <b>aynı nötr tip</b>ten türetilir (tutarlılık
 * = derlenebilirlik).</p>
 *
 * <p><b>Tip çözüm sırası</b> (SPEC §6.5; task §5.1 "parite: .NET aynı sıra"): önce {@code resolveType}
 * (TypeEnv), null dönerse {@link ExprWalk#inferLiteralTypes} literal-ipuçları, o da yoksa son-çare
 * {@code Decimal} ({@code BigDecimal}) — CoreTemplate1 {@code ResolveType} son-çare {@code decimal} ile
 * BİREBİR. Bu, hem tam-pariteyi hem de <b>derlenebilirliği</b> sağlar: çözülemeyen bir path Decimal
 * bir path ile kıyaslanırsa ({@code amount <= resource.creditLimit}, fixture'da Invoice'ta
 * {@code creditLimit} yok → çözülemez) compareTo formu ile {@code BigDecimal} alan tutarlı kalır;
 * son-çare {@code Double} olsaydı {@code compareTo(double)} DERLENMEZDİ. (Task §5.1 metnindeki "Double
 * varsay" ifadesi, task §5.4 test beklentisi "BigDecimal resourceCreditLimit" ve ".NET aynı sıra"
 * pariteси ile çeliştiğinden Decimal alınmıştır — rapor notu.)</p>
 */
public final class JavaPredicateRenderer implements ExprWalk.Render<JavaPredicateRenderer.Frag> {

    /** Render fragmanı: üretilen Java kod parçası + nötr tip (compareTo/equals/primitif kararı için). */
    public record Frag(String code, String type) {
    }

    /**
     * T6.3-FIX #3 — nötr manifest temporal tipleri ({@code Date}→{@code LocalDate},
     * {@code DateTime}→{@code Instant}; {@link Naming#javaType}). C#'ta {@code DateTime} operatör-
     * aşırıyüklemesiyle {@code >=}/{@code <=} doğal çözülür (CoreTemplate1 {@code ExprBuild} bu
     * yüzden özel-durum içermez); Java'da {@code LocalDate}/{@code Instant} karşılaştırma operatörü
     * DESTEKLEMEZ (derlenmez) — {@code Decimal} gibi {@code compareTo} formuna alınır (davranışsal
     * parite: aynı karşılaştırma semantiği, dile-özgü render).
     */
    private static final Set<String> TEMPORAL = Set.of("Date", "DateTime");

    private final Function<List<String>, String> resolveType;
    private final Map<String, String> hints;
    private final boolean recordAccessor;

    private JavaPredicateRenderer(Function<List<String>, String> resolveType, Map<String, String> hints,
            boolean recordAccessor) {
        this.resolveType = resolveType;
        this.hints = hints;
        this.recordAccessor = recordAccessor;
    }

    /**
     * Guards biçimi: path → {@code input.{camelJoin(path)}()} (input record accessor'ı).
     * Döner: {@code (String expr, List<List<String>> paths)} — distinct path'ler görülme sırasıyla.
     */
    public static ExprWalk.Result<String> render(ExprNode root, /* @Nullable */ Function<List<String>, String> resolveType) {
        return render(root, resolveType, true);
    }

    /**
     * {@code recordAccessor=true} → Guards ({@code input.prop()}); {@code false} → Invariants
     * (bare parametre adı {@code prop}, metot imzası alanları doğrudan alır — task §5.3).
     */
    public static ExprWalk.Result<String> render(ExprNode root, /* @Nullable */ Function<List<String>, String> resolveType,
            boolean recordAccessor) {
        Map<String, String> hints = ExprWalk.inferLiteralTypes(root);
        JavaPredicateRenderer renderer = new JavaPredicateRenderer(resolveType, hints, recordAccessor);
        // ExprWalk'a verilen tam-çözüm: decimalContext (literal → BigDecimal) render kararıyla aynı
        // nötr tipten türesin diye resolveType→hints→Double zinciri buradan geçer.
        Function<List<String>, String> full = renderer::neutralType;
        ExprWalk.Result<Frag> res = ExprWalk.walk(root, renderer, full);
        return new ExprWalk.Result<>(res.expr().code(), res.paths());
    }

    /**
     * Bir path'in Java input alan tipi (Guards input record'u / Invariants parametresi). Render
     * kararıyla <b>aynı</b> nötr tip zincirinden türer → BigDecimal alanı compareTo ile, primitif
     * alan doğal operatörle eşleşir (derlenebilirlik garantisi).
     */
    public static String javaFieldType(List<String> path, /* @Nullable */ Function<List<String>, String> resolveType,
            Map<String, String> hints) {
        String t = resolveType == null ? null : resolveType.apply(path);
        if (t == null) {
            t = hints.get(ExprWalk.propName(path));
        }
        if (t == null) {
            t = "Decimal";
        }
        return "Double".equals(t) ? "double" : Naming.javaType(t, false);
    }

    /** resolveType → hints → Decimal (son-çare) zinciriyle path'in nötr tipini (asla null) döndürür. */
    private String neutralType(List<String> path) {
        String t = resolveType == null ? null : resolveType.apply(path);
        if (t != null) {
            return t;
        }
        String h = hints.get(ExprWalk.propName(path));
        if (h != null) {
            return h;
        }
        return "Decimal";
    }

    // ── ExprWalk.Render<Frag> callback'leri ────────────────────────────────────────────────

    @Override
    public Frag path(List<String> path, String propName) {
        String code = recordAccessor ? "input." + propName + "()" : propName;
        return new Frag(code, neutralType(path));
    }

    @Override
    public Frag literal(String litKind, Object value, boolean decimalContext) {
        if (value instanceof String s) {
            return new Frag("\"" + escape(s) + "\"", "String");
        }
        if (value instanceof Boolean b) {
            return new Frag(b ? "true" : "false", "Bool");
        }
        double d = ((Number) value).doubleValue();
        String num = numberText(d);
        if (decimalContext) {
            // BigDecimal String-ctor ZORUNLU (anti-pattern §8: new BigDecimal(0.1) temsil hatası verir).
            return new Frag("new BigDecimal(\"" + num + "\")", "Decimal");
        }
        String t = (d == Math.floor(d) && !Double.isInfinite(d)) ? "Int" : "Double";
        return new Frag(num, t);
    }

    @Override
    public Frag binary(String nodeKind, String op, Frag left, Frag right) {
        if ("and".equals(nodeKind)) {
            return new Frag("(" + left.code() + " && " + right.code() + ")", "Bool");
        }
        if ("or".equals(nodeKind)) {
            return new Frag("(" + left.code() + " || " + right.code() + ")", "Bool");
        }
        boolean decimal = "Decimal".equals(left.type()) || "Decimal".equals(right.type());
        boolean temporal = TEMPORAL.contains(left.type()) || TEMPORAL.contains(right.type());
        boolean string = "String".equals(left.type()) || "String".equals(right.type());

        if (isArith(nodeKind)) {
            if (decimal) {
                String method = switch (op == null ? "" : op) {
                    case "+" -> "add";
                    case "-" -> "subtract";
                    case "*" -> "multiply";
                    case "/" -> "divide";
                    default -> throw new UnsupportedConstruct("unsupported arith op: " + op);
                };
                return new Frag("(" + left.code() + "." + method + "(" + right.code() + "))", "Decimal");
            }
            String rt = ("Double".equals(left.type()) || "Double".equals(right.type())) ? "Double" : "Int";
            return new Frag("(" + left.code() + " " + op + " " + right.code() + ")", rt);
        }

        // cmp
        if (decimal) {
            String cop = isEq(op) ? "==" : op;
            return new Frag("(" + left.code() + ".compareTo(" + right.code() + ") " + cop + " 0)", "Bool");
        }
        if (temporal) {
            // LocalDate/Instant >=/<=/>/< DERLENMEZ (BigDecimal ile aynı gerekçe) — compareTo formu.
            String cop = isEq(op) ? "==" : op;
            return new Frag("(" + left.code() + ".compareTo(" + right.code() + ") " + cop + " 0)", "Bool");
        }
        if (string) {
            if (isEq(op)) {
                return new Frag("(" + left.code() + ".equals(" + right.code() + "))", "Bool");
            }
            if ("!=".equals(op)) {
                return new Frag("(!" + left.code() + ".equals(" + right.code() + "))", "Bool");
            }
            throw new UnsupportedConstruct("String karşılaştırmada '" + op + "' desteklenmiyor (yalnız =, !=)");
        }
        // primitif sayısal / bool → doğal operatör; = → ==
        String jop = isEq(op) ? "==" : op;
        return new Frag("(" + left.code() + " " + jop + " " + right.code() + ")", "Bool");
    }

    // ── yardımcılar ────────────────────────────────────────────────────────────────────────

    private static boolean isEq(String op) {
        return "=".equals(op) || "==".equals(op);
    }

    private static boolean isArith(String nodeKind) {
        return switch (nodeKind) {
            case "add", "sub", "mul", "div" -> true;
            default -> false;
        };
    }

    /** Tam-sayı değerli double → ondalıksız ({@code 0}); aksi halde {@link Double#toString} ({@code 0.5}). */
    private static String numberText(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
