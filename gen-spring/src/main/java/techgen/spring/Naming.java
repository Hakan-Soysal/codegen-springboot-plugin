package techgen.spring;

import java.util.Locale;

/**
 * Manifest skaler / kimlik → Java idiom eşlemesi (davranış sözleşmesi §6.4; referans
 * {@code docs/referans/gen-dotnet-emisyon-sozlesmesi.md} §3; CoreTemplate1 {@code Naming.cs}
 * karşılığı, Java hedefine uyarlanmış).
 */
public final class Naming {

    private Naming() {
    }

    /**
     * Manifest tip adını Java tipine eşler (non-nullable bağlam; {@code Int}→{@code int},
     * {@code Bool}/{@code Boolean}→{@code boolean}). Nullable bağlam için {@link #javaType(String, boolean, boolean)}.
     */
    public static String javaType(String manifestType, boolean collection) {
        return javaType(manifestType, collection, false);
    }

    /**
     * SPEC §6.4 tablosu: ID/String/{@code *Id} soneki→{@code String}; Decimal→{@code BigDecimal};
     * Int→{@code int} ({@code nullable}=true iken {@code Integer}); Bool/Boolean→{@code boolean}
     * ({@code nullable}=true iken {@code Boolean}); DateTime→{@code Instant}; Date→{@code LocalDate};
     * Duration→{@code Duration}; diğer adlar→passthrough (üretilen tip); {@code collection}→{@code List<T>}.
     */
    public static String javaType(String manifestType, boolean collection, boolean nullable) {
        String base = switch (manifestType) {
            case "ID", "String" -> "String";
            case "Decimal" -> "BigDecimal";
            case "Int" -> nullable ? "Integer" : "int";
            case "Bool", "Boolean" -> nullable ? "Boolean" : "boolean";
            case "DateTime" -> "Instant";
            case "Date" -> "LocalDate";
            case "Duration" -> "Duration";
            default -> manifestType.endsWith("Id") ? "String" : manifestType;
        };
        return collection ? "List<" + base + ">" : base;
    }

    /** İlk harf upper, gerisi aynen (CoreTemplate1 {@code Naming.Pascal} birebir). */
    public static String pascal(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** İlk harf lower, gerisi aynen. */
    public static String camel(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** Modül adı → Java paket segmenti (lowercase). */
    public static String packageOf(String module) {
        return module.toLowerCase(Locale.ROOT);
    }

    /** Op-slice paketi: {@code "app." + lower(module) + "." + lower(opId)} (SPEC §6.3). */
    public static String slicePackage(String module, String opId) {
        return "app." + packageOf(module) + "." + opId.toLowerCase(Locale.ROOT);
    }

    /** camelCase/PascalCase → UPPER_SNAKE (sabit adlandırma, ör. {@code REQUIRED_ROLES}). */
    public static String constant(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(s.charAt(i - 1))) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    /** HTTP metodu → Spring mapping anotasyonu. POST/PUT/PATCH/DELETE eşlenir; diğerleri GET'e düşer. */
    public static String httpVerbAnnotation(String method) {
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "POST" -> "@PostMapping";
            case "PUT" -> "@PutMapping";
            case "PATCH" -> "@PatchMapping";
            case "DELETE" -> "@DeleteMapping";
            default -> "@GetMapping";
        };
    }

    /** POST/PUT/PATCH gövde bağlar ({@code @RequestBody}); GET/DELETE route/query bağlar. */
    public static boolean bindsBody(String method) {
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "POST", "PUT", "PATCH" -> true;
            default -> false;
        };
    }
}
