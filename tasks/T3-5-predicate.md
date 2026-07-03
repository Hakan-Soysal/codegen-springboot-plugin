# T3.5 🔴 — Java predicate renderer + Guards + Invariants

## 1. Goal
ExprNode→**tipli Java** predicate render'ını (`JavaPredicateRenderer`) ve Guards
(`{Op}Guards.validation0/rule0/permit0` + input record'ları) ile `{Entity}Invariants` emisyonlarını ekle.

## 2. Why
INV-4'ün Java'ya özgü en ince noktası: `BigDecimal` karşılaştırması operatörle YAZILAMAZ
(`compareTo`), String eşitliği `equals` ister — yanlış render compile GEÇER ama yanlış davranır
(autoboxing `==` referans karşılaştırması!) ya da hiç derlenmez. Semantically subtle + silent-fail:
kritik yolun en riskli task'ı.

## 3. Inputs
- `SPEC.md` §6.5 (render kuralları), §3 INV-4
- `docs/referans/gen-core-davranis-sozlesmesi.md` §3 (ExprBuild: tip-baskınlık, InferLiteralTypes,
  parantezleme, distinct path)
- `docs/referans/gen-dotnet-emisyon-sozlesmesi.md` §1 (Guards.g.cs/Invariants), §7 (validation/rule/
  permit/guardRef/invariant satırları)
- **Pattern (READ-ONLY):** CoreTemplate1 `DotnetEmitter.cs` 1216-1331 (GuardsFile/ExtComment),
  1296-1319 (InvariantsFile), `Gen.Core/Predicate/ExprBuild.cs`
- T2.1 `ExprWalk` + `TypeEnv`; T3.2 `Naming`

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q -pl gen-spring test                                        # expected: exit 0 (T3.4 yeşil)
ls gen-core/src/main/java/techgen/core/predicate/ExprWalk.java    # expected: var (T2.1 done)
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — JavaPredicateRenderer
**File:** `gen-spring/src/main/java/techgen/spring/JavaPredicateRenderer.java`
**Action:** Add — `render(ExprNode root, Function<List<String>,String> resolveType)` →
`(String expr, List<List<String>> paths)`. ExprWalk callback'lerini gerçekler:
- Path → `input.{camelJoin(path)}()` (input record accessor'ı; camelJoin collision-safe:
  `["resource","creditLimit"]`→`resourceCreditLimit`).
- Karşılaştırma (cmp) tip-duyarlı:
  - iki taraftan biri **Decimal** bağlamlı → `({L}.compareTo({R}) {op} 0)`; literal sayı Decimal
    bağlamında `new BigDecimal("{v}")`.
  - **String** bağlamlı eşitlik (`=`) → `({L}.equals({R}))`; eşitsizlik `!=` → `(!{L}.equals({R}))`.
  - primitif sayısal (Int/Double) → doğal operatörler; `=`→`==`.
  - bool literal → `true|false`.
- and→`&&`, or→`||`; her binary parantezli; arith (+,-,*,/) Decimal bağlamında `.add/.subtract/
  .multiply/.divide` — fixture'da arith yoksa bile birim testle kanıtlanır.
- Tip çözümü: önce `resolveType` (TypeEnv), null dönerse `ExprWalk.inferLiteralTypes` ipuçları,
  o da yoksa Double varsay (parite: .NET aynı sıra).
- AggNode/CallNode/DurationNode/op'suz binary → `UnsupportedConstruct` (yakalayan emitter
  `report.unsupported(...)` yazar — sessizlik yok).

### Step 5.2 — Guards emisyonu
**File emit:** `gen/java/app/{module}/{op}/{Op}Guards.java` (validation/rule/permit varsa)
**Action:** her validation[i] → `public static boolean validation{i}({Op}Validation{i}Input input)
{ return {expr}; }` + `public record {Op}Validation{i}Input({tipli alanlar — distinct paths})`;
rule → `rule{i}`; abac.permit → `permit0` (paths'te `actor.*` → String tip). guardRef'li ifade →
metot üstü `// guardRef: {id}` + policy `guard-linkage=build-time coverage link` +
`realized("guardRef", opId)`. → `realized("validation"|"rule"|"permit", opId)` (validation/rule
op-başına bir kez, .NET paritesi).

### Step 5.3 — Invariants emisyonu
**File emit:** `gen/java/app/{module}/{EntityId}Invariants.java` (invariants varsa) —
`public static boolean invariant{i}({tip} {alan})` biçiminde tipli statik metotlar (input =
invariant path'lerinin entity-field tipleri). → `realized("invariant", entityId)` (+guardRef varsa).

### Step 5.4 — Testler
**File:** `gen-spring/src/test/java/techgen/spring/PredicateRenderTest.java` + `GuardsEmitTest.java`
- `amount > 0` (Decimal) → `(input.amount().compareTo(new BigDecimal("0")) > 0)`.
- `amount <= resource.creditLimit` (iki Decimal path) → compareTo formu + input record'da
  `BigDecimal amount, BigDecimal resourceCreditLimit`.
- `seq > 0` (Int) → `(input.seq() > 0)` (compareTo YOK).
- String eşitlik: sentetik `status == "planli"` → `(input.status().equals("planli"))`.
- and/or parantezleme; distinct path bir kez.
- agg node → UnsupportedConstruct.
- fixture emit: `CreateInvoiceGuards.validation0/rule0` + iki input record; `WriteAuditLogGuards.
  validation0` (guardRef yorumlu) + `permit0` (actor.tenant String equals); `InvoiceInvariants.
  invariant0`; build-report entry'leri.
- **javac testi:** üretilen Guards+Invariants+input record'ları (Result ailesiyle birlikte) in-memory
  derleniyor → 0 diagnostic.

## 6. Acceptance tests
### 6.1 `mvn -q -pl gen-spring test` → exit 0.
### 6.2 Pozitif — compareTo/equals/primitif üç formun üçü de birebir string assert'li.
### 6.3 Negatif — agg/call/duration → UnsupportedConstruct; op'suz cmp → UnsupportedConstruct.

## 7. Out of scope (DO NOT)
- HandlerBase'in Guards'ı ÇAĞIRMASI — T3.6 (yalnız üyeler üretilir)
- Boundary validation (`{Ext}{Op}Validation`) — T4.4
- NotValid/NotProcessable eşlemesi (davranış) — doldurucu/conformance alanı

## 8. Anti-patterns
- DO NOT BigDecimal'ı `==`/`>` ile karşılaştır — compareTo formu ZORUNLU.
- DO NOT String'i `==` ile karşılaştır — `equals`; null-güvenliği için literal solda DEĞİL —
  input alanları contract gereği zorunlu; sıra `{L}.equals({R})` (parite ve okunabilirlik).
- DO NOT input record yerine Map/Object[] — tipli input INV-4'ün özü.
- DO NOT `double` literal'i BigDecimal'a `new BigDecimal(0.1)` ile çevir — **String ctor**
  (`new BigDecimal("0.1")`) şart (temsil hatası).
- DO NOT render'ı gen-core'a koy — dil-nötr ExprWalk gen-core'da kalır, Java render gen-spring'de.

## 9. Definition of Done
- [ ] Renderer + Guards + Invariants emisyonu mevcut
- [ ] ≥12 test; üç render formu + iki Unsupported yolu + javac derlemesi kanıtlı
- [ ] 6.1-6.3 koşuldu, çıktı okundu
- [ ] `git status`: yalnız gen-spring

## 10. Self-check
1. compareTo formunu HEM literal HEM path-path karşılaştırmasında test ettim mi?
2. BigDecimal String-ctor kuralına uydum mu (kod + test)?
3. Int yolunun compareTo'ya SAPMADIĞINI test ettim mi?
4. javac testini gerçekten koştum mu?
5. Allowlist dışı dosya var mı?
