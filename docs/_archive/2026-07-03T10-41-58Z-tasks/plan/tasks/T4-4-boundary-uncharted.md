# T4.4 🔴 — Boundary externals + client seam + boundary validation + Uncharted

## 1. Goal
External sistemleri `boundary/{Ext}` interface + human `{Ext}Client` seam'ine, caller-side boundary
validation'ları `{Ext}{Op}Validation` sınıflarına, uncharted sistemleri OWNED entity/type'larıyla
`uncharted/{Name}` stub'larına emit et.

## 2. Why
Boundary/uncharted disposition farkı incedir: external kendi entity'sini SAHİPLENMEZ, uncharted
SAHİPLENİR (owned POCO'lar). Boundary validation'ın caller-side oluşu (INV-4) ve serving'in yalnız
metadata-yorum oluşu .NET parite kurallarıdır; yanlışsa census kapanır ama anlam kayar (silent-fail).

## 3. Inputs
- `SPEC.md` §6.3 (Boundary/Uncharted satırları), §6.2 (boundary marker)
- `docs/referans/gen-dotnet-emisyon-sozlesmesi.md` §1 (Boundary.g.cs/BoundaryClientLogic/
  BoundaryValidation/Uncharted), §7 (external/boundary-op/uncharted satırları)
- **Pattern (READ-ONLY):** CoreTemplate1 `DotnetEmitter.cs` 913-1071 (BoundaryFile/
  BoundaryClientLogic/BoundaryValidation/UnchartedFile)
- T3.5 renderer (boundary validation'lar da tipli predicate); T3.6 slice deseni

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q -pl gen-spring test    # expected: exit 0 (T3.6 yeşil; T3.5 renderer mevcut)
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — Boundary interface
**File emit:** `gen/java/app/boundary/{Ext}.java` (externals>0 || callEdges>0 iken paket oluşur)
**Action:** `public interface {Ext}` — her boundary-op: `{RetJavaTip} {opCamel}({params});`
serving → metot üstü yorum (`// serving: {raw} — transport client sorumluluğu`). callEdges bu
external'a değiyorsa dosya başına saga/calls yorumları (T4.5 policy'leri). → `realized("external",
name)`, her op `realized("boundary-op", "{ext}.{op}")`, serving başına `realized("serving",
"{ext}.{op}:{proto}")`.

### Step 5.2 — Human client seam
**File emit (writeIfAbsent):** `src/main/java/app/boundary/{Ext}Client.java` —
`public class {Ext}Client implements {Ext}`; her metot:
`throw new UnsupportedOperationException("{Ext}.{op}: doldurulacak");`
Wiring/Bootstrap'ta `@Bean {Ext} {ext}Client()` kaydı (gen-owned). Handler'ı bu external'ı çağıran
op'ların (callEdges.from) HandlerBase'ine `protected final {Ext} {ext};` alanı + ctor param
(ctor-senkron kuralı).

### Step 5.3 — Boundary validation (caller-side)
**File emit:** `gen/java/app/boundary/{Ext}{OpPascal}Validation.java` — boundary-op'ta validation
varsa: `public static boolean validation{i}({tipli input record})` (renderer T3.5; tip çözümü
boundary-op signature paramlarından). → `realized("validation", "{ext}.{op}")`.

### Step 5.4 — Uncharted
**File emit:** `gen/java/app/uncharted/{Name}.java` — stub interface + `{Name}StubClient`
(UnsupportedOperationException stub, GEN-owned — human seam DEĞİL; .NET paritesi) + OWNED entity/type
sınıfları (düz POJO — JPA'ya BAĞLANMAZ) aynı dosya-komşuluğunda `uncharted/{Name}{Entity}.java`.
Policy `uncharted-realization=call-adapter stub + owned model` (değer .NET'tekiyle uyumlu; T4.5
policy süpürmesiyle senkron). → `realized("uncharted", name)` + boundary-op/validation/serving/
concurrency alt-kayıtları (census şablonlarıyla).

### Step 5.5 — Testler
**File:** `gen-spring/src/test/java/techgen/spring/BoundaryEmitTest.java`
- fixture: `boundary/PaymentGateway.java` interface — `charge(BigDecimal amount)` → `String`,
  `refund(String chargeId)` → `boolean`.
- `PaymentGatewayClient` human seam, marker'lı, ezilmiyor; Bootstrap/Wiring kaydı var.
- `PaymentGatewayChargeValidation.validation0` — `(input.amount().compareTo(new BigDecimal("0")) > 0)`.
- CreateInvoice HandlerBase'inde `PaymentGateway paymentGateway` alanı (callEdge from=CreateInvoice).
- `uncharted/LegacyLedger.java` + `LegacyLedgerStubClient` (gen-owned; src/ altında DOSYA YOK —
  negatif assert) + `LegacyLedgerLedgerEntry` POJO (@Entity YOK — negatif grep).
- build-report: ("external","PaymentGateway"), ("boundary-op","PaymentGateway.charge"),
  ("validation","PaymentGateway.charge"), ("serving","PaymentGateway.charge:rest"),
  ("uncharted","LegacyLedger") realized.
- E2E compile yeşil (@Tag e2e).

## 6. Acceptance tests
### 6.1 `mvn -q -pl gen-spring test` → exit 0.
### 6.2 Pozitif — boundary validation compareTo formu birebir; compile yeşil.
### 6.3 Negatif — uncharted client'ın human-seam OLMADIĞI (src/'de dosya yok) ve owned entity'nin
@Entity TAŞIMADIĞI assert'leri.

## 7. Out of scope (DO NOT)
- saga/compensate yorumları + policy'leri — T4.5
- Gerçek HTTP client implementasyonu — doldurucu alanı
- callEdges census kaydı — T4.5

## 8. Anti-patterns
- DO NOT external entity ICAT et — external sistem model sahibi değildir; yalnız uncharted owned
  entity/type üretir.
- DO NOT uncharted owned entity'yi JPA'ya bağla — düz POJO (persist sorumluluğu bizde değil).
- DO NOT boundary validation'ı client seam'in içine göm — caller-side ayrı statik sınıf (INV-4 +
  seam'in özgürlüğü).
- DO NOT uncharted stub'ı human seam yap — .NET paritesi gen-owned stub (fark: external=human doldurur,
  uncharted=bilinmeyen sistem, stub kalır).

## 9. Definition of Done
- [ ] 4 emisyon + HandlerBase boundary alanları + kayıtlar mevcut
- [ ] ≥9 test; iki negatif assert dahil
- [ ] E2E compile yeşil (çıktı okundu)
- [ ] `git status`: yalnız gen-spring

## 10. Self-check
1. external vs uncharted sahiplik farkını İKİ negatif assert'le kanıtladım mı?
2. Boundary validation'ın tip çözümünü boundary-op paramlarından yaptığımı test ettim mi?
3. Ctor-senkron kuralı sonrası compile hâlâ yeşil mi (gerçek koşum)?
4. Marker birebir mi (`{Ext}.{op}: doldurulacak`)?
5. Allowlist dışı dosya var mı?
