package techgen.core.model;

/**
 * Manifest.cs:66 — sıralama anahtarı (event.module, event.name, consumer.op);
 * census'ta event.name ile sayılır (Completeness.cs:49).
 */
public record SubscriptionJson(EventRef event, ConsumerRef consumer) {
}
