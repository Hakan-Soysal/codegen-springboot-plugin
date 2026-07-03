package techgen.spring;

/**
 * {@code gen.config.json} (dile-özel, SPEC §6.6) — şema birebir iki alan. Geçici tip: bu task
 * (T3.2) yalnız kaydı ekler; {@code gen.config.json} yükleyicisi ve whitelist/policy-raporlama
 * detayı T5.1'de eklenecek.
 *
 * <p>{@code dbProvider} whitelist: {@code postgres}/{@code sqlserver}/{@code h2}/{@code inmemory}
 * (→H2 in-memory). {@code null} → seam yorumu; whitelist-dışı → {@code Unsupported("dbProvider", ...)}.
 * {@code testDbProvider} default {@code inmemory}.</p>
 */
public record GenConfig(String dbProvider, String testDbProvider) {
}
