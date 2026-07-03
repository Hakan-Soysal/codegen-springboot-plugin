# Gen.Core Pipeline — Davranış Sözleşmesi Referansı (Java yeniden-yazım kaynağı)

> Bu doküman, `/Users/hakansoysal/Desktop/ClaudeCode Denemeler/CoreTemplate1/src/Gen.Core` kaynak kodunun
> tam okumasından çıkarılmış davranışsal sözleşmedir. Java-native `gen-core` modülü bu sözleşmeyi
> **birebir** korumalıdır. Anchor'lar CoreTemplate1 kaynak dosyalarına işaret eder (READ-ONLY referans;
> CoreTemplate1'e asla yazılmaz).

Genel mimari: 3 aşamalı hedef-bağımsız pipeline — **Load** (`Loader`) → **Join & Validate → Generation Model** (`GmBuilder`) → tüketiciler (emitter'lar + `Completeness.Check`). Tüm kalıcı model tipleri immutable record. JSON: camelCase, bilinmeyen alanlar sessizce yok sayılır.

---

## 1. Manifest.cs — manifest.json TAM şeması

Dosya: `Model/Manifest.cs`. Not (`Manifest.cs:5-6`): **bilinmeyen JSON alanları sessizce yok sayılır** (ör. `operation.edges` şu an tüketilmiyor). Bütün tipler immutable record.

### Kök: `ManifestJson` (`Manifest.cs:8-23`)
- `Mode: string` — "linked" | "standalone".
- `Contract: string?` — operations.json'a göreli yol (nullable).
- `Meta: Meta`
- `Deployables: List<Deployable>`
- `Modules: List<ModuleDecl>`
- `Operations: List<OperationJson>`
- `Entities: List<EntityJson>`
- `Types: List<TypeJson>`
- `Errors: List<ErrorJson>`
- `Events: List<EventJson>`
- `Subscriptions: List<SubscriptionJson>`
- `Externals: List<ExternalJson>`
- `Uncharted: List<UnchartedJson>` — external-benzeri çağrı-adapter AMA kendi entity/type'larını **OWN eder** (`Manifest.cs:21`).
- `CallEdges: List<CallEdgeJson>`
- `Coverage: Coverage`

### Yardımcı/scalar record'lar
- `Meta(bool HasErrors, int ErrorCount)` (`:25`).
- `Deployable(string Name, List<string> Units, List<ExtJson>? Ext=null)` (`:26`) — anahtar **Name**.
- `ModuleDecl(string Name, bool PureTechnical, List<ExtJson>? Ext=null)` (`:27`) — anahtar **Name**.
- `ParamJson(string Name, string Type, bool Collection, List<ExtJson>? Ext=null)` (`:29`).
- `SignatureJson(List<ParamJson> Params, string Returns)` (`:30`) — `Returns` sadece tip adı stringi.
- `ServingArg(string Kind, string Value, List<string>? Params)` (`:31`).
- `ServingJson(string Protocol, List<ServingArg> Args, string Raw)` (`:32`) — census'ta `{owner}:{Protocol}` ile anahtarlanır.
- `AccessJson(List<string> Reads, List<string> Creates, List<string> Updates, List<string> Deletes)` (`:33`) — **4-anahtarlı access modeli**; "Kapı 0 authority". Command/query ayrımı buradan: `isCommand = Creates|Updates|Deletes boş değilse` (`GmBuilder.cs:67`).
- `GuardedExpr(string Text, ExprNode Ast, string? GuardRef)` (`:34`) — validation/rule/invariant elemanı.
- `Consistency(string Risk, string? Mode)` (`:35`) — census'ta `Mode != null || Risk == "eventual"` ise sayılır (`Completeness.cs:84`).
- `Abac(ExprNode Permit)` (`:36`).
- `Idempotent(List<string> Keys)` (`:37`).
- `PaginationKey(string Field, string Direction)` (`:38`); `Pagination(string Strategy, List<PaginationKey> Keys, int? Size)` (`:39`).
- `ExtJson(string Ns, string Name, Dictionary<string,JsonElement> Args)` (`:41`) — census'ta `@{Ns}.{Name}` construct adıyla sayılır (`Completeness.cs:95`). `Args` toleranslı raw-JSON.

### `OperationJson` (`Manifest.cs:43-49`) — anahtar **Id**
`Id, Module, Visibility, Realizes(string?), Signature, Serving(List), Roles(List<string>), Ownership(string?), Access(AccessJson), Validation(List<GuardedExpr>), Rule(List<GuardedExpr>), Note(string?), BusinessNote(string?), Consistency, Abac(Abac?), Scopes(List<string>), Throws(List<string>), Idempotent(Idempotent?), Emits(List<string>), Pagination(Pagination?), Ext(List<ExtJson>?)`.
Kritik: `Realizes` = business-contract op id'sine join anahtarı. `Emits` = string[] (event adları) — hedef ConsumerOpRef manifest'te taşınmaz.

### `EntityJson` (`Manifest.cs:52-57`) — anahtar **Id**
- `SourceOfTruth(string Module, string Entity)` (`:51`).
- `EntityFieldJson(string Name, string Type, bool Collection, string Cardinality, string Ref, string? TargetModule, bool? CrossModule, SourceOfTruth?, List<ExtJson>? Ext)` (`:52-54`).
- `EntityJson(string Id, string Module, List<string> Realizes, List<EntityFieldJson> Fields, List<GuardedExpr> Invariants, string? Concurrency, List<ExtJson>? Ext)` (`:55-57`). `Realizes` **çoğuldur** (op'unki tekil). `Concurrency == "optimistic"` census'ta "concurrency".

### `TypeJson` (`Manifest.cs:59-60`) — anahtar **Id**
- `FieldJson(string Name, string Type, bool Collection, List<ExtJson>? Ext)` (`:59`).
- `TypeJson(string Id, string Module, string Kind, List<FieldJson>? Fields, List<string>? Values, List<ExtJson>? Ext)` (`:60`) — `Kind` census'ta construct adının **kendisi** olur (`Completeness.cs:52`; composite/enum vb.). `Values` enum değerleri.

### Errors / Events / Subscriptions / CallEdges / Externals
- `ErrorJson(string Id, string Module, string ResultType)` (`:62`).
- `EventJson(string Id, string Module, List<FieldJson> Payload)` (`:63`).
- `EventRef(string Module, string Name)` (`:64`); `ConsumerRef(string Module, string Op)` (`:65`).
- `SubscriptionJson(EventRef Event, ConsumerRef Consumer)` (`:66`) — sıralama anahtarı `(Event.Module, Event.Name, Consumer.Op)`; census'ta **`Event.Name`** ile sayılır (`Completeness.cs:49`).
- `CallTarget(string System, string Op)` (`:67`); `CallEdgeJson(string From, CallTarget To, string Kind, CallTarget? Compensate)` (`:68`). Sıralama `(From, To.System, To.Op)`. Census: her edge → ("calls", From); `Compensate != null` → ("compensate", From).
- `BoundaryOpJson(string Id, SignatureJson Signature, List<ServingJson>? Serving=null, List<GuardedExpr>? Validation=null)` (`:69`).
- `ExternalJson(string Name, bool Generated, List<BoundaryOpJson> Operations)` (`:70`) — anahtar **Name**.

### Uncharted (`Manifest.cs:72-77`) — anahtar **Name**
- `UnchartedEntity(string Id, List<string> Realizes, List<EntityFieldJson> Fields, string? Concurrency, List<ExtJson>? Ext)` (`:73`).
- `UnchartedType(string Id, string Kind, List<FieldJson>? Fields, List<string>? Values, List<ExtJson>? Ext)` (`:74`).
- `UnchartedJson(string Name, bool Generated, string? Deployable, List<BoundaryOpJson> Operations, List<UnchartedEntity> Entities, List<UnchartedType> Types)` (`:75-77`).

### `Coverage` (`Manifest.cs:78`)
`Coverage(List<string> UnrealizedBusinessOps, List<string> UncoveredEntities)`.

---

## 2. Contract.cs — operations.json TAM şeması

Dosya: `Model/Contract.cs`. Not (`Contract.cs:5-6`): `schedule`/`delegation` şu an tüketilmiyor (ignore). Çoğu alan nullable (toleranslı).

### Kök: `ContractFile` (`Contract.cs:8-15`)
`ContractFile(ContractMeta? Meta, List<ContractOp>? Operations, List<ContractEntity>? Entities, List<ContractActor>? Actors, List<raw-json>? Relations, List<ProcessJson>? Processes=null, List<FlowJson>? Flows=null)`.
`Relations` = toleranslı ham JSON (tüketilmiyor). `Processes`/`Flows` TestPlan girdisi.

### Alt tipler
- `ContractMeta(int? SchemaVersion)` (`:17`).
- `ContractSignature(string Actor, string Verb, string Ownership, string Resource)` (`:18`).
- `ContractGuard(string Id, string Kind, string? Role, string? Calendar, string? Text, ExprNode? Ast)` (`:19`).
- `ContractEffect(string Kind, raw-json? Target, ExprNode? Expr=null, string? Text=null)` (`:24`). `Target` string ("biz.Invoice") VEYA path dizisi (["Appointment","status"]) olabilir → toleranslı raw-JSON. `Expr` = calculate effect ExprNode; `Text` = ham literal. Field-ASSERT bunları okur.
- `ContractAccess(List<string> Writes, List<string> Reads)` (`:25`) — **business access 2-anahtarlı** (manifest'inki 4-anahtarlı — kritik asimetri).

### `ContractOp` (`Contract.cs:27-30`) — anahtar **Id**
`ContractOp(string Id, string Kind, ContractSignature? Signature, string? Description, List<ContractGuard> Guards, List<ContractEffect> Effects, ContractAccess Access, List<string> Flows, List<string> Processes, string? Domain)`.

### realizes ilişkisi
Contract tarafında realizes karşılığı YOK; tek yönlü: manifest `OperationJson.Realizes` (tekil) ve `EntityJson.Realizes` (çoğul) → `ContractOp.Id` / `ContractEntity.Id`.

### Diğer
- `ContractEntity(string Id, string Name, string? Domain)` (`:32`).
- `ContractActor(string Id, string? Extends)` (`:33`).

### Process/Flow (TestPlan girdisi, `Contract.cs:35-39`)
- `ProcessJson(string Id, string? Entity, string? Note, List<ProcessStage>? Items)` (`:36`).
- `ProcessStage(string Type, string Name, string? StageKind, string? Flow, string? By)` (`:37`) — `Flow` bir flow id referansı (containment anahtarı).
- `FlowJson(string Id, string? Actor, string? Note, List<FlowStep>? Items)` (`:38`).
- `FlowStep(string Type, string Name, string? Target, bool Optional, bool Repeat, string? Using)` (`:39`) — `Target` op-id referansı (RunSequence kaynağı).

---

## 3. ExprNode + ExprBuild — Expression AST ve predicate indirgeme

### AST (`Model/ExprNode.cs`)
Discriminated union, JSON'da ayrımcı üç alan (`ExprNode.cs:7-9`): `node` (binary/agg/call), `path` (PathNode), `kind` (literal/duration).
- `BinaryNode(string NodeKind, string? Op, ExprNode Left, ExprNode Right)` — `NodeKind ∈ and|or|cmp|add|sub|mul|div`; `Op` yalnız cmp/arith'te dolu.
- `AggNode(string Fn, List<string> Path)` — aggregate.
- `CallNode(string Name, List<ExprNode> Args)`.
- `PathNode(List<string> Path)`.
- `LiteralNode(string LitKind, object Value)` — `LitKind ∈ string|number|boolean`.
- `DurationNode(double Value, string Unit, string Text)`.

### Deserializer (`ExprNode.cs:29-65`) — sıra önemli:
1. `node` alanı varsa: "agg"→AggNode; "call"→CallNode; **default**→BinaryNode (yani bilinmeyen NodeKind toleranslı BinaryNode olur, PATLAMAZ).
2. `path` alanı varsa → PathNode.
3. `kind` alanı varsa: "duration"→DurationNode; string/number/boolean→Literal; **bilinmeyen literal kind → parse hatası** (`:60`).
4. Hiçbiri → **parse hatası "unknown ExprNode shape"** (`:64`).
Round-trip serializer da var (`:74-120`). Bu asimetri (bilinmeyen NodeKind toleranslı, bilinmeyen literal/şekil fatal) korunmalı.

### ExprBuild (`Predicate/ExprBuild.cs`) — INV-4
- `Build(root, resolveType?)` → `(exprString, distinctPaths)`. `resolveType`: path → nötr manifest tipi ("Decimal"/"Int"/...).
- `TypeOf` (`:28-36`): bottom-up sayısal tip; add/sub/mul/div için baskınlık **Decimal > Double > Int**; literal double tam sayıysa "Int".
- `Walk` (`:38-44`): BinaryNode/PathNode/LiteralNode desteklenir; **AggNode/CallNode/DurationNode → UnsupportedConstruct** (INV-7 rapor yolu).
- `WalkBinary`: her binary **her zaman parantezli** `({left} {op} {right})`.
- `PathRef`: path → `input.{PascalJoin}`; distinct sırayla paths listesine.
- `PropName`: collision-safe Pascal-join — `['resource','creditLimit']` → `ResourceCreditLimit`.
- `InferLiteralTypes` (`:70-91`): karşılaştırma bağlamından path tip-ipucu (status=="planli" → String).
- `Op` (`:103-108`): and→`&&`, or→`||`; `=`→`==`; op'suz cmp/arith → UnsupportedConstruct.
- `Literal`: string→çift tırnak, bool→true/false, double→invariant "R" format (+ .NET'te decimal-context 'm' suffix — Java'da BigDecimal stratejisiyle yeniden tasarlanır ama tip-baskınlık kuralı korunur).

---

## 4. Loader + Json — yükleme akışı

### `Loader` (`Pipeline/Loader.cs`)
- `LoadManifest(path)` (`:9-14`): dosya yok → **LoadError("manifest bulunamadı: {path}")**; parse hatası → **LoadError("manifest ayrıştırılamadı: {msg}")**. Manifest hatası her zaman FATAL.
- `LoadContract(manifestPath, contractPath)` (`:18-25`): `contractPath==null` → null (standalone). Yol manifest dizinine göreli çözülür. **Dosya yoksa → null; parse hatası → null** (throw YOK) — hata join aşamasına devredilir.

### `Json` (`Json.cs`)
Global ayarlar: camelCase, case-insensitive, indent yok, relaxed escaping, ExprNode converter kayıtlı. `Parse<T>`: null deserialize → hata. (BuildReport kendi pretty ayarlarını kullanır: indent'li, null-atlamalı.)

---

## 5. GmBuilder + GenerationModel — Join & GM

### `GmBuilder.Build(m, contract?)` (`GmBuilder.cs:9-57`)
1. `linked = m.Mode == "linked"`.
2. **JoinError #1** (`:12-13`): `linked && m.Contract != null && contract == null` → JoinError("linked mod ama operations.json çözülemedi: {m.Contract}").
3. Standalone'da join N/A (INV-9): ops/ents indeksleri boş. Linked'de contract Operations/Entities Id ile indekslenir.
4. Operations: Id-ordinal sıralı, `BuildOp` ile.
5. **JoinError #2 (entity realizes)** (`:24-28`): linked'de her `entity.Realizes[rid]` contract entity'lerinde yoksa → JoinError("entity '{Id}' realizes '{rid}' operations.json'da yok").
6. **TypeEnv**: `OpParams` = opId→(paramName→type); `EntityFields` = entityId→(fieldName→type).
7. GM koleksiyonları **tümü ordinal-sıralı**: Modules(Name), Entities(Id), Types(Id), Events(Id), Subscriptions(Event.Module→Event.Name→Consumer.Op), Errors(Id), Externals(Name), CallEdges(From→To.System→To.Op), Deployables(Name), Uncharted(Name). `TestPlan = TestPlanBuilder.Build(contract, m.Operations)`.

### `BuildOp` (`GmBuilder.cs:59-69`)
- **JoinError #3 (op realizes)** (`:62-66`): linked && `o.Realizes != null` && contractOps'ta yok → JoinError("operation '{Id}' realizes '{Realizes}' operations.json'da yok").
- `isCommand = Creates|Updates|Deletes herhangi biri boş değil` (`:67`).

### `GenerationModel` (`Gm/GenerationModel.cs:10-24`)
Hedef-nötr IR: `Mode, Modules, Operations(GmOperation), Entities, Types, Events, Subscriptions, Errors, Externals, CallEdges, Deployables, Uncharted, Env(TypeEnv), TestPlan`.

### `TypeEnv` (`GenerationModel.cs:30-56`)
- `WriteTarget(op)`: op'un yazma-hedefi = `Creates ∪ Updates ∪ Deletes` içinden **ilki**; yoksa null.
- `ResolvePath(gm, path, opId?, entityId?)`: boş→null; `path[0]=="actor"`→**"String"** (opak claim); `path[0]=="resource"` && opId → WriteTarget entity'sinin `path[1]` alan tipi; aksi bare path → önce op param tipi, sonra entity alan tipi; çözülemeyen→null.

### `GmOperation` (`GenerationModel.cs:59-63`)
`GmOperation(OperationJson Op, ContractOp? Business, bool IsCommand)`.

---

## 6. TestPlan + TestPlanBuilder — Test plan üretimi

### IR (`Gm/TestPlan.cs`)
- `TestPlan(ProcessTests, OrphanFlowTests, OrphanOpTests)` — **üç test türü**.
- `ProcessTest(string ProcessId, string? Entity, List<string> RunSequence, List<PrereqStep> Prerequisites, List<string> WriteSet)`.
- `ScenarioTest(string Id, string Scope, RunSequence, Prerequisites, WriteSet)` — `Scope ∈ "OrphanFlow"|"OrphanOp"`.
- `PrereqKind { Single, Ambiguous, Missing }`; `PrereqStep(string Entity, string? CreatorOp, PrereqKind Kind)`.
IR = prerequisites (arrange) + RunSequence (act) + WriteSet (assert kaynağı).

### `TestPlanBuilder.Build(contract?, manifestOps)` (`TestPlanBuilder.cs:15-164`)
1. `contract==null || Processes==null || Flows==null` → **boş TestPlan** (çökme yok).
2. `opById`: dup id → **son kazanır**.
3. `creators` ters-index: entity → [Creates içeren op Id'leri], Id-ordinal gezinme, distinct.
4. **`Derive(runSeq)`** (`:42-104`):
   - `produced` = runSeq Creates birleşimi; `needed` = (Reads ∪ Updates) − produced; `WriteSet` = Creates∪Updates∪Deletes ordinal distinct.
   - Manifest'te olmayan opId → skip (toleranslı).
   - Sınıflama: creators sayısı 1→Single; >1→**Ambiguous(CreatorOp=null)**; 0→**Missing(CreatorOp=null)**. ASLA creator seçilmez çoklu/sıfırda.
   - Topo-sort (Single'lar): e1→e2 kenarı = creators[e1][0]'ın (Reads∪Updates)'ı e2'yi içeriyorsa. Deterministik: deps'i tükenmiş en küçük ordinal; döngüde en küçük ordinal fallback → sonsuz döngü yok.
   - Ambiguous/Missing sona, entity-id ordinal.
5. `flowById`: dup → son kazanır.
6. **ProcessTests**: process Items'ında `stage.Flow != null` → flowsInProcess; dangling flow → skip (throw yok); flow Items'ında `step.Target != null` sırayla RunSequence'e (**Items sırası korunur — ordinal DEĞİL**).
7. `opsInFlow`: TÜM flow'ların target'ları (küme-farkından ÖNCE).
8. **OrphanFlowTests** = Flows \ flowsInProcess, Scope="OrphanFlow".
9. **OrphanOpTests** = contract.Operations \ opsInFlow, tek-op runSeq, Scope="OrphanOp".
10. Sonuç listeleri ordinal sıralı (ProcessId / Id).

---

## 7. BuildReport + Completeness

### `BuildReport` (`Report/BuildReport.cs`)
- `ConstructStatus { Realized, Unsupported, EmitConflict, SilentDrop }`.
- `BuildEntry(string Construct, string Id, ConstructStatus Status, string? Reason)`.
- API: `Realized(c,id)`; `Unsupported(c,id,reason)`; `Conflict(c,id,reason)`; `SilentDrop(c,id)` → sabit reason `"manifest'te var; ne emit ne rapor (INV-7)"`; `Policy(name,decision)` → ordinal-sorted map.
- **`Covers(construct, owner)`** (`:27-29`): construct case-insensitive eşit && `Status != SilentDrop` && `IdMatches`.
- **`IdMatches(id, owner)`** (`:34-36`): `id == owner` VEYA `id.StartsWith(owner)` && daha uzun && `id[owner.Length] ∈ {'#','-',':',' '}`. Sınır-ayracı önek — substring DEĞİL (compound Id'ler: `{op}#Validation0`, `{op}->{err}`, `{op}:{proto}`). "Get", "GetInvoice"i yanlış örtmesin (INV-7 soundness).
- **`Clean`**: tüm entry'ler Realized ise true.
- **`ToJson`**: entry'ler (Construct→Id) ordinal sıralı; `{construct,id,status,reason}`; kök `{constructs, policies}`; pretty (indent'li, null-atlamalı); dosya sonu `"\n"`.
- `SilentDrops`: status==SilentDrop olanlar. `Tag`: realized/unsupported/silentDrop/emitConflict.

### `Completeness` (`Report/Completeness.cs`) — construct census
Build-time-only construct'lar census'a GİRMEZ: standalone/contract/import/rolemap/extension-decl/realizes/access/pureTechnical; returnAnnotations, emits-target ConsumerOpRef, @internal token, external/uncharted/error/event üstü @ns.name.

`Census(m)` sayılan construct'lar — TAM LİSTE:
- `deployable` (Name) + Ext
- `module` (Name) + Ext
- `error` (Id)
- `external` (Name); alt: `boundary-op` (`{ext}.{op}`), `validation` (Count>0), `serving` (`{ext}.{op}:{proto}`), param Ext
- `uncharted` (Name); alt: boundary-op, validation, serving, param Ext; entity `concurrency` (optimistic)
- `subscription` (`Event.Name`)
- `calls` (From); `compensate` (Compensate!=null → From)
- `event` (Id) + payload field Ext
- `{t.Kind}` (type Id — kind census construct adının KENDİSİ) + Ext'ler
- entity: `entity`(Id) + Ext; `concurrency`(optimistic); `invariant`(Count>0); `guardRef`(herhangi GuardRef!=null); alan başına `sourceOfTruth`(`{en}.{f}`) + field Ext
- operation: `operation`(Id); `visibility`(Id); `roles`(>0); `scopes`(>0); `ownership`(!=null); `validation`(>0); `rule`(>0); `guardRef`; `permit`(Abac!=null); `note`(!=null); throw başına `throws`(`{op}->{err}`); `idempotent`; `pagination`; emit başına `emits`(`{op}->{ev}`); `consistency`(Mode!=null || Risk=="eventual"); serving başına `serving`(`{op}:{proto}`); param Ext; op Ext
- her ext → `("@{Ns}.{Name}", owner)`
- Dönüş: Distinct (tuple değer-eşitliği).

**`Check(m, report)`**: census'taki her (construct, owner) için `!report.Covers(...)` → `report.SilentDrop(...)`. No-silent-loss'un uygulama noktası.

---

## 8. Errors — hata tipleri

- **`LoadError`** — Aşama 1: manifest yok / manifest parse hatası.
- **`JoinError`** — Aşama 2: linked+contract çözülemedi; entity realizes çözülmedi; op realizes çözülmedi.
- **`ModelError`** — rezerve (Gen.Core'da fırlatılmıyor).
- **`UnsupportedConstruct`** — INV-7: ExprBuild bilinmeyen node; op'suz cmp/arith. Fatal DEĞİL — çağıran build-report'a Unsupported yazar.

---

## 9. Java yeniden-yazımın BİREBİR koruması gereken değişmezler

**Determinizm**
- Tüm GM koleksiyonları ordinal (byte-wise) sıralı — §5'teki anahtarlarla. Java'da `String.compareTo` (UTF-16 kod-birimi) kullanılacak; .NET Ordinal ile uç-durum eşdeğerliği determinizm testiyle sabitlenmeli.
- TestPlan çıktı listeleri ordinal; RunSequence içi sıra Items sırasını korur (ordinal DEĞİL).
- Prereq topo-sort deterministik + döngüde en-küçük-ordinal fallback.
- BuildReport JSON (Construct,Id) ordinal; policies ordinal-sorted; çıktı `\n` ile biter.
- Census Distinct ile tekilleştirilir.

**No-silent-loss (INV-7/INV-8)**
- Census'taki her construct ya Realized/Unsupported/Conflict raporlanır; aksi SilentDrop. Java census listesi **birebir aynı construct adları ve owner-id şablonları** (`{ext}.{op}`, `{op}:{proto}`, `{op}->{err}`) üretmeli.
- Realize edilemeyen construct → UnsupportedConstruct (sessiz düşürme yasak).
- IdMatches sınır-ayracı kuralı ({'#','-',':',' '}) korunmalı.

**Hata ilişkisi**
- Manifest hatası fatal (LoadError); contract eksik/bozuk sessiz null AMA linked'de `m.Contract != null` ise JoinError'a yükselir. Realizes bütünlüğü linked'de zorunlu.

**Toleranslılık (bilinçli sessiz davranışlar)**
- Bilinmeyen JSON alanları yok sayılır.
- Dup id → son kazanır (opById, flowById).
- Dangling flow / manifest'te olmayan op → skip.
- Standalone / eski contract → boş TestPlan; join N/A.

**Model asimetrileri**
- Manifest access 4-anahtar; business ContractAccess 2-anahtar. IsCommand ve prereq/WriteSet **manifest'in 4-anahtarından** türer.
- Op Realizes tekil; entity Realizes çoğul. Contract'ta ters-referans yok.
- Prereq çoklu/sıfır creator'da asla creator seçilmez.

**Predicate (INV-4)**
- `=`→`==`, and→`&&`, or→`||`; her binary parantezli; path→`input.{PascalJoin}` (hedef dilde uyarlanabilir ama collision-safe join korunur); distinct path sırası korunur. Tip-baskınlık: Decimal > Double > Int. Java'da Decimal = `BigDecimal` (compareTo stratejisi gen-spring tasarım kararı).

**Serileştirme**
- camelCase, case-insensitive, bilinmeyen alan toleransı, ExprNode round-trip. Bilinmeyen literal-kind/şekil parse hatası; bilinmeyen NodeKind toleranslı BinaryNode.
