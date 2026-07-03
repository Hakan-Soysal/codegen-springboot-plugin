# T2.1 🔴 — GmBuilder + TypeEnv + nötr ExprWalk

## 1. Goal
`GmBuilder.build(manifest, contract)` join'ini, `GenerationModel`/`GmOperation`/`TypeEnv` IR'ını ve
ExprNode'un dil-nötr yürüyüşünü (`ExprWalk`) gen-core'a ekle.

## 2. Why
Davranış sözleşmesi §5: JoinError koşulları, ordinal sıralama (determinizmin kökü) ve
isCommand/WriteTarget türetimi burada. Yanlış sıralama = tüm golden'lar başka; yanlış join =
sessiz eksik business bağı. Semantically subtle + downstream-impact maksimum.

## 3. Inputs (must read fully before editing)
- `docs/referans/gen-core-davranis-sozlesmesi.md` §5 (GmBuilder/TypeEnv), §3 (ExprBuild Walk kuralları), §9 (determinizm)
- **Pattern (READ-ONLY):** CoreTemplate1 `src/Gen.Core/Pipeline/GmBuilder.cs`, `Gm/GenerationModel.cs`,
  `Predicate/ExprBuild.cs`
- T1.2 model record'ları; T1.3 Loader (testlerde kullanılacak)
- `fixtures/manifest.json` + `fixtures/operations.json`

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q -pl gen-core test                                          # expected: exit 0
ls gen-core/src/main/java/techgen/core/pipeline/Loader.java       # expected: var (T1.3 done)
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — GM IR
**File:** `gen-core/src/main/java/techgen/core/gm/`
**Action:** Add — `GenerationModel(mode, modules, operations:List<GmOperation>, entities, types,
events, subscriptions, errors, externals, callEdges, deployables, uncharted, env:TypeEnv,
testPlan:TestPlan)`; `GmOperation(op:OperationJson, business:ContractOp /*nullable*/, isCommand:boolean)`
+ kısayollar `id()`, `module()`.
**Not:** `testPlan` alanı T2.2'de dolacak — bu task'ta `TestPlan.empty()` placeholder'ı ile derlenir
(T2.2 bu tipe sahip; T2.1 ile T2.2 PARALEL koşacaksa PM sıralamayı T2.2→T2.1 yapmalı ya da bu task
geçici boş-record tanımlar ve T2.2 onu genişletir — executor bu kararı raporlar).

### Step 5.2 — GmBuilder
**File:** `gen-core/src/main/java/techgen/core/pipeline/GmBuilder.java`
**Action:** Add — akış birebir:
1. `linked = "linked".equals(m.mode())`.
2. `linked && m.contract()!=null && contract==null` → `JoinError("linked mod ama operations.json çözülemedi: "+m.contract())`.
3. Linked'de contract op/entity Id-index'leri; standalone'da boş.
4. Operations: Id ordinal (`String::compareTo`) sıralı; her biri `buildOp`:
   - linked && op.realizes()!=null && index'te yok → `JoinError("operation '"+id+"' realizes '"+r+"' operations.json'da yok")`
   - `isCommand = !creates.isEmpty() || !updates.isEmpty() || !deletes.isEmpty()`
5. Entity realizes denetimi: linked'de her rid çözülmeli, yoksa `JoinError("entity '"+id+"' realizes '"+rid+"' operations.json'da yok")`.
6. Tüm koleksiyonlar sıralı: Modules/Deployables/Externals/Uncharted=name; Entities/Types/Events/Errors=id;
   Subscriptions=(event.module, event.name, consumer.op); CallEdges=(from, to.system, to.op).
7. TypeEnv kur: opParams (opId→paramName→type), entityFields (entityId→fieldName→type).

### Step 5.3 — TypeEnv
**File:** `gen-core/src/main/java/techgen/core/gm/TypeEnv.java`
**Action:** Add — `writeTarget(GmOperation)`: creates∪updates∪deletes **ilk elemanı** (sırayla creates,
updates, deletes listeleri gezilir), yoksa null. `resolvePath(gm, path, opId, entityId)`:
boş→null; `path[0]=="actor"`→"String"; `path[0]=="resource"` && opId→writeTarget entity'sinin
`path[1]` alan tipi (yoksa null); aksi bare: önce op param, sonra entity field; yoksa null.

### Step 5.4 — ExprWalk (dil-nötr yürüyüş)
**File:** `gen-core/src/main/java/techgen/core/predicate/ExprWalk.java`
**Action:** Add — render'ı **callback'e** bırakan yürüyüş (T3.5 Java render'ı bunu kullanır):
- Desteklenen: BinaryNode (her zaman parantezli, and→AND or→OR cmp/arith→op), PathNode, LiteralNode.
- AggNode/CallNode/DurationNode → `UnsupportedConstruct("unsupported expr node: ...")`.
- Op'suz cmp/arith → `UnsupportedConstruct`.
- Tip-baskınlık yardımcıları: `typeOf(node, resolveType)` — Decimal > Double > Int; literal tam-sayı
  double → "Int". `inferLiteralTypes(root)` — karşılaştırma bağlamından path tip-ipucu.
- Distinct path toplama (görülme sırasıyla) + collision-safe camel-join: `["resource","creditLimit"]`
  → `resourceCreditLimit`.

### Step 5.5 — Testler
**File:** `gen-core/src/test/java/techgen/core/pipeline/GmBuilderTest.java` + `ExprWalkTest.java`
**Action:** Add:
- fixture join: CreateInvoice.business!=null, kind="command"; GetInvoice.business==null; isCommand
  CreateInvoice/WriteAuditLog=true, GetInvoice/ListInvoices=false.
- op sırası: [CreateInvoice, GetInvoice, ListInvoices, WriteAuditLog] (ordinal).
- JoinError üçlüsü: (a) linked+contract-null, (b) op realizes çözülmez, (c) entity realizes çözülmez —
  mini-manifest mutasyonlarıyla, mesaj içerikleri assert'li.
- standalone: mini-manifest mode="standalone" → join atlanır, business null.
- TypeEnv: resolvePath(["amount"], op=CreateInvoice) → "Decimal"; (["resource","creditLimit"]) →
  writeTarget=Invoice'ta alan yok → null; (["actor","tenant"]) → "String".
- ExprWalk: agg/call/duration → UnsupportedConstruct; and/or parantezleme; distinct path bir kez.

## 6. Acceptance tests
### 6.1 `mvn -q -pl gen-core test` → exit 0.
### 6.2 Pozitif — fixture GM sıra/join assert'leri geçiyor.
### 6.3 Negatif — üç JoinError testi mesajlarıyla geçiyor.

## 7. Out of scope (DO NOT)
- TestPlanBuilder içeriği — T2.2 (bu task yalnız boş-placeholder tipine referans verebilir)
- Java'ya render (BigDecimal.compareTo, equals) — T3.5
- BuildReport/Census — T2.3

## 8. Anti-patterns
- DO NOT `Comparator.naturalOrder()` yerine locale-duyarlı Collator kullan — sıralama UTF-16
  kod-birimi (`String::compareTo`) olmalı.
- DO NOT isCommand'ı contract `kind`'ından türet — manifest access 4-anahtarından türetilir.
- DO NOT writeTarget için "en mantıklı entity"yi seç — sözleşme İLK eleman der (deterministik).
- DO NOT ExprWalk'ta string-concatenation ile Java kodu üret — render callback'e bırakılır (dil-nötr kal).

## 9. Definition of Done
- [ ] 5.1-5.4 sınıfları mevcut; javadoc'ta sözleşme referansı
- [ ] 5.5: ≥12 test
- [ ] 6.1-6.3 koşuldu, çıktı okundu
- [ ] TestPlan placeholder kararı task raporuna yazıldı
- [ ] `git status`: yalnız gen-core

## 10. Self-check
1. Üç JoinError koşulunun ÜÇÜNÜ de negatif testle kanıtladım mı?
2. Sıralamanın her koleksiyon için anahtarını sözleşmeyle karşılaştırdım mı?
3. ExprWalk'a dil-özel hiçbir şey sızdı mı (BigDecimal/equals kelimeleri geçiyor mu — geçmemeli)?
4. Testleri gerçekten koştum mu?
5. Allowlist dışı dosya var mı?
