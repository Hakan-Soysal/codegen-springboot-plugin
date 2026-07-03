package techgen.spring;

/**
 * Tek-sabit sürüm kaynağı (davranış sözleşmesi §6.1/§8 anti-pattern: "Spring Boot sürümünü
 * şablona hardcode etme"). Pinler {@code docs/surumler.md}'den (T0.2) alınır; başka HİÇBİR
 * dosyada literal sürüm string'i tekrarlanmaz — şablonlar bu sabitlere referans verir.
 */
public final class Versions {

    private Versions() {
    }

    /** {@code docs/surumler.md}: Spring Boot 3.5.x en güncel patch (Maven Central metadata). */
    public static final String SPRING_BOOT = "3.5.16";

    /** {@code docs/surumler.md}: build-helper-maven-plugin en güncel sürüm (Maven Central metadata). */
    public static final String BUILD_HELPER_PLUGIN = "3.6.1";

    /** Üretilen app'in sabit artefakt sürümü ({@code app:app}, {@code app.gen:generated-parent}). */
    public static final String APP_VERSION = "0.1.0";
}
