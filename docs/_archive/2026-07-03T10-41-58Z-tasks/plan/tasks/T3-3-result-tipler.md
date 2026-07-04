# T3.3 🔴 — Result taksonomisi + ResultHttp + Errors + Types + Events

## 1. Goal
Kapalı Result hiyerarşisini (INV-5), HTTP eşlemesini, modül error kataloglarını, type/enum ve event
record emisyonlarını ekle.

## 2. Why
Result taksonomisi TÜM handler imzalarının dönüş tipi ve conformance runner'ın (T8.1) okuduğu yüzey —
alt-tip adları ve `code()` accessor'ı sözleşmedir; sapma conformance'ı kırar. Hata fabrikaları
resultType→alt-tip eşlemesi semantik olarak incedir (silent-fail).

## 3. Inputs
- `SPEC.md` §6.3 (Result satırı + Errors/Types/Events satırları), §3 INV-5
- `docs/referans/gen-dotnet-emisyon-sozlesmesi.md` §1 "Result taksonomisi" (birebir .NET şekli) +
  Errors/Types/Events satırları
- **Pattern (READ-ONLY):** CoreTemplate1 `DotnetEmitter.cs` 684-733 (Result/ResultHttp), 735-752 (Types),
  828-883 (Errors/ThrowFactory), 1334-1345 (Events)
- T3.2 `SpringEmitter` + `EmitWriter` + `Naming`

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q -pl gen-spring test    # expected: exit 0 (T3.2 yeşil)
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — Result hiyerarşisi
**File emit:** `gen/java/app/Result.java` (+ komşu dosyalar; writeAlways)
**Action:** şablon:
```java
public sealed interface Result<T> permits Success, NotAuthenticated, NotAuthorized, NotValid, NotProcessable, ServerError {}
record Success<T>(T value) implements Result<T> {}
record NotAuthenticated<T>(String reason) implements Result<T> {}
record NotAuthorized<T>(String reason) implements Result<T> {}
record NotValid<T>(Map<String,String> errors) implements Result<T> {}
record NotProcessable<T>(String code, String message) implements Result<T> {}
record ServerError<T>(String message) implements Result<T> {}
record Page<T>(List<T> items, String nextCursor) {}
record Unit() {}
```
(Her public record ayrı dosya; paket `app`.) Alt-tip basit adları ve `code()` accessor adı SABİT
sözleşmedir (conformance `getSimpleName()` + `code()` okur).

### Step 5.2 — ResultHttp
**File emit:** `gen/java/app/ResultHttp.java`
**Action:** `static ResponseEntity<?> toHttp(Result<?>)` — switch pattern-matching: Success→200(value),
NotAuthenticated→401 `{reason}`, NotAuthorized→403 `{reason}`, NotValid→400 `{errors}`,
NotProcessable→422 `{code,message}`, ServerError→500 `{message}`. Process-global
`static UnaryOperator<ResponseEntity<?>> override` hook'u (default identity).

### Step 5.3 — Errors katalogları
**File emit:** `gen/java/app/{module}/Errors.java` — modülde error varsa: her error
`public static final String {Id} = "{Id}";` + `// resultType: {ResultType}` yorumu.
→ `report.realized("error", id)`.

### Step 5.4 — Types + Events
**File emit:** `gen/java/app/{module}/{TypeId}.java` — kind=="enum" → `public enum {Id} { A, B }`;
diğer kind → `public record {Id}(...)` (alanlar Naming.javaType ile). → `report.realized(t.kind(), id)`.
**Type-level ext** (`t.ext()` varsa): her ext → prelude yorum + `report.realized("@{ns}.{name}", typeId)`
+ `report.policy("{ns}-realization", ...)` (.NET `TypesFile` → `ExtComment(t.Ext, t.Id)` paritesi;
T3.4'ün entity-level ext deseniyle SİMETRİK; fixture `AuditMeta` composite'inde `@schema.versioned`
canlı örnek — realize edilmezse census `("@schema.versioned","AuditMeta")` çiftini silentDrop yapar,
T4.5 ZeroDropTest kırılır). Type-**FIELD** ext (owner `{typeId}.{field}`) T4.5 sweep'inde kalır.
`gen/java/app/{module}/{EventId}.java` — payload alanlı `public record`. + kök `gen/java/app/EventBus.java`:
`public interface EventBus { void publish(Object event); }` + `OutboxEventBus implements EventBus`
(gövde `throw new UnsupportedOperationException("outbox: doldurulacak-degil — altyapı stub")` DEĞİL:
.NET paritesi = NotImplemented stub; yorumla işaretle) + Bootstrap'a bean kaydı (yalnız events>0).
→ `realized("event", id)`.

### Step 5.5 — Testler
**File:** `gen-spring/src/test/java/techgen/spring/ResultTypesEmitTest.java`
- fixture emit → `Result.java` + 6 alt-tip + Page + Unit dosyaları var; içerikte `sealed interface` +
  `permits` satırı.
- `Errors.java` (Billing): `DuplicateInvoice` sabiti + resultType yorumu.
- `InvoiceStatus` enum 3 değerli; `Money` record 2 alanlı `BigDecimal amount`.
- `InvoiceCreated` record + EventBus + Bootstrap kaydı.
- build-report: ("error","DuplicateInvoice"), ("enum","InvoiceStatus"), ("composite","Money"),
  ("event","InvoiceCreated"), ("@schema.versioned","AuditMeta") realized.

## 6. Acceptance tests
### 6.1 `mvn -q -pl gen-spring test` → exit 0.
### 6.2 Pozitif — emit edilen `Result.java` + alt tipler `javac`-geçerli: test, üretilen gen/java
ağacını tek başına `javax.tools.JavaCompiler` ile derler (Spring bağımlılıksız dosyalar: Result ailesi,
Errors, enum) → 0 diagnostic.
### 6.3 Negatif — bilinmeyen resultType'lı error için fabrika davranışı T3.7'de; burada resultType
yorumunun birebir manifest değerini taşıdığı assert'lenir (uydurma değer yok).

## 7. Out of scope (DO NOT)
- Hata FABRİKALARI (HandlerBase içi) — T3.7
- Entity/JPA — T3.4
- Subscription consumer — T4.2

## 8. Anti-patterns
- DO NOT alt-tip adlarını Java-idyomatikleştir (Ok/Err, Failure...) — 6 ad conformance sözleşmesi.
- DO NOT `NotValid.errors` için özel tip — `Map<String,String>` birebir.
- DO NOT Page'i Spring `Page`/`Pageable` ile karıştır — kendi record'ımız (framework icadı yok, bağımlılık sızmaz).
- DO NOT enum değerlerini yeniden-case'le — manifest'teki halleriyle (Draft/Issued/Paid).

## 9. Definition of Done
- [ ] 5.1-5.4 emisyonları + realized çağrıları mevcut
- [ ] ≥8 test; javac in-memory derleme testi dahil
- [ ] 6.1-6.2 koşuldu, çıktı okundu
- [ ] `git status`: yalnız gen-spring

## 10. Self-check
1. Alt-tip adlarının ve `code()` accessor'ının conformance sözleşmesiyle (referans §A.3) eşleştiğini doğruladım mı?
2. javac testini gerçekten koştum mu (assert diagnostics boş)?
3. realized çağrılarını build-report assert'leriyle kanıtladım mı?
4. Page/Unit dahil tüm dosyaların GenHeader taşıdığını kontrol ettim mi?
5. Allowlist dışı dosya var mı?
