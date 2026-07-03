package techgen.core.errors;

/** Aşama 2: linked+contract çözülemedi; entity realizes çözülmedi; op realizes çözülmedi. */
public final class JoinError extends RuntimeException {
    public JoinError(String message) {
        super(message);
    }
}
