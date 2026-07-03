# T4.2 🔴 — EventBus + emits + Subscription consumer + seam

## 1. Goal
`emits` construct'ını handler yüzeyine bağla (EventBus alanı), subscription'ları consumer sınıflarına
emit et: gen-owned `{Event}To{Op}ConsumerBase` + human seam + kök `Subscriptions` kaydı.

## 2. Why
Subscription consumer'ı YANLIŞ modüle emit etmek (event'in modülü vs consumer'ın modülü) .NET
tarafında açık kuraldır (consumer op slice'ında yaşar); karıştırılırsa census `subscription`
(Event.Name ile sayılır) yine kapanır ama davranış yanlış olur — silent-fail. Seam mekaniği
(WriteIfAbsent) burada üçüncü kez farklı imzayla tekrarlanır.

## 3. Inputs
- `SPEC.md` §6.2 (subscription marker), §6.3 (Events/Subscriptions satırları)
- `docs/referans/gen-dotnet-emisyon-sozlesmesi.md` §1 (Subscriptions.g.cs + SubscriptionLogic +
  EventBus koşulları), §2 (marker: `"{cls}.handle: doldurulacak"`)
- **Pattern (READ-ONLY):** CoreTemplate1 `DotnetEmitter.cs` 1348-1398 (SubscriptionsFile/
  SubscriptionLogic/EventBus), 39-48 (emit sırası)
- T3.3 (Event record + EventBus), T3.6 (slice + Wiring deseni)

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q -pl gen-spring test    # expected: exit 0 (T3.6 yeşil)
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — emits → HandlerBase alanı
**Action:** op.emits boş değilse HandlerBase'e `protected final EventBus eventBus;` alanı + ctor
param (T3.6 ctor-senkron kuralı: Wiring @Bean çağrısı da güncellenir). Javadoc'a emit listesi.
→ her emit için `realized("emits", "{op}->{ev}")`.

### Step 5.2 — Consumer base + seam
**File emit (gen):** `gen/java/app/{consumerModule}/{consumerOp}/{Event}To{Op}ConsumerBase.java`
— abstract; ctor'da `{Op}Handler` alanı; `public abstract void handle({Event} event);` +
Javadoc "event→request eşle → handler.execute çağır" (playbook kanonik sırası).
**File emit (human, writeIfAbsent):** `src/main/java/app/{consumerModule}/{consumerOp}/
{Event}To{Op}Consumer.java` — extends base; gövde:
`throw new UnsupportedOperationException("{Event}To{Op}Consumer.handle: doldurulacak");`
→ `realized("subscription", event.name())`.

### Step 5.3 — Kök Subscriptions kaydı
**File emit:** `gen/java/app/Subscriptions.java` (yalnız subscriptions>0) — `@Configuration`;
consumer bean'leri `@Bean` ile (human sınıflar); `Map<Class<?>, List<Object>>` benzeri statik
kayıt tablosu YERİNE basit: her consumer için `@Bean` + Javadoc dispatch notu (in-memory bus
stub'ının dispatch'i doldurucu/altyapı işi — .NET paritesi: kayıt + stub). Bootstrap `@Import`'a eklenir.

### Step 5.4 — Testler
**File:** `gen-spring/src/test/java/techgen/spring/SubscriptionEmitTest.java`
- fixture: `InvoiceCreatedToWriteAuditLogConsumerBase` **Ops** modül slice'ında (`app/ops/writeauditlog/`)
  — Billing'de DEĞİL (negatif grep).
- Human consumer seam var, marker'lı; ikinci emit ezmiyor.
- CreateInvoice HandlerBase'inde `EventBus eventBus` alanı; GetInvoice'ta YOK.
- `Subscriptions.java` Bootstrap'tan import ediliyor.
- build-report: ("emits","CreateInvoice->InvoiceCreated"), ("subscription","InvoiceCreated") realized.
- E2E: üretilen app `mvn -q compile` exit 0 (@Tag e2e).

## 6. Acceptance tests
### 6.1 `mvn -q -pl gen-spring test` → exit 0.
### 6.2 Pozitif — consumer doğru modülde + compile yeşil.
### 6.3 Negatif — consumer Billing'de YOK; EventBus alanı emits'siz op'ta YOK.

## 7. Out of scope (DO NOT)
- Gerçek dispatch/outbox implementasyonu — stub sözleşmesi (doldurucu/altyapı alanı)
- Trigger — T4.3; boundary — T4.4
- consistency=eventual outbox iskeleti — T3.7'de yorum olarak zaten var

## 8. Anti-patterns
- DO NOT consumer'ı event'in modülüne koy — CONSUMER op'un modül slice'ı.
- DO NOT Spring `@EventListener`/ApplicationEvent altyapısına bağla — kendi `EventBus` interface'imiz
  (framework icadı yok / taşınabilirlik; .NET paritesi IEventBus).
- DO NOT human consumer'a `@Component` — kayıt gen-owned Subscriptions config'inde.
- DO NOT handle imzasını `Object event` yap — tipli `{Event}` record.

## 9. Definition of Done
- [ ] emits alanı + consumer base/seam + Subscriptions kaydı mevcut
- [ ] ≥7 test; modül-doğruluğu negatif assert'li
- [ ] E2E compile yeşil (çıktı okundu)
- [ ] `git status`: yalnız gen-spring

## 10. Self-check
1. Consumer'ın CONSUMER modülünde olduğunu pozitif+negatif assert'le kanıtladım mı?
2. Ctor-senkron kuralını (HandlerBase+Wiring birlikte) uyguladım mı — compile bunu kanıtlıyor mu?
3. Marker birebir mi?
4. Testleri gerçekten koştum mu?
5. Allowlist dışı dosya var mı?
