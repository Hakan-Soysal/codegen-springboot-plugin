# Gen.Dotnet Emisyon Sözleşmesi Referansı (gen-spring tasarım kaynağı)

> `/Users/hakansoysal/Desktop/ClaudeCode Denemeler/CoreTemplate1/src/Gen.Dotnet` + `src/Gen.Cli` tam
> okumasından çıkarılmıştır. Anchor'lar (dosya adı yoksa) `DotnetEmitter.cs` satırlarına işaret eder.
> CoreTemplate1 READ-ONLY referanstır. Root sabit = `"App"` (`DotnetEmitter.cs:15`).

Girdi zinciri: `manifest.json` (+`operations.json`, linked) → `GmBuilder.Build` → GM → `DotnetEmitter.Emit(gm, outDir, report, config)`. Emit dizini iki kök: `gen/` (üreteç-sahibi, prune'lu) ve `src/` (human-seam). Test ağacı `tests/gen` + `tests/src`.

---

## 1. Emit edilen dosya envanteri

İki yazım disposition'ı: **WriteAlways** (gen-owned `.g.cs`, her run ezilir, provenance+prune'a girer) ve **WriteIfAbsent** (human-seam/shell, yoksa-üret, asla ezilmez, provenance'a GİRMEZ).

### Kök seviye (outDir/)
- **App.csproj** — WriteIfAbsent, HumanShell (`:26`, `Csproj()` `:1533-1549`): Sdk.Web, net10.0, Nullable, tests/** exclude, `<Import Project="gen/Generated.props" Condition="Exists(...)"/>`.
- **Program.cs** — WriteIfAbsent, HumanShell (`:159`, `:1516-1530`): `AddGenerated()` + `MapGenerated()` çağıran minimal host; pipeline sırası insan-sahipli.

### gen/ kökü (global) — WriteAlways
- **gen/Generated.props** (`:27`, `:1553-1572`) — üreteç-sahibi paket manifesti; EF Core + config.DbProvider'a göre provider paketi. `.g.cs` olmadığı için GenHeader almaz.
- **gen/Result.g.cs** (`:28`, `:684-703`) — Result taksonomisi (aşağıda).
- **gen/ResultHttp.g.cs** (`:29`, `:705-733`) — Result→HTTP eşleme + `Override` process-global hook.
- **gen/GlobalUsings.g.cs** (`:30`, `:1576-1581`).
- **gen/AppDbContext.g.cs** — yalnız Entities>0 (`:34`, `:1189-1212`): DbSet'ler + `OnModelCreatingPartial` seam.
- **gen/EventBus.g.cs** — yalnız Events>0 (`:35`, `:1385-1398`): `IEventBus.PublishAsync` + `OutboxEventBus` stub.
- **gen/Subscriptions.g.cs** — yalnız Subscriptions>0 (`:39`, `:1348-1364`).
- **gen/Host.g.cs** — Deployables>0 (`:52`, `:791-824`): `DeploymentTopology.Deployables` map.
- **gen/Boundary.g.cs** — Externals>0 || CallEdges>0 (`:54`, `:913-955`).
- **gen/Uncharted/{Name}.g.cs** — uncharted başına (`:63-67`, `:1017-1071`): stub interface+client + OWNED entity/type POCO.
- **gen/Idempotency.g.cs** — herhangi op Idempotent!=null (`:68`, `:1073-1089`).
- **gen/Bootstrap.g.cs** — her zaman (`:158`, `:1610-1639`): `AddGenerated`/`MapGenerated` global aggregator + global DI.

### gen/{Module}/ — WriteAlways
- **Wiring.g.cs** — her zaman (`:77`, `:1643-1659`): `Add{M}Module`/`Map{M}Module` aggregator.
- **Errors.g.cs** — modül error'ları varsa (`:83`, `:828-841`).
- **Module.g.cs** — modül Ext varsa (`:84-86`): prelude yorumları.
- **Events.g.cs** — modül event'leri varsa (`:87`, `:1334-1345`): event = sealed record.
- **Types.g.cs** — modül type'ları varsa (`:88`, `:735-752`): enum → enum, diğer → sealed record.
- **Entities.g.cs** — modül entity'leri varsa (`:89`, `:755-787`): partial class (EF); optimistic → `[Timestamp] byte[] RowVersion`.
- **{Entity}.Invariants.g.cs** — Invariants varsa (`:90-94`, `:1296-1319`): `static class {Id}Invariants` + tipli predicate'ler.

### gen/{Module}/{Op}/ (feature slice) — WriteAlways
Her op `{Op}Handler` partial-class ailesi:
- **{Op}.g.cs** — her zaman (`:101`, `:1466-1494`): request record (`{Op}Command`/`{Op}Query`) + `partial class {Op}Handler` + gövdesiz `partial Task<Result<{Ret}>> ExecuteAsync({Req} request, CancellationToken ct)` bildirimi; note→XML doc.
- **{Op}.Endpoint.g.cs** — her zaman (`:102`, `:1663-1685`): `Add{Op}` (DI + trigger hosted-service) + `Map{Op}` (route; internal/non-rest → boş gövde).
- **{Op}.Guards.g.cs** — validation/rule/permit varsa (`:106-107`, `:1216-1244`): `Validation_N`/`Rule_N`/`Permit_0` + `{Owner}{Kind}{N}Input` record'ları.
- **{Op}.Auth.g.cs** — roles/scopes/ownership varsa (`:108-109`, `:1401-1427`): `RequiredRoles`/`RequiredScopes`/`Ownership` sabitleri.
- **{Op}.Page.g.cs** — pagination varsa (`:111-117`, `:1432-1446`): `PaginationStrategy` + `DefaultPageSize`.
- **{Op}.Idem.g.cs** — idempotent varsa (`:118-122`, `:1091-1101`): `IdempotencyKeys` array.
- **{Op}.Ext.g.cs** — op Ext varsa (`:123-124`, `:1142-1177`): prelude yorumlar + tanınan ext sabitleri (AuditCategory/MetricName/HttpRoute).
- **{Op}.Trigger.g.cs** — `@trigger.*` ext varsa (`:125-137`, `:1105-1123`): `{Op}{T}Trigger : IHostedService` partial.
- **{Op}.Throws.g.cs** — Throws varsa (`:138-139`, `:844-873`): `ThrowableErrors` array + tipli hata fabrikaları.
- **{Op}.Consistency.g.cs** — mode≠null VEYA risk==eventual (`:140-141`, `:887-910`): `ConsistencyRisk`/`ConsistencyMode`.

### src/ (human-seam) — WriteIfAbsent
- **src/{Module}/{Op}/{Op}Handler.Logic.cs** — her op (`:104-105`, `:1496-1513`).
- **src/{Module}/{Op}/{Op}{T}Trigger.Logic.cs** — trigger başına (`:130-136`, `:1127-1140`).
- **src/{Module}/{Op}/{Event}To{Op}Consumer.Logic.cs** — subscription (consumer slice'ında) (`:41-48`, `:1368-1383`).
- **src/Boundary/{Ext}Client.Logic.cs** — external başına (`:56-62`, `:959-974`): `I{Ext}` impl stub'ları.

### tests/ (`EmitTests` bölümü `:201-250`)
- **tests/Tests.csproj** — WriteIfAbsent, HumanShell (`:206`, `:351-374`).
- **tests/gen/Fixture.g.cs** — WriteAlways (`:211`, `:413-490`): AddGenerated bootstrap + test-provider override, `RunAsync<THandler>`, `Get<TEntity>`, `ResetAsync`, `Performed`/`Emitted` recorder.
- **tests/gen/GlobalUsings.g.cs** — WriteAlways (`:212`).
- **tests/gen/{Scope}/{Name}.g.cs** — emit edilebilir test başına (`:234`, `:276-320`): 3-faz owned iskelet + `partial {Req} Arrange_{Op}()` bildirimi.
- **tests/src/{Scope}/{Name}.Logic.cs** — WriteIfAbsent ARRANGE seam (`:240`, `:328-345`): `return default!;` + `doldurulacak` marker yorumu.
- Test scope'ları: `Process`, `OrphanFlow_*`, `OrphanOp_*` (TestPlan'dan). Single-DIŞI (Ambiguous/Missing) prereq'li test iskelet EMİT ETMEZ → `Unsupported("test-prereq", ...)` (`:219-228`).

### Result taksonomisi (birebir, `:684-703`)
```csharp
public readonly record struct Unit;
public abstract record Result<T>;
public sealed record Success<T>(T Value) : Result<T>;
public sealed record NotAuthenticated<T>(string Reason) : Result<T>;
public sealed record NotAuthorized<T>(string Reason) : Result<T>;
public sealed record NotValid<T>(IReadOnlyDictionary<string,string> Errors) : Result<T>;
public sealed record NotProcessable<T>(string Code, string Message) : Result<T>;
public sealed record ServerError<T>(string Message) : Result<T>;
public sealed record Page<T>(IReadOnlyList<T> Items, string? NextCursor);
```
Kapalı taksonomi (INV-5). HTTP eşleme (`ToHttp` `:717-730`): Success→200, NotAuthenticated→401, NotAuthorized→403, NotValid→ValidationProblem(400), NotProcessable→422 `{code,message}`, ServerError→500 `{message}`.

Error kataloğu (`:828-841`): `static class Errors` içinde her hata `const string {Id} = "{Id}"` + resultType yorumu. Fabrikalar (`ThrowFactory` `:876-883`): resultType→Result alt-tipi (NotValid→dict, NotAuthorized/NotAuthenticated/ServerError→string, tanınmayan→NotProcessable(codeRef, message)).

---

## 2. Seam mekaniği

- `WriteAlways(path, content)` (`:659-665`): `.g.cs`'e `GenHeader` prepend; `_written` (path+sha256) listesine ekler; **write-only-if-changed** (aynıysa dokunmaz → mtime kararlı).
- `WriteIfAbsent(path, content)` (`:668`): yalnız dosya yoksa yazar; provenance/prune DIŞI. Dizin oluşturmaz — caller açar.
- **GenHeader** (birebir, `:656`): `// <auto-generated/>` + `#nullable enable`.
- Partial-class deseni: `.g.cs`'te gövdesiz partial method bildirimi; `Logic.cs`'te aynı namespace+class, method impl.
- **Boş marker metinleri (birebir):**
  - Handler: `throw new NotImplementedException("{op.Id}: iş mantığı doldurulacak")` (`:1509`)
  - Trigger: `...("{op.Id}{T}Trigger.StartAsync: doldurulacak")` (`:1137`)
  - Subscription: `...("{cls}.HandleAsync: doldurulacak")` (`:1379`)
  - Boundary: `...("{ext.Name}.{b.Id}: doldurulacak")` (`:971`)
  - Test ARRANGE: `return default!;` + `doldurulacak` yorum (`:339-340`)
- **Brownfield migrasyon** `MigrateSeamIfFlat` (`:674-681`): eski düz layout seam'i slice'a `File.Move` — WriteIfAbsent'tan ÖNCE (çift-impl derleme kırığı önlenir).

---

## 3. Naming kuralları (`Naming.cs`)

- `Pascal(s)`: ilk harf upper, gerisi aynen.
- `Type(type, collection)` skaler eşleme: ID→string, String→string, Decimal→decimal, Int→int, Bool/Boolean→bool, DateTime→DateTime, Date→DateOnly, Duration→TimeSpan; `*Id` soneki→string (FK referansı); diğer adlar→passthrough; collection→`List<T>`.
- `HttpVerb`: POST/PUT/PATCH/DELETE→Map{Verb}, default→MapGet. `BindsBody`: POST/PUT/PATCH→body bind; GET/DELETE→route+query.
- Sınıf adları: handler=`{op}Handler`; request=`{op}Command`(IsCommand)/`{op}Query`; consumer=`{Event}To{Op}Consumer`; trigger=`{op}{Pascal(t)}Trigger`; invariants=`{Entity}Invariants`.

**Java eşlemesi:** DateOnly→LocalDate, TimeSpan→Duration, decimal→BigDecimal, ID/*Id→String, List<T> aynı.

---

## 4. GenConfig (`GenConfig.cs`)

`GenConfig(string? DbProvider, string? TestDbProvider = null)` — yalnız iki alan. `gen.config.json` manifest dizininde; yoksa null → provider seam.
- **DbProvider** whitelist: postgres/sqlite/sqlserver/inmemory. Etki: Generated.props paketi + Bootstrap AddDbContext kaydı (null→seam yorumu; whitelist-dışı→`Unsupported("dbProvider")` + seam).
- **TestDbProvider** default inmemory; Tests.csproj paketi + Fixture DB decl/use (inmemory/sqlite→runtime Guid db-adı; postgres/sqlserver→env `TEST_DB`).
- DbProvider dile-özel → paylaşılan manifest'e GİRMEZ. gen-spring karşılığı: application.yml + Maven bağımlılık seçimi.

---

## 5. Provenance (`Provenance.cs`)

- `provenance.json` outDir kökünde; `Provenance(Generator, Version, Files[])`; `ProvenanceEntry(Path, Class, Sha256)`.
- `FileClass { Generated, HumanSeam, HumanShell }` ama **yalnız Generated listelenir** (`DotnetEmitter.cs:191-193`). Path=outDir-göreli, `/` ayraçlı, ordinal-sıralı.
- Sha256: UTF8→SHA256→lowercase hex. **Atomik yazım**: temp + move; sonda `\n`.
- `TryRead`: yok/bozuk → null → **prune atlanır** (canlı dosya yanlış silinmez).
- **Prune** (`:168-194`): önceki provenance'taki Generated dosyalardan bu run'da yazılmayanlar silinir; human dosyalara asla dokunmaz; boş dizinler en-derin-önce temizlenir.

---

## 6. Gen.Cli akışı (`Gen.Cli/Program.cs`)

1. Arg'lar: `args[0]`=manifest (default fixtures), `args[1]`=outDir (default `out`).
2. `Loader.LoadManifest` + `Loader.LoadContract(manifestPath, manifest.Contract)`.
3. `GmBuilder.Build` → GM.
4. `GenConfig.Load(<manifestDir>/gen.config.json)`.
5. `DotnetEmitter.Emit(gm, outDir, report, config)`.
6. `Completeness.Check(manifest, report)` — INV-7 gate.
7. `report.WriteTo(<outDir>/build-report.json)`.
8. **Exit: `drops.Count == 0 ? 0 : 1`** — exit 1 YALNIZ SilentDrop; Unsupported → exit 0.
Konsol: `emit → {outDir} (clean=..., constructs=..., silentDrops=...)` + drop başına `⚠ SESSİZ DROP: ...`.

---

## 7. Construct→emisyon dispositionları

| Construct | Disposition | Anchor |
|---|---|---|
| deployable | Host.g.cs DeploymentTopology; policy `deployment-topology=modular-monolith host` | `:793-798` |
| module | namespace+dizin+Wiring.g.cs | `:73-77` |
| module.ext | Module.g.cs prelude | `:84-86` |
| error | Errors.g.cs const + resultType yorumu | `:835` |
| external | Boundary.g.cs `I{Ext}` interface | `:919` |
| boundary-op | interface method | `:927,1054` |
| boundary validation | `{Ext}{Op}Validation.Validation_N` caller-side | `:977-1014` |
| boundary serving | metadata yorumu | `:937-938` |
| uncharted | Uncharted/{Name}.g.cs stub + OWNED entity/type; policy `uncharted-realization` | `:1017-1071` |
| subscription | Subscriptions.g.cs consumer + src seam | `:1354` |
| calls | Boundary.g.cs yorum | `:945` |
| compensate | saga yorumu; policy `saga-orchestration-state=in-memory` | `:948-951` |
| event | Events.g.cs record + EventBus | `:36,1342` |
| type | Types.g.cs record/enum; `Realized(t.Kind, t.Id)` | `:743-748` |
| entity | Entities.g.cs partial + DbSet | `:763` |
| concurrency | `[Timestamp] RowVersion` | `:779-783` |
| sourceOfTruth | cross-module FK yorumu (nav AÇILMAZ); policy | `:771-776` |
| invariant | {Entity}.Invariants.g.cs | `:1296` |
| operation | slice + handler ailesi | `:154` |
| visibility | internal→route yok; policy | `:143-144` |
| roles/scopes/ownership | Auth.g.cs sabitleri | `:1406-1408` |
| validation | Guards.g.cs Validation_N→NotValid/400 | `:1226` |
| rule | Guards.g.cs Rule_N→NotProcessable/422 | `:1227` |
| permit | Guards.g.cs Permit_0 | `:1228` |
| guardRef | yorum; policy `guard-linkage` | `:1254-1258` |
| note | XML doc | `:1478` |
| throws | Throws.g.cs fabrikalar | `:850-856` |
| idempotent | Idem.g.cs + Idempotency.g.cs; policy `dedup-store=in-memory` | `:118-122` |
| pagination | Page.g.cs + Page<T>; policy `pagination-strategy`, `cursor-token=opaque` | `:111-116` |
| emits | send-effect | `:110` |
| consistency | Consistency.g.cs yalnız mode≠null VEYA eventual; policy `consistency-mode` | `:887-893` |
| serving:rest | Endpoint.g.cs route | `:147` |
| **serving:non-rest** | **`Unsupported("serving", "REST-only binding")`; policy `serving-{proto}=unsupported`** | `:150-151` |
| @ns.name ext | yorum + census; policy `{ns}-realization` | `:1326-1331` |

**Policies (build-report.policies, ordinal-sorted):** deployment-topology, saga-orchestration-state=in-memory, consistency-mode={risk}/{mode}, dedup-store=in-memory, pagination-strategy={strategy}, cursor-token=opaque, trigger-wiring=IHostedService stub, http-binding, guard-linkage, source-of-truth, uncharted-realization, visibility, serving-{proto}, {ns}-realization. Hepsi `(generator-policy)` etiketli.

---

## 8. Determinizm mekaniği

1. GM sıralaması (GmBuilder) — tüm iterasyonlar sabit ordinal sırada.
2. Rapor sıralaması: constructs (Construct→Id) ordinal; policies sorted-map.
3. Provenance ordinal path-sıralı.
4. Satır sonları: her şablon açık `\n`; JSON dosya sonu `+"\n"`.
5. Write-only-if-changed → mtime kararlı.
6. **Emit-time'da runtime-değer YASAK** (Guid/DateTime yalnız üretilen kodun runtime'ında).
7. Golden fixture: `tests/Gen.Tests/golden/emit-snapshot.txt` — 56 satır `{relPath}\t{sha256}`, ordinal sıralı; determinizm regresyonu buna diff.

---

## 9. gen-spring taşıma notları (tasarım girdileri)

- Root `App` → Java base package (konfigüre edilebilir, default örn. `com.app`).
- **Partial class yok** → Generation Gap: gen-owned abstract base + human subclass (WriteIfAbsent), aynı "gen ezer / human korunur" disposition'ı.
- Result<T> → Java 21 `sealed interface Result<T> permits ...` + record'lar; HTTP eşleme ResponseEntity.
- EF → Spring Data JPA: `@Entity`, `[Timestamp]`→`@Version`, DbSet→`JpaRepository`.
- Minimal API → `@RestController`; HttpVerb/BindsBody kuralları aynı.
- IHostedService → Spring `SmartLifecycle`/`@Scheduled` stub.
- AddGenerated/MapGenerated → `@Configuration` + açık `@Import` zinciri (determinizm/izlenebilirlik için component-scan yerine açık kayıt önerilir).
- Determinizm + gate/exit-code + IdMatches sözleşmeleri dilden bağımsız birebir taşınır.
