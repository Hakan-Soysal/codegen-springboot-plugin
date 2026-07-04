# T4.5 🔴 — Kalan dispositionlar: saga/calls/host/sourceOfTruth-policy/visibility/serving/module-ext süpürmesi

## 1. Goal
Fixture'ın (ve şemanın) kalan tüm construct'larını birebir disposition'la kapat ve TÜM policy
kayıtlarını tamamla: calls/compensate (saga), deployable/Host, visibility policy, non-rest serving
→ explicit Unsupported, module-ext prelude, param-ext'ler. Bu task sonunda fixture emit'i
**0 silentDrop**.

## 2. Why
INV-7'nin "tam parite" kanıtı bu süpürmede kapanır. Non-rest serving'in `Unsupported` (drop DEĞİL)
oluşu exit-code sözleşmesinin canlı örneğidir; atlanırsa gate fixture'da drop üretir ve tüm zincir
(T5.1 exit, T6.1 golden) kilitlenir.

## 3. Inputs
- `SPEC.md` §6.7 (policy listesi birebir)
- `docs/referans/gen-dotnet-emisyon-sozlesmesi.md` §7 (disposition tablosu TAM) + §1 (Host/Boundary yorumları)
- **Pattern (READ-ONLY):** CoreTemplate1 `DotnetEmitter.cs` 791-824 (HostFile), 887-955 (Consistency/
  Boundary saga yorumları), 140-156 (op-döngüsü disposition'ları)
- T2.3 Census listesi (kapatılacak hedef); T4.4 Boundary dosyaları

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q -pl gen-spring test    # expected: exit 0 (T4.1-T4.4 yeşil)
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — calls + compensate (saga)
**Action:** her callEdge: Boundary dosyasına (T4.4) yorum satırı `// calls: {from} -> {to.system}.{to.op}
({kind})` → `realized("calls", from)`. Compensate'li edge: `// saga: compensate = {system}.{op}
(ters-sıra/LIFO — doldurucu playbook'u)` → `realized("compensate", from)` + policy
`saga-orchestration-state=in-memory`.

### Step 5.2 — Host/deployables
**File emit:** `gen/java/app/DeploymentTopology.java` (deployables>0) —
`public static final Map<String, List<String>> DEPLOYABLES = Map.of("BillingService", List.of("Billing"), ...)`
(name-ordinal; Map.of sıra garanti etmez → `LinkedHashMap` statik-init ya da sıralı entry listesi —
determinizm kaynak-metin düzeyindedir). Deployable ext → yorum + `realized("@{ns}.{name}", name)`.
→ `realized("deployable", name)` + policy `deployment-topology=modular-monolith host`.

### Step 5.3 — visibility + serving süpürmesi
**Action:** her op: `realized("visibility", opId)` zaten T3.6'da; policy `visibility=exposed-route /
internal-no-route` bir kez. Non-rest serving (grpc/queue/...): `report.unsupported("serving",
"{op}:{proto}", "REST-only binding")` + policy `serving-{proto}=unsupported`. Fixture'da
CreateInvoice `@grpc()` bunun canlı testi.

### Step 5.4 — module-ext + param-ext + kalan ext site'ları
**Action:** module.ext → `gen/java/app/{module}/ModulePrelude.java` yorum-dosyası (ya da Wiring'e
prelude yorumu — .NET Module.g.cs paritesi) → `realized("@{ns}.{name}", moduleName)`.
Param-ext (op signature + boundary + event payload + entity field + type field): ilgili emisyon
noktasına yorum + census owner şablonuyla realized. Policy `{ns}-realization=comment-prelude` ns
başına bir kez.

### Step 5.5 — 0-drop kanıtı + policy seti testi
**File:** `gen-spring/src/test/java/techgen/spring/ZeroDropTest.java`
- fixture emit + `Completeness.check` → `report.silentDrops().isEmpty()` **assert**; değilse test
  mesajı drop listesini basar (teşhis).
- policies map'i şu anahtarları içeriyor (assert): deployment-topology, saga-orchestration-state,
  consistency-mode, dedup-store, pagination-strategy, cursor-token, trigger-wiring, http-binding,
  guard-linkage, source-of-truth, uncharted-realization, visibility, serving-grpc, audit-realization,
  metric-realization, trigger-realization (fixture ext ns'leri), http-realization, sensitivity-realization,
  crypto-realization, schema-realization, deploy-realization, ucparam-realization.
  (`http-binding` policy'si T3.7'de `@http` op-ext için emit edilir — SPEC §6.7; bu test onun
  varlığını da doğrular.)
- ("serving","CreateInvoice:grpc") status==unsupported (drop değil).
- `clean()==false` (unsupported var) AMA silentDrops boş — Clean≠exit ayrımının kanıtı.

## 6. Acceptance tests
### 6.1 `mvn -q -pl gen-spring test` → exit 0.
### 6.2 Pozitif — ZeroDropTest yeşil; policy seti tam.
### 6.3 Negatif — grpc serving'in unsupported (SILENT_DROP değil) olduğu status-assert'i.

## 7. Out of scope (DO NOT)
- Exit-code'un kendisi — T5.1 (bu task yalnız report'u doğru doldurur)
- Golden snapshot — T6.1
- studyo 0-drop — T6.3 (bu task yalnız birincil fixture'ı garanti eder)

## 8. Anti-patterns
- DO NOT "yorum emit etmek realize saymak için yetmez" diye düşünüp construct'ı atla — .NET paritesi:
  bazı construct'ların v1 disposition'u AÇIK YORUM + policy'dir (calls/saga/sourceOfTruth); önemli olan
  RAPORLANMIŞ olması (INV-7 unsupported≠drop felsefesi).
- DO NOT Map.of ile deterministik-görünüm — kaynak metinde sıralı üretim şart.
- DO NOT policy değerlerini yeniden-adlandır — SPEC §6.7 listesi birebir (skill rung-1 çözümleri
  bu adlara bağlanır).
- DO NOT drop listesi boş çıksın diye census'u değiştir — census T2.3'ün sözleşmesi; kapatma EMİTTER
  tarafında yapılır.

## 9. Definition of Done
- [ ] calls/compensate/host/visibility/serving/ext süpürmesi + tüm policy'ler mevcut
- [ ] ZeroDropTest yeşil (fixture 0 silentDrop) — çıktı okundu
- [ ] grpc-unsupported + clean-false-drop-yok kanıtları testli
- [ ] `git status`: yalnız gen-spring

## 10. Self-check
1. Census listesini (T2.3) satır satır gezip her construct'ın emitter karşılığını işaretledim mi?
2. ZeroDrop assert'inin drop LİSTESİNİ bastığını (teşhis edilebilirlik) doğruladım mı?
3. Policy adlarını SPEC §6.7 ile diff'ledim mi?
4. Testleri gerçekten koştum mu?
5. Allowlist dışı dosya var mı?
