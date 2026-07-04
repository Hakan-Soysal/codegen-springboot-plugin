# T2.2 🔴 — TestPlanBuilder

## 1. Goal
TestPlan IR'ını (`TestPlan/ProcessTest/ScenarioTest/PrereqStep/PrereqKind`) ve
`TestPlanBuilder.build(contract, manifestOps)` türetimini gen-core'a ekle.

## 2. Why
Davranış sözleşmesi §6: prereq sınıflaması (Single/Ambiguous/Missing — asla creator seçilmez),
deterministik topo-sort + döngü fallback'i ve "RunSequence içi sıra Items sırasıdır, ordinal DEĞİL"
kuralı kolay yanlış yazılır ve compile geçer (silent-fail). T7.1 test emisyonunun tek girdisi.

## 3. Inputs
- `docs/referans/gen-core-davranis-sozlesmesi.md` §6 (tam algoritma)
- **Pattern (READ-ONLY):** CoreTemplate1 `src/Gen.Core/Pipeline/TestPlanBuilder.cs`, `Gm/TestPlan.cs`
- T1.2 Contract modeli (ProcessJson/FlowJson/FlowStep); T1.2 OperationJson (access)
- `fixtures/studyo.operations.json` — process/flow içeren GERÇEK örnek (fixtures/operations.json'da
  process/flow YOK → boş-plan yolu)

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q -pl gen-core test    # expected: exit 0 (T1.2 done)
python3 -c "import json;d=json.load(open('fixtures/studyo.operations.json'));print(len(d.get('processes',[])),len(d.get('flows',[])))"
# expected: her ikisi > 0
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — IR record'ları
**File:** `gen-core/src/main/java/techgen/core/gm/TestPlan.java` (+ komşu record'lar)
**Action:** Add:
```java
public record TestPlan(List<ProcessTest> processTests, List<ScenarioTest> orphanFlowTests,
                       List<ScenarioTest> orphanOpTests) { public static TestPlan empty(){...} }
public record ProcessTest(String processId, String entity, List<String> runSequence,
                          List<PrereqStep> prerequisites, List<String> writeSet) {}
public record ScenarioTest(String id, String scope, List<String> runSequence,
                           List<PrereqStep> prerequisites, List<String> writeSet) {} // scope: "OrphanFlow"|"OrphanOp"
public enum PrereqKind { SINGLE, AMBIGUOUS, MISSING }
public record PrereqStep(String entity, String creatorOp, PrereqKind kind) {} // creatorOp yalnız SINGLE'da dolu
```

### Step 5.2 — Builder
**File:** `gen-core/src/main/java/techgen/core/pipeline/TestPlanBuilder.java`
**Action:** Add — algoritma birebir:
1. `contract==null || processes==null || flows==null` → `TestPlan.empty()`.
2. `opById` (dup → **son kazanır**); `creators`: entity → creates-eden op Id listesi (op'lar Id-ordinal
   gezilir; distinct).
3. `derive(runSeq)`: produced=creates birleşimi; needed=(reads∪updates)−produced; writeSet=
   creates∪updates∪deletes ordinal-distinct; manifest'te olmayan opId → skip.
   Sınıflama: creators==1→SINGLE(creatorOp=creators[0]); >1→AMBIGUOUS(null); 0→MISSING(null).
   SINGLE'lar topo-sort: kenar e1→e2 ⟺ creators[e1][0]'ın (reads∪updates)'ı needed-SINGLE
   kümesindeki e2'yi içerir; her adımda bağımlılığı tükenmiş **en küçük ordinal**; hiçbiri uygun
   değilse (döngü) kalanların en küçük ordinali seçilir (sonsuz döngü imkânsız).
   AMBIGUOUS/MISSING sona, entity-id ordinal.
4. `flowById` (dup → son kazanır). ProcessTests: process Items'ında `stage.flow!=null` → flowsInProcess'e;
   dangling flow (flowById'de yok) → **skip, throw yok**; flow Items'ında `step.target!=null` sırayla
   RunSequence'e (**Items sırası KORUNUR**). Sonra derive.
5. `opsInFlow` = TÜM flow'ların target'ları (küme farkından ÖNCE).
6. OrphanFlowTests = flows − flowsInProcess (scope="OrphanFlow"); OrphanOpTests = contract.operations
   − opsInFlow (tek-op runSeq, scope="OrphanOp").
7. Sonuç listeleri ordinal (processId / id).

### Step 5.3 — Testler
**File:** `gen-core/src/test/java/techgen/core/pipeline/TestPlanBuilderTest.java`
**Action:** Add:
- fixtures/operations.json (process/flow yok) → empty plan (üç liste boş).
- studyo çifti → processTests.size()>0; bir process'in runSequence'i contract'taki Items sırasıyla
  eşleşiyor (ordinal DEĞİL — bunu kanıtlayan assert: sequence sıralanmış halinden FARKLI ise birebir
  Items sırası; değilse ikinci bir process seç).
- Sentetik mini-contract testleri: (a) tek-creator → SINGLE + creatorOp dolu; (b) iki-creator →
  AMBIGUOUS + creatorOp null; (c) sıfır-creator → MISSING; (d) topo-sort: e1 creator'ı e2'yi okuyor
  → e2 önce; (e) döngü (e1↔e2) → en-küçük-ordinal fallback, sonlanır; (f) dangling flow → skip;
  (g) dup op id → son kazanır.

## 6. Acceptance tests
### 6.1 `mvn -q -pl gen-core test` → exit 0.
### 6.2 Pozitif — studyo planı: üç listenin sayıları task raporuna yazıldı; ≥1 SINGLE prereq örneği
assert'lendi.
### 6.3 Negatif — AMBIGUOUS/MISSING'de `creatorOp==null` assert'leri geçiyor (creator SEÇİLMEDİĞİ kanıtı).

## 7. Out of scope (DO NOT)
- Test EMİSYONU (JUnit iskeleti/Fixture) — T7.1
- GmBuilder entegrasyonu dışında GM değişikliği — T2.1'in alanına dokunma (yalnız
  `GenerationModel.testPlan`'ı gerçek build ile doldur)

## 8. Anti-patterns
- DO NOT AMBIGUOUS'ta "ilk creator'ı seç" — sözleşme ASLA seçme der; test-prereq Unsupported yolunun
  (T7.1) ön-şartı budur.
- DO NOT RunSequence'i sıralamaya sok — contract-meaningful Items sırası.
- DO NOT topo-sort için genel graph-lib ekle — 30 satırlık deterministik algoritma; yeni bağımlılık yasak.
- DO NOT döngüde exception fırlat — en-küçük-ordinal fallback sözleşmesi.

## 9. Definition of Done
- [ ] IR record'ları + builder mevcut
- [ ] ≥10 test (7 sentetik senaryo + fixture + studyo)
- [ ] 6.1-6.3 koşuldu; studyo sayıları raporda
- [ ] `GenerationModel.testPlan` artık gerçek build sonucu taşıyor
- [ ] `git status`: yalnız gen-core

## 10. Self-check
1. "Asla creator seçilmez" kuralını her iki dalda (AMBIGUOUS, MISSING) negatif testle kanıtladım mı?
2. RunSequence sırasının Items sırası olduğunu SIRALANMIŞTAN FARKLI bir örnekle mi kanıtladım?
3. Döngü fallback'inin sonlandığını test ettim mi?
4. Testleri gerçekten koştum mu, çıktıyı okudum mu?
5. Allowlist dışı dosya var mı?
