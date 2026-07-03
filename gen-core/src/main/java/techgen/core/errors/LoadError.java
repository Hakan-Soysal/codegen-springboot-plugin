package techgen.core.errors;

/** Aşama 1: manifest yok / manifest parse hatası. Her zaman fatal. */
public final class LoadError extends RuntimeException {
    public LoadError(String message) {
        super(message);
    }
}
