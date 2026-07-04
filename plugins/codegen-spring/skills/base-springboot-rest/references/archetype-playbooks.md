# Arketip Playbook Reference (filler)

Bu referans, seam-doldurucu (filler) için **arketip-bazlı uzman doldurma** rehberidir.
Her arketip için: (a) **kanonik orkestrasyon sırası**, (b) **tek-tip seam (A2)** notu, (c) bir **few-shot
doğru-doldurulmuş** örnek (`doldurulacak` marker gövdesinin gerçek in-place impl ile değişmesi).

> Amaç: seam = ORKESTRASYON. Üreteç sözleşmeyi `{Op}HandlerBase`'in (Generation Gap, gen-owned, abstract)
> adlandırılmış üyelerine ayrıştırdı (`{Op}Guards.validation{N}`/`rule{N}`, `{Entity}Invariants.invariant{N}`,
> adlı-hata fabrikaları + `THROWABLE_ERRORS`, `REQUIRED_ROLES`, `IDEMPOTENCY_KEYS`, kapalı `Result<T>`,
> `Page<T>` zarfı). Gövdenin işi bunları **kanonik sırada bağlamaktır** → halüsinasyon yüzeyi dar. En değerli
> agent-bağlamı = Conventions + Examples.

---

## A2 — TEK-TİP SEAM DESENİ (post-T4, GLOBAL — tüm arketipler için aynı)

**Tüm seam noktaları handler-gövdesiyle AYNI desendedir.** İki ayrı fill mekaniği YOKTUR; Boundary /
Trigger / Subscription özel-durum DEĞİLDİR — handler ile birebir aynı in-place deseni kullanırlar. Java
hedefinde `.NET`'in `partial class`'ı **Generation Gap** deseninin (abstract taban sınıf + human
`extends` alt sınıf) karşılığıdır — Java'da partial class yok:

```
gen-owned abstract taban sınıf ({X}Base.java, HER ZAMAN ezilir — WriteAlways)
        └── insan/LLM alt-sınıfı  →  src/main/java/.../{X}.java  (extends {X}Base, WriteIfAbsent, ASLA ezilmez, marker taşır)
```

- Marker konvansiyonu: boş seam gövdesi `"...doldurulacak"` alt-string'ini taşır
  (`throw new UnsupportedOperationException("...: doldurulacak")`). Filler bu marker gövdesini gerçek impl ile
  değiştirir.
- `{X}Base.java` üreteç-sahibidir, her run ezilir → seam'in *imzasını* (override edilen metot signature'ı)
  asla elle değiştirme.
- `{X}.java` (human, `extends {X}Base`) insan-sahibidir, `WriteIfAbsent` ile bir kez üretilir ve **donar** →
  filler buraya yazar.
- Doldurma `gen/**` altına ASLA yazmaz (emission-contract yazma-yasağı).

**Arketip → tek-tip seam giriş-noktası** (hepsi aynı in-place desen, yalnız override edilen metot değişir):

| Arketip ailesi | Seam dosyası (insan, WriteIfAbsent) | Doldurulacak override |
|---|---|---|
| Command / Query (+saga/+idem/+pagination) | `src/main/java/app/{module}/{op}/{Op}Handler.java` | `Result<T> execute({Op}Request request)` |
| Trigger-inbound | `src/main/java/app/{module}/{op}/{Op}{T}Trigger.java` | `void start()` |
| Subscription-consumer | `src/main/java/app/{consumerModule}/{consumerOp}/{Event}To{Op}Consumer.java` | `void handle({Event} event)` |
| Boundary-client | `src/main/java/app/boundary/{Ext}Client.java` | `{Ext}` interface üye(ler)i (transport impl) |
| Test-arrange | `src/test/java/app/{scope}/{Name}Arrange.java` | `{ReqType} request{i}()` (ön-gereksinim payload'ı) |

> Trigger/Subscription sınıfları `{X}Base` (abstract, `SmartLifecycle`/handler-alanlı) + human `extends`
> alt-sınıfa ayrıştırıldı; gövde alt-sınıfa indi. Boundary interface (`{Ext}`) gen'de kalır, impl
> `{Ext}Client.java` insan-seam'i olur. Sonuç: filler **hepsini in-place doldurur**, arketip ayrımı
> tek-mekaniğe iner.

---

## 1. Command

**Tespit:** request `*Command` (record). **Kanonik sıra:**
`validate → rule → entity + invariant → persist → emit → Result`

- `{Op}Guards.validation{N}(input)` (false → `NotValid` / 400) → `{Op}Guards.rule{N}(input)`
  (false → `NotProcessable` / 422)
- entity oluştur/yükle + `{Entity}Invariants.invariant{N}` → persist (`JpaRepository`) → event emit
  (`EventBus`).
- her başarısız-olabilen adım **adlı-hata** fabrikasına bağlanır (`THROWABLE_ERRORS`); başarı →
  `Success<T>`.

**Seam (A2):** `src/main/java/app/{module}/createinvoice/CreateInvoiceHandler.java`, override `execute`.

Üretilen boş stub (gen-owned `CreateInvoiceHandlerBase` + human seam):
```java
public class CreateInvoiceHandler extends CreateInvoiceHandlerBase {

    public CreateInvoiceHandler(InvoiceRepository invoiceRepository, EventBus eventBus) {
        super(invoiceRepository, eventBus);
    }

    @Override
    public Result<InvoiceId> execute(CreateInvoiceCommand request) {
        throw new UnsupportedOperationException("CreateInvoice: iş mantığı doldurulacak");  // ← marker
    }
}
```

Few-shot — marker değiştirilmiş (kanonik sıra):
```java
public class CreateInvoiceHandler extends CreateInvoiceHandlerBase {

    public CreateInvoiceHandler(InvoiceRepository invoiceRepository, EventBus eventBus) {
        super(invoiceRepository, eventBus);
    }

    @Override
    public Result<InvoiceId> execute(CreateInvoiceCommand request) {
        // 1) validate → NotValid (gen validation{N} + adlı-hata fabrikası)
        if (!CreateInvoiceGuards.validation0(new CreateInvoiceValidation0Input(request.amount()))) {
            return invalidAmount(Map.of("amount", "must be > 0"));
        }

        // 2) rule → NotProcessable
        if (!CreateInvoiceGuards.rule0(new CreateInvoiceRule0Input(request.customerId()))) {
            return customerNotEligible("customer not eligible for invoicing");
        }

        // 3) entity + invariant
        Invoice invoice = Invoice.create(request.customerId(), request.amount());

        // 4) persist
        invoiceRepository.save(invoice);

        // 5) emit
        eventBus.publish(new InvoiceCreated(invoice.id()));

        // 6) Result
        return new Success<>(invoice.id());
    }
}
```

---

## 2. Query

**Tespit:** request `*Query` (record), mutasyon yok. **Kanonik sıra:**
`yetki → sorgu → projeksiyon → Result`

- `REQUIRED_ROLES.contains(...)` / authz kontrolü (false → `NotAuthorized` / 403) → read-only sorgu
  (`@Transactional(readOnly = true)`, `save`/persist YOK)
- DTO projeksiyonu → `Success<T>`. Mutasyon/emit YOK.

**Seam (A2):** `src/main/java/app/{module}/getinvoice/GetInvoiceHandler.java`, override `execute`.

Few-shot — marker değiştirilmiş:
```java
public class GetInvoiceHandler extends GetInvoiceHandlerBase {

    public GetInvoiceHandler(InvoiceRepository invoiceRepository, SecurityContext securityContext) {
        super(invoiceRepository, securityContext);
    }

    @Override
    @Transactional(readOnly = true)
    public Result<InvoiceDto> execute(GetInvoiceQuery request) {
        // 1) yetki
        if (!REQUIRED_ROLES.contains(securityContext.role())) {
            return forbidden("invoice:read role required");
        }

        // 2) sorgu (read-only)
        Invoice invoice = invoiceRepository.findById(request.invoiceId()).orElse(null);
        if (invoice == null) {
            return invoiceNotFound("invoice " + request.invoiceId() + " not found");
        }

        // 3) projeksiyon + 4) Result
        return new Success<>(new InvoiceDto(invoice.id(), invoice.amount(), invoice.status()));
    }
}
```

---

## 3. saga (Command+saga)

**Tespit:** `Boundary`'de `// saga:` compensate kenarı. **Kanonik sıra:**
base Command sırası **+** dış-çağrı sırası **+** hata → **ters-sıra compensate**.

- `saga-orchestration-state` (generator-policy, in-memory) ile committed-adımları izle;
- bir adım fail → o ana kadar başarılı adımları **ters sırada** compensate çağır; sonra adlı-hata döndür.

**Seam (A2):** yine `{Op}Handler.java` — saga *ayrı seam değildir*, aynı in-place `execute` gövdesidir.
Üreteç saga kenarını taban sınıfta yorum-marker olarak emit eder:
```java
// saga: Order -> Payment.charge (compensate: Payment.refund)
// ponytail: committed-adımları izle; hata -> ters-sıra refund çağır (orchestration-state seam).
```

Few-shot — marker değiştirilmiş (ileri sıra + ters-sıra compensate):
```java
public class PlaceOrderHandler extends PlaceOrderHandlerBase {

    public PlaceOrderHandler(OrderRepository orderRepository, PaymentGateway paymentGateway,
            InventoryService inventoryService) {
        super(orderRepository, paymentGateway, inventoryService);
    }

    @Override
    public Result<OrderId> execute(PlaceOrderCommand request) {
        if (!PlaceOrderGuards.validation0(new PlaceOrderValidation0Input(request.lines()))) {
            return emptyOrder(/* ... */);
        }

        Order order = Order.create(request.customerId(), request.lines());

        // ── saga ileri-sıra: committed adımları izle ──
        Deque<Runnable> committed = new ArrayDeque<>();
        try {
            paymentGateway.charge(order.id(), order.total());
            committed.push(() -> paymentGateway.refund(order.id()));   // compensate kaydı

            inventoryService.reserve(order.id(), request.lines());
            committed.push(() -> inventoryService.release(order.id()));

            orderRepository.save(order);
            return new Success<>(order.id());
        } catch (RuntimeException ex) {
            // ── hata → ters-sıra compensate (Deque zaten LIFO — push/pop) ──
            while (!committed.isEmpty()) {
                committed.pop().run();
            }
            return paymentOrReservationFailed("saga rolled back");
        }
    }
}
```

---

## 4. idempotency (Idempotent)

**Tespit:** `{Op}HandlerBase`'te `IDEMPOTENCY_KEYS` sabiti (gen). **Kanonik sıra:**
**başta** `IdempotencyStore.tryBegin` (key = `IDEMPOTENCY_KEYS`'ten türetilmiş) **+** normal Command sırası.

- `tryBegin` false → işlem daha önce yapılmış → idempotent yanıt döndür (yeniden çalıştırma);
- true → tek-seferlik mutasyona devam. `dedup-store` = generator-policy (in-memory).

**Seam (A2):** yine `{Op}Handler.java` — idempotency ön-kontrolü aynı in-place `execute` gövdesinin
başına eklenir. Üreteç `IDEMPOTENCY_KEYS`'i taban sınıfta emit eder:
```java
public abstract class SubmitPaymentHandlerBase {
    public static final List<String> IDEMPOTENCY_KEYS = List.of("request.paymentRef");
    // ...
}
```

Few-shot — marker değiştirilmiş (`tryBegin` başta):
```java
public class SubmitPaymentHandler extends SubmitPaymentHandlerBase {

    public SubmitPaymentHandler(PaymentRepository paymentRepository, IdempotencyStore idempotencyStore) {
        super(paymentRepository, idempotencyStore);
    }

    @Override
    public Result<PaymentId> execute(SubmitPaymentCommand request) {
        // 0) idempotency gate — EN BAŞTA (key = IDEMPOTENCY_KEYS)
        String key = request.paymentRef();
        if (!idempotencyStore.tryBegin(key)) {
            return new Success<>(request.existingPaymentId());  // replay → aynı sonuç, yan-etki yok
        }

        // 1..n) normal Command sırası
        if (!SubmitPaymentGuards.validation0(new SubmitPaymentValidation0Input(request.amount()))) {
            return invalidAmount(/* ... */);
        }
        Payment payment = Payment.create(request.paymentRef(), request.amount());
        paymentRepository.save(payment);
        return new Success<>(payment.id());
    }
}
```

---

## 5. pagination (Query+pagination)

**Tespit:** `{Op}HandlerBase`'te `PAGINATION_STRATEGY` sabiti (gen). **Kanonik sıra:**
base Query sırası **+** strategy (cursor / offset) **+** `Page<T>` zarfı.

- yetki → sorgu (cursor/offset stratejisine göre `Pageable`/`WHERE id > cursor`) → projeksiyon
- `Page<T>(items, nextCursor)` zarfına sar; `cursor-token` kodlaması = generator-policy (opaque).

**Seam (A2):** yine `{Op}Handler.java`, `execute`, dönüş tipi `Result<Page<T>>`. Üreteç `Page<T>` zarfını
çekirdekte kapalı record olarak emit eder:
```java
public record Page<T>(List<T> items, String nextCursor) {}
```

Few-shot — marker değiştirilmiş (cursor strategy + `Page<T>` zarfı):
```java
public class ListInvoicesHandler extends ListInvoicesHandlerBase {

    public ListInvoicesHandler(InvoiceRepository invoiceRepository, SecurityContext securityContext) {
        super(invoiceRepository, securityContext);
    }

    @Override
    @Transactional(readOnly = true)
    public Result<Page<InvoiceDto>> execute(ListInvoicesQuery request) {
        // 1) yetki
        if (!REQUIRED_ROLES.contains(securityContext.role())) {
            return forbidden("invoice:list role required");
        }

        // 2) sorgu + strategy (cursor: id > decode(cursor))
        long afterId = request.cursor() == null ? 0 : decodeCursor(request.cursor());
        List<Invoice> rows = invoiceRepository.findByIdGreaterThanOrderByIdAsc(
                afterId, PageRequest.of(0, request.pageSize() + 1));   // +1 → sonraki sayfa var mı?

        // 3) projeksiyon + Page<T> zarfı
        boolean hasMore = rows.size() > request.pageSize();
        List<InvoiceDto> items = rows.stream()
                .limit(request.pageSize())
                .map(i -> new InvoiceDto(i.id(), i.amount(), i.status()))
                .toList();
        String next = hasMore ? encodeCursor(items.get(items.size() - 1).id()) : null;

        return new Success<>(new Page<>(items, next));
    }
}
```

---

## 6. Trigger-inbound

**Tespit:** `{Op}{T}TriggerBase` var (`SmartLifecycle` iskeleti, gen-owned; `isRunning`/`stop` gen-owned
basit state, `start()` soyut). **Kanonik sıra:**
`start → kaynaktan oku/dinle → request kur → handler.execute → (gerekirse) ack/commit`

- inbound wiring (scheduler/queue/webhook/file/stream) kaynağını bağla; her olayda request kur,
  `handler.execute` çağır. Trigger **iş mantığını içermez**, yalnız wiring yapar.

**Seam (A2):** `src/main/java/app/{module}/createinvoice/CreateInvoiceCronTrigger.java`, override
`start()` — handler ile **aynı in-place desen** (gen abstract base + override + insan alt-sınıfı).

Üretilen boş stub (`CreateInvoiceCronTriggerBase` + human seam):
```java
public class CreateInvoiceCronTrigger extends CreateInvoiceCronTriggerBase {

    public CreateInvoiceCronTrigger(CreateInvoiceHandler handler) {
        super(handler);
    }

    @Override
    public void start() {
        throw new UnsupportedOperationException("CreateInvoiceCronTrigger.start: doldurulacak");  // ← marker
    }
}
```

Few-shot — marker değiştirilmiş (kaynak → `handler.execute`):
```java
public class CreateInvoiceCronTrigger extends CreateInvoiceCronTriggerBase {

    private final DueInvoiceSource source;
    private final TaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduled;

    public CreateInvoiceCronTrigger(CreateInvoiceHandler handler, DueInvoiceSource source,
            TaskScheduler taskScheduler) {
        super(handler);
        this.source = source;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void start() {
        // @trigger.cron inbound wiring: zamanlayıcı → request kur → handler.execute
        this.scheduled = taskScheduler.scheduleAtFixedRate(() -> {
            for (DueInvoice due : source.dueInvoices()) {
                CreateInvoiceCommand request = new CreateInvoiceCommand(due.customerId(), due.amount());
                handler.execute(request);   // gen abstract base'den handler enjekte edildi
            }
        }, Duration.ofHours(1));
        running = true;
    }
}
```

---

## 7. Subscription-consumer

**Tespit:** `{Event}To{Op}ConsumerBase` var (gen-owned; `handle(event)` soyut). **Kanonik sıra:**
`handle → event → request eşle → handler.execute`

- gelen `event`'i hedef op'un request tipine eşle; `handler.execute` çağır. Consumer **iş mantığı
  içermez**, yalnız event→request mapping + dispatch.

**Seam (A2):** `src/main/java/app/{consumerModule}/createinvoice/OrderPaidToCreateInvoiceConsumer.java`,
override `handle(event)` — handler ile **aynı in-place desen**.

Üretilen boş stub (`OrderPaidToCreateInvoiceConsumerBase` + human seam):
```java
public class OrderPaidToCreateInvoiceConsumer extends OrderPaidToCreateInvoiceConsumerBase {

    public OrderPaidToCreateInvoiceConsumer(CreateInvoiceHandler handler) {
        super(handler);
    }

    @Override
    public void handle(OrderPaid event) {
        throw new UnsupportedOperationException(
                "OrderPaidToCreateInvoiceConsumer.handle: doldurulacak");  // ← marker
    }
}
```

Few-shot — marker değiştirilmiş (event→request → `handler.execute`):
```java
public class OrderPaidToCreateInvoiceConsumer extends OrderPaidToCreateInvoiceConsumerBase {

    public OrderPaidToCreateInvoiceConsumer(CreateInvoiceHandler handler) {
        super(handler);
    }

    @Override
    public void handle(OrderPaid event) {
        // event → request eşle
        CreateInvoiceCommand request = new CreateInvoiceCommand(event.customerId(), event.amountDue());

        // dispatch (consumer iş mantığı içermez)
        handler.execute(request);
    }
}
```

---

## 8. Boundary-client

**Tespit:** `{Ext}` boundary interface stub'ı (gen'de `{Ext}` interface'i). **Kanonik sıra (üye başına):**
`request kur (transport DTO) → dış-çağrı (HTTP/gRPC/SDK) → yanıt eşle → tipli dönüş`

- dış adapter: arayüz üyesini transport impl ile gerçekle. Her üye = bir transport çağrısı + map.
  Hatalar transport-istisnasından domain'e çevrilir (saga compensate'i bunu çağırır).

**Seam (A2):** `src/main/java/app/boundary/PaymentGatewayClient.java`, `PaymentGateway` interface impl'i —
handler ile **aynı in-place desen** (gen `{Ext}` arayüzü kalır; impl insan-seam'i `WriteIfAbsent`, ezilmez;
DI `@Bean PaymentGateway paymentGateway(PaymentGatewayClient impl) { return impl; }`).

Üretilen boş stub (`PaymentGatewayClient`):
```java
public class PaymentGatewayClient implements PaymentGateway {

    @Override
    public ChargeResult charge(String orderId, BigDecimal amount) {
        throw new UnsupportedOperationException("PaymentGateway.charge: doldurulacak");  // ← marker
    }
}
```

Few-shot — marker değiştirilmiş (transport impl, Spring `RestClient`):
```java
public class PaymentGatewayClient implements PaymentGateway {

    private final RestClient restClient;

    public PaymentGatewayClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ChargeResult charge(String orderId, BigDecimal amount) {
        // 1) request kur (transport DTO)
        Map<String, Object> body = Map.of(
                "order_id", orderId,
                "amount_cents", amount.multiply(BigDecimal.valueOf(100)).intValue());

        // 2) dış-çağrı
        ChargeResponse dto = restClient.post()
                .uri("/v1/charges")
                .body(body)
                .retrieve()
                .body(ChargeResponse.class);

        // 3) yanıt eşle → 4) tipli dönüş
        return new ChargeResult(dto.chargeId(), "succeeded".equals(dto.status()));
    }
}
```

---

## 9. test-arrange (ARRANGE-only seam)

**Tespit:** `src/test/java/app/{scope}/{Name}Arrange.java` (gen owned test iskeleti
`gen/test-java/app/{scope}/{Name}Test.java`'nın çağırdığı ARRANGE seam'i; `WriteIfAbsent`, marker `//
Arrange {op}: doldurulacak`). **Kanonik sıra:**
`temiz-data hazırlığı → her SINGLE-creator ön-gereksinim için tipli request{i}() payload'ı`

- seam'in tek işi tutarlı başlangıç state'i kurmak — **ASSERT'e ASLA dokunma** (owned test iskeleti
  `gen/test-java/**`'dedir; anti-circularity, Altın kural A3).
- Ön-gereksinim payload'ları **yalnız `Single`** (tek-creator) prereq'ler için emit edilir; `Ambiguous`/
  `Missing` prereq'li testlere zaten seam emit edilmez.

**Seam (A2):** `src/test/java/app/sales/TakvimSureciArrange.java`, her `Single` prereq/adım için
`request{i}()` metodu — handler ile **aynı in-place desen** (owned iskelet + human seam), ama **scope
ARRANGE ile sınırlı**.

Üretilen boş stub (owned iskelet `TakvimSureciArrange`):
```java
// ARRANGE human-seam (TakvimSureciArrange): op-başı tipli request payload'ları — insan/LLM doldurur.
// gen ezmez (WriteIfAbsent).
public class TakvimSureciArrange {

    public TanimlaDersTipiCommand request0() {
        return null; // Arrange TanimlaDersTipi: doldurulacak — tutarlı başlangıç payload'u kur
    }
}
```

Few-shot — marker değiştirilmiş (yalnız ARRANGE, ASSERT'e dokunulmadı):
```java
public class TakvimSureciArrange {

    public TanimlaDersTipiCommand request0() {
        // tek-creator ön-gereksinim: ClassType → TanimlaDersTipi (Single)
        return new TanimlaDersTipiCommand("Pilates", Duration.ofMinutes(45), 12);
    }
}
```

---

## Doldurma kuralları (özet)

1. **Yalnız contract/gen'de geçen tip/paket** kullan (halüsinasyon kapısı; paket-allowlist + build).
2. **Kanonik sırayı** ihlal etme — her arketibin sırası yukarıda kesindir.
3. Gövde gen üyelerini **bağlar** (icat etmez): `{Op}Guards.validation{N}`/`rule{N}` /
   `{Entity}Invariants.invariant{N}` / `REQUIRED_ROLES` / `IDEMPOTENCY_KEYS` / adlı-hata fabrikaları
   (`THROWABLE_ERRORS`) + kapalı `Result<T>` / `Page<T>`.
4. **Marker** (`doldurulacak`) gövdesini gerçek impl ile değiştir; override edilen metot imzasına dokunma.
5. `gen/**` altına ASLA yazma; yalnız human seam dosyalarına (`{X}.java extends {X}Base`) yaz
   (WriteIfAbsent, donar).
6. `test-arrange` seam'i **yalnız ARRANGE** doldurur; owned `gen/test-java/**` ASSERT'ine ASLA dokunma
   (anti-circularity).
