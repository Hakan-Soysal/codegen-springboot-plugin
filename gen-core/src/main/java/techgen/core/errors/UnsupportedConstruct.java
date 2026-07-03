package techgen.core.errors;

/**
 * Hedef-adaptör bir construct'ı realize edemiyor (INV-7). Sessiz düşürme yerine
 * rapor edilir; çağıran build-report'a Unsupported yazar. Fatal DEĞİL.
 */
public final class UnsupportedConstruct extends RuntimeException {
    public UnsupportedConstruct(String message) {
        super(message);
    }
}
