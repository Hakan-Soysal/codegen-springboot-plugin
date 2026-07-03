# T1.2 🔴 — Manifest/Contract POJO'ları + Jackson yapılandırması

## 1. Goal
manifest.json ve operations.json'ın TAM Java modelini (record'lar) ve merkezî `techgen.core.Json`
ObjectMapper fabrikasını ekle; iki fixture çifti kayıpsız parse edilsin.

## 2. Why
SPEC §2/§5: girdi sözleşmesi .NET kardeşiyle ORTAK — alan eksik/yanlış-tipli olursa downstream her
şey (GM, census, emisyon) sessizce sapar. Şema davranış sözleşmesinde alan alan sabitlenmiştir;
bu task fidelity'nin temelidir (silent-fail + downstream-impact: kritik yol).

## 3. Inputs (must read fully before editing)
- `docs/referans/gen-core-davranis-sozlesmesi.md` §1 (ManifestJson TAM şema), §2 (Contract TAM şema), §4 (Json ayarları)
- **Pattern (READ-ONLY):** CoreTemplate1 `src/Gen.Core/Model/Manifest.cs`, `Model/Contract.cs`, `Json.cs`
- `fixtures/manifest.json` + `fixtures/operations.json` (tam) ve `fixtures/studyo.*.json` (göz gezdir — ölçek)
- T1.1 çıktısı: `techgen.core.model.ExprNode` (GuardedExpr.ast bunu kullanır)

## 4. Pre-conditions (verify before starting; all must succeed)
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q -pl gen-core test                                    # expected: exit 0 (T1.1 yeşil)
ls gen-core/src/main/java/techgen/core/model/ExprNode.java  # expected: var (T1.1 done)
```
If any check fails, STOP and report.

## 5. Changes (atomic numbered steps)

### Step 5.1 — `techgen.core.Json`
**File:** `gen-core/src/main/java/techgen/core/Json.java`
**Action:** Add — statik `ObjectMapper mapper()`:
- `FAIL_ON_UNKNOWN_PROPERTIES=false` (bilinmeyen alan sessiz)
- `ACCEPT_CASE_INSENSITIVE_PROPERTIES=true`
- ExprNode (de)serializer'ları modül olarak kayıtlı
- `parse(String, Class<T>)`: null dönerse exception (null-deserialize yasak)

### Step 5.2 — Manifest modeli
**File:** `gen-core/src/main/java/techgen/core/model/` altında record'lar
**Action:** Add — davranış sözleşmesi §1'deki HER record'ı birebir alan listesiyle yaz:
`ManifestJson` (16 alan: mode, contract, meta, deployables, modules, operations, entities, types,
errors, events, subscriptions, externals, uncharted, callEdges, coverage), `Meta`, `Deployable`,
`ModuleDecl`, `ParamJson`, `SignatureJson`, `ServingArg`, `ServingJson`, `AccessJson`
(**4 liste: reads/creates/updates/deletes**), `GuardedExpr(text, ast:ExprNode, guardRef)`,
`Consistency`, `Abac(permit:ExprNode)`, `Idempotent`, `PaginationKey`, `Pagination`,
`ExtJson(ns, name, args:Map<String,JsonNode>)`, `OperationJson` (21 alan — sözleşmedeki sırayla),
`SourceOfTruth`, `EntityFieldJson`, `EntityJson`, `FieldJson`, `TypeJson`, `ErrorJson`, `EventJson`,
`EventRef`, `ConsumerRef`, `SubscriptionJson`, `CallTarget`, `CallEdgeJson`, `BoundaryOpJson`,
`ExternalJson`, `UnchartedEntity`, `UnchartedType`, `UnchartedJson`, `Coverage`.
Nullable alanlar Java'da referans tipi + `@Nullable` yorum; liste alanları eksikse boş-liste'ye
normalize et (ctor'da `Objects.requireNonNullElse(x, List.of())`).

### Step 5.3 — Contract modeli
**File:** aynı paket
**Action:** Add — `ContractFile(meta, operations, entities, actors, relations:List<JsonNode>,
processes, flows)`, `ContractMeta`, `ContractSignature`, `ContractGuard`, `ContractEffect(kind,
target:JsonNode, expr:ExprNode, text)` (**target toleranslı raw** — string VEYA array),
`ContractAccess(writes, reads)` (**2 liste**), `ContractOp`, `ContractEntity`, `ContractActor`,
`ProcessJson`, `ProcessStage`, `FlowJson`, `FlowStep`.

### Step 5.4 — POJO testleri
**File:** `gen-core/src/test/java/techgen/core/model/ModelParseTest.java`
**Action:** Add:
- fixture manifest parse → sayımlar: operations=4, entities=2, types=4, errors=1, events=1,
  subscriptions=1, externals=1, uncharted=1, callEdges=2; `mode=="linked"`.
- CreateInvoice: access.creates=["Invoice"], throws=["DuplicateInvoice"], idempotent.keys=["customerId"],
  ext 2 adet, validation[0].ast BinaryNode.
- contract parse → biz.CreateInvoice access.writes=["biz.Invoice"] (**2-anahtar**).
- `ContractEffect.target` hem string hem path-array kabulü (iki mini-JSON ile — regression).
- studyo çifti parse → exception yok; operations sayısı 43.
- bilinmeyen alan toleransı: `{"mode":"linked","fooUnknown":1,...}` mini-manifest parse OLUR.

## 6. Acceptance tests
### 6.1
```bash
mvn -q -pl gen-core test    # expected: exit 0
```
### 6.2 Pozitif — yukarıdaki sayım assert'leri geçiyor (test raporunda isimleri görülür).
### 6.3 Negatif — `Json.parse("null", ManifestJson.class)` → exception (null-deserialize testi).

## 7. Out of scope (DO NOT)
- Loader dosya-IO'su — T1.3
- GM/join/TypeEnv — T2.1
- Şemaya alan EKLEME/çıkarma — sözleşme ortak; sapma gerekirse DUR + PM'e sor

## 8. Anti-patterns
- DO NOT alan atla ("fixture'da yok" diye) — şema sözleşmesi fixture'dan GENİŞTİR (ör.
  `EntityFieldJson.targetModule/crossModule` fixture'da hiç görünmez ama şemadadır).
- DO NOT `ExtJson.args` için tipli sınıf yaz — toleranslı `Map<String,JsonNode>` sözleşmesi.
- DO NOT `ContractEffect.target`'ı String yap — string VEYA array gelir; `JsonNode` şart.
- DO NOT Jackson'ın snake_case/kebab stratejilerini aç — camelCase default + case-insensitive yeter.
- DO NOT record'larda Lombok kullan — sade Java 21 record.

## 9. Definition of Done
- [ ] Step 5.1-5.3: tüm record'lar sözleşmedeki alan adları/tipleriyle mevcut
- [ ] Step 5.4: ≥10 test; studyo dahil
- [ ] 6.1-6.3 koşuldu, çıktı okundu
- [ ] `git status`: yalnız gen-core

## 10. Self-check
1. Sözleşme §1/§2'deki HER record için Java karşılığı var mı — tek tek saydım mı?
2. AccessJson 4-liste, ContractAccess 2-liste asimetrisini test ediyor muyum?
3. Testleri gerçekten koştum mu, çıktıyı okudum mu?
4. Bilinmeyen-alan toleransını pozitif testle kanıtladım mı?
5. Allowlist dışına dosya yazdım mı?
