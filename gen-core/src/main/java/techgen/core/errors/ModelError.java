package techgen.core.errors;

/** Rezerve — gen-core'da fırlatılmıyor. */
public final class ModelError extends RuntimeException {
    public ModelError(String message) {
        super(message);
    }
}
