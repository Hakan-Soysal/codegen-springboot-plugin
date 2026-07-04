# SPEC — techgen-spring: Tech DSL → Spring Boot Kod Üreteci + LLM-Doldurucu (Java-native)

> Kaynak kararlar: kullanıcı ile 2026-07-03 oturumu. Referans (feyz) paketi:
> `/Users/hakansoysal/Desktop/ClaudeCode Denemeler/CoreTemplate1` (techgen-dotnet) — **READ-ONLY**;
> oraya hiçbir yazma yapılmaz. Davranışsal sözleşmeler `docs/referans/*.md` dosyalarında
> anchor'lı olarak çıkarılmıştır:
> - `docs/referans/gen-core-davranis-sozlesmesi.md` (pipeline/model/GM/TestPlan/report)
> - `docs/referans/gen-dotnet-emisyon-sozlesmesi.md` (emisyon envanteri/seam/naming/determinizm)
> - `docs/referans/conformance-testler-skill-sozlesmesi.md` (conformance/test süiti/skill paketi)

## 1. Amaç ve kullanıcılar

`manifest.json` (+ opsiyonel `operations.json`) girdisini alıp **derlenen, iş-mantığı gövdeleri boş
(marker'lı) bir Spring Boot uygulaması** üreten deterministik, **Java-native** araç ve onun
**LLM-doldurucu skill'i**. Üretilen iskeleti LLM (base-springboot-rest skill'i) veya insan doldurur.
techgen-dotnet'in kardeşi: **aynı girdi sözleşmesi, aynı değişmezler, tam construct paritesi**.

**Kapsam (tek plan, tam parite):** 31 construct (deployable/module/operation/entity/type/error/
event/subscription/external/uncharted/boundary-op/calls/visibility/serving/roles/ownership/scopes/
validation/rule/note/consistency/permit/guardRef/throws/idempotent/emits/pagination/invariant/
compensate/sourceOfTruth/concurrency/ext) + build-report + determinizm + provenance + Java
conformance runner + `base-springboot-rest` skill paketi + evals + üretilen-test iskeleti (TestPlan).

## 2. Sabit kararlar (kullanıcı onaylı)

| Karar | Değer |
|---|---|
| Mimari | **Standalone Java-native üreteç** (CoreTemplate1'in Gen.Core'una bağımlılık YOK; pipeline Java'da yeniden yazılır) |
| Konum | Her şey `/Users/.../SpringBoot Template` klasöründe, sıfırdan yeni repo |
| CoreTemplate1 | Yalnız okunur referans; asla değiştirilmez |
| Üretilen app stack'i | Spring Boot **3.5.x** (en güncel 3.5 patch'i implementasyon anında Context7 ile pinlenir) + **Java 21** + **Maven**; ince `@RestController` + CQRS handler bean + Spring Data JPA + kapalı Result taksonomisi |
| Üreteç stack'i | Java 21 + Maven multi-module; JSON = Jackson |
| Girdi sözleşmesi | manifest.json / operations.json **birebir ortak** (dile-özel varyant YASAK). Dile-özel tek dosya: `gen.config.json` |

## 3. Değişmezler (INV — techgen-dotnet'ten birebir)

- **INV-D (determinizm):** aynı girdi → byte-aynı üreteç-sahibi çıktı. Tüm koleksiyon iterasyonları
  ordinal sıralı; şablonlarda `\n`; emit-time'da runtime-değer (UUID/now/random) YASAK;
  write-only-if-changed.
- **INV-7 (no-silent-loss):** manifest census'undaki her (construct, owner) ya `realized` ya
  `unsupported(reason)` ya `emitConflict` raporlanır; hiçbiri değilse `silentDrop` ve
  **exit 1 ⟺ ≥1 silentDrop**. `unsupported` exit'i ETKİLEMEZ. `Clean` ≠ exit ölçütü.
- **INV-S (seam):** üreteç-sahibi ağaç her run ezilir + prune edilir; insan-sahibi seam **WriteIfAbsent**
  ile bir kez üretilir, asla ezilmez, provenance'a girmez, prune edilmez. Boş seam marker substring'i:
  **`doldurulacak`**.
- **INV-4 (tipli predicate):** validation/rule/invariant/permit ExprNode'dan **tipli** Java prediate'ine
  indirilir; `dynamic`/yansıma yok; AggNode/CallNode/DurationNode → `UnsupportedConstruct`.
- **INV-5 (kapalı Result):** 6'lı taksonomi kapalıdır (sealed); handler yalnız bu tipleri döndürür.
- **INV-9:** standalone modda join atlanır; linked modda çözülemeyen realizes → `JoinError`.
- **INV-A3 (conformance):** assertion SPEC dosyasındadır; runner'a hiçbir beklenen değer gömülmez;
  LLM-judge yasak.
- **Gerçek-derleme doğrulaması:** her emit özelliği golden-file EŞLEŞMESİ + üretilen projenin
  `mvn compile` exit 0 kanıtıyla "bitti" sayılır (kâğıt üstü "çalışıyor" yasak).

## 4. Üreteç proje yapısı (bu repo)

```
SpringBoot Template/
├── pom.xml                      # aggregator (Java 21, modüller)
├── gen-core/                    # dil-nötr pipeline: model + loader + join + GM + TestPlan + report
├── gen-spring/                  # Spring Boot emitter (+ Java predicate renderer)
├── gen-cli/                     # CLI (shaded executable jar)
├── conformance/                 # Java conformance runner (shaded executable jar)
├── fixtures/                    # manifest.json + operations.json + studyo.* (CoreTemplate1'den KOPYA) + gen.config örnekleri
├── plugins/codegen-spring/      # Claude Code plugin: skills/base-springboot-rest/
├── .claude-plugin/marketplace.json
├── scripts/pack-plugin-bundles.sh
├── docs/referans/               # CoreTemplate1 davranış sözleşmeleri (bu SPEC'in ekleri)
├── SPEC.md                      # bu dosya
└── tasks/                       # IMPLEMENTATION-PLAN + task dosyaları
```

Maven modül adları/artefaktlar: `com.vennyx.techgen:gen-core|gen-spring|gen-cli|conformance`,
sürüm `0.1.0-SNAPSHOT`. Paket kökleri: `techgen.core`, `techgen.spring`, `techgen.cli`,
`techgen.conformance`.

## 5. gen-core — pipeline sözleşmesi

`docs/referans/gen-core-davranis-sozlesmesi.md`'nin Java gerçeklemesi. Kritik uyarlamalar:

- **Model:** Java `record`'lar; Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false` (bilinmeyen alan sessiz),
  camelCase (Jackson default), case-insensitive property kabulü (`ACCEPT_CASE_INSENSITIVE_PROPERTIES`).
- **ExprNode:** custom `JsonDeserializer` — ayrımcı sıra: `node` (agg/call/default→Binary) → `path` →
  `kind` (duration/string/number/boolean; bilinmeyen literal-kind → `JsonProcessingException`) →
  hiçbiri → hata. Bilinmeyen NodeKind **toleranslı** BinaryNode. Round-trip serializer dahil.
- **Hatalar:** `LoadError`, `JoinError`, `ModelError` (rezerve), `UnsupportedConstruct` — aynı
  fırlatılma koşulları (manifest fatal; contract eksik/bozuk→null; linked'de JoinError'a yükselir).
- **Sıralama:** `Comparator.comparing(..., String::compareTo)` — UTF-16 kod-birimi sırası. Golden
  determinizm testi .NET ordinal ile eşdeğerliği fixture üzerinde kanıtlar.
- **TestPlanBuilder:** Derive (produced/needed/WriteSet), Single/Ambiguous/Missing sınıflaması
  (asla creator seçilmez), deterministik topo-sort + döngüde en-küçük-ordinal fallback — birebir.
- **BuildReport/Completeness:** `Covers` case-insensitive construct + `IdMatches` sınır-ayracı
  (`#`,`-`,`:`,` `) — birebir. Census construct adları ve owner-id şablonları (`{ext}.{op}`,
  `{op}:{proto}`, `{op}->{err}`, `{en}.{f}`, `@{ns}.{name}`) **birebir aynı** (dil-nötr sözleşme).
  build-report.json biçimi birebir: `{constructs:[{construct,id,status,reason}], policies:{}}`,
  ordinal sıralı, pretty, null-atlamalı, sonda `\n`.

## 6. gen-spring — emisyon sözleşmesi

### 6.1 Üretilen uygulama yerleşimi

```
<targetDir>/
├── pom.xml                          # HumanShell (WriteIfAbsent): <parent> → gen/parent/pom.xml
├── gen/
│   ├── parent/pom.xml               # WriteAlways: ÜRETEÇ-SAHİBİ parent POM — Spring Boot 3.5.x parent,
│   │                                #   Java 21, bağımlılıklar (web, data-jpa, provider driver'ı),
│   │                                #   build-helper (gen/java + gen/test-java source-root ekleme)
│   ├── java/app/...                 # WriteAlways üreteç-sahibi Java kaynak kökü (aşağıda envanter)
│   └── test-java/app/...            # WriteAlways üretilen test iskeleti
├── src/main/java/app/...            # HumanSeam/HumanShell ağacı (WriteIfAbsent)
│   └── Application.java             # HumanShell: @EnableAutoConfiguration + @Import(GeneratedBootstrap)
│                                    #   (component-scan YOK — §12/4 tam-açık kayıt kararı)
├── src/main/resources/application.yml  # HumanShell
├── src/test/java/app/...            # HumanSeam: test ARRANGE seam'leri
├── build-report.json                # her run yazılır
└── provenance.json                  # her run yazılır (yalnız Generated dosyalar)
```

**Generated.props'un Java karşılığı = üreteç-sahibi parent POM** (`gen/parent/pom.xml`): Maven'de
fragment-import olmadığı için bağımlılık/plugin yönetimi parent'a taşınır; insan `pom.xml`'i bir kez
üretilir ve parent'ı işaret eder. gen/java ile src/main/java **aynı `app.*` paketlerini** paylaşır
(iki source-root, tek derleme birimi — Generation Gap için şart).

### 6.2 Seam mekaniği — Generation Gap (Java'da partial yok)

.NET partial-class ailesinin Java karşılığı:

```
gen/java/app/{module}/{op}/{Op}HandlerBase.java   (WriteAlways, abstract)
    ├── adlı üyeler: validation0..N / rule0..N / permit0 (Guards'tan delege veya static import),
    │   hata fabrikaları, sabitler (REQUIRED_ROLES, IDEMPOTENCY_KEYS, ...), DI alanları
    │   (repository'ler, I{Ext} client'lar, IdempotencyStore, EventBus)
    └── public abstract Result<{Ret}> execute({Op}Command request);

src/main/java/app/{module}/{op}/{Op}Handler.java  (WriteIfAbsent, insan-sahibi)
    └── extends {Op}HandlerBase; execute gövdesi:
        throw new UnsupportedOperationException("{op}: iş mantığı doldurulacak");
```

- Bean kaydı **gen-owned** `GeneratedBootstrap`/modül wiring `@Configuration`'ında `@Bean` ile yapılır
  (human sınıfına anotasyon konmaz; component-scan'e güvenilmez — determinizm + izlenebilirlik).
- Aynı desen trigger (`start()`), subscription consumer (`handle(event)`), boundary client
  (interface impl) ve test ARRANGE (`arrange{Op}()`) için geçerlidir.
- Boş marker metinleri (birebir şablon):
  - Handler: `throw new UnsupportedOperationException("{opId}: iş mantığı doldurulacak");`
  - Trigger: `"...{opId}{T}Trigger.start: doldurulacak"` / Subscription: `"{cls}.handle: doldurulacak"`
  - Boundary: `"{Ext}.{op}: doldurulacak"` / Test ARRANGE: `return null; // doldurulacak` + yorum
- **Brownfield migrasyon** (.NET `MigrateSeamIfFlat` karşılığı): eski düz yerleşimdeki seam dosyası
  yeni slice klasörüne taşınır (kopyalanmaz) — WriteIfAbsent'tan önce.

### 6.3 Emisyon envanteri (gen/java/app altı)

.NET envanterinin (bkz. referans §1) Java eşleniği. Op-slice paket: `app.{module()}.{op()}` (tüm
paket segmentleri lowercase; sınıf adları PascalCase).

| .NET | Java (gen/java/app/...) |
|---|---|
| Result.g.cs | `Result.java` — `sealed interface Result<T> permits Success, NotAuthenticated, NotAuthorized, NotValid, NotProcessable, ServerError` + record'lar + `Unit`, `Page<T>(List<T> items, String nextCursor)` |
| ResultHttp.g.cs | `ResultHttp.java` — `ResponseEntity<?> toHttp(Result<?>)`: Success→200, NotAuthenticated→401, NotAuthorized→403, NotValid→400 `{errors}`, NotProcessable→422 `{code,message}`, ServerError→500 `{message}`; process-global `override` hook |
| AppDbContext.g.cs | `{module}/{Entity}Repository.java` — `interface extends JpaRepository<{Entity}, String>` (entity başına) + `PersistenceConfig.java` (`@EnableJpaRepositories`/`@EntityScan` gen+src paketleri) |
| Entities.g.cs | `{module}/{Entity}.java` — `@Entity`; alan eşlemesi Naming tablosuna göre; optimistic → `@Version private long version;` |
| Types.g.cs | `{module}/{TypeId}.java` — enum → `enum`; composite → `record` (JPA'da kullanım: `@Embeddable` DEĞİL, düz record — .NET paritesi) |
| Errors.g.cs | `{module}/Errors.java` — `public static final String {Id} = "{Id}";` + resultType yorumu |
| Events.g.cs / EventBus.g.cs | `{module}/{Event}.java` record + `EventBus.java` (`interface EventBus { void publish(Object event); }` + `OutboxEventBus` stub) |
| {Entity}.Invariants.g.cs | `{module}/{Entity}Invariants.java` — static tipli predicate metotları |
| {Op}.g.cs | `{op}/{Op}Command|Query.java` record + `{Op}HandlerBase.java` (abstract, §6.2); note→Javadoc |
| {Op}.Endpoint.g.cs | `{op}/{Op}Endpoint.java` — `@RestController`; verb eşleme POST/PUT/PATCH→`@{Verb}Mapping`+`@RequestBody`, GET/DELETE→path/query bind; internal/non-rest → controller emit edilmez |
| {Op}.Guards.g.cs | `{op}/{Op}Guards.java` — `validation0..`/`rule0..`/`permit0` static tipli metotlar + input record'ları |
| {Op}.Auth.g.cs | `{Op}HandlerBase` içinde `REQUIRED_ROLES`/`REQUIRED_SCOPES`/`OWNERSHIP` sabitleri |
| {Op}.Page.g.cs | HandlerBase'te `PAGINATION_STRATEGY`/`DEFAULT_PAGE_SIZE` sabitleri |
| {Op}.Idem.g.cs / Idempotency.g.cs | HandlerBase'te `IDEMPOTENCY_KEYS` + kök `IdempotencyStore.java` (in-memory impl) |
| {Op}.Throws.g.cs | HandlerBase'te `THROWABLE_ERRORS` + tipli hata fabrikaları (resultType→Result alt-tipi eşlemesi birebir) |
| {Op}.Consistency.g.cs | HandlerBase'te `CONSISTENCY_RISK`/`CONSISTENCY_MODE` (yalnız mode≠null VEYA eventual) |
| {Op}.Ext.g.cs | HandlerBase'te tanınan ext sabitleri + prelude yorumları |
| {Op}.Trigger.g.cs | `{op}/{Op}{T}TriggerBase.java` (abstract, `SmartLifecycle` iskeleti) + human `{Op}{T}Trigger` seam |
| Subscriptions.g.cs | kök `Subscriptions.java` kayıt + `{consumerModule}/{op}/{Event}To{Op}ConsumerBase.java` + human seam |
| Boundary.g.cs | `boundary/{Ext}.java` interface + human `src/.../boundary/{Ext}Client.java` seam; `{Ext}{Op}Validation.java` caller-side |
| Uncharted/{Name}.g.cs | `uncharted/{Name}.java` stub interface+client + OWNED entity/type sınıfları |
| Host.g.cs | `DeploymentTopology.java` — `Map<String, List<String>>` |
| Bootstrap.g.cs / Wiring.g.cs | `GeneratedBootstrap.java` (`@Configuration`, modül wiring'leri `@Import`) + `{module}/{Module}Wiring.java` (`@Configuration`, op bean'leri + controller kayıtları) |
| GlobalUsings.g.cs | Java karşılığı YOK (import'lar dosya başına emit edilir) — build-report'ta karşılığı aranmaz |
| tests/gen/Fixture.g.cs | `gen/test-java/app/Fixture.java` — context bootstrap + H2 override + `run(handlerClass, request)` + `get(entityClass, id)` + `reset()` |
| tests/gen/{Scope}/{Name}.g.cs | `gen/test-java/app/{scope}/{Name}Test.java` — JUnit 5, 3-faz owned iskelet + abstract arrange bildirimi |
| tests/src/{...}.Logic.cs | `src/test/java/app/{scope}/{Name}Arrange.java` — WriteIfAbsent ARRANGE seam |

**GenHeader (üreteç-sahibi `.java` dosyalarının ilk satırları, birebir):**
```java
// <auto-generated/>  — techgen-spring; elle DÜZENLEME: her üretimde ezilir
```

### 6.4 Naming — manifest skaler → Java eşlemesi

| Manifest | Java |
|---|---|
| ID, String, `*Id` soneki | `String` |
| Decimal | `BigDecimal` |
| Int | `int` (nullable bağlamda `Integer`) |
| Bool/Boolean | `boolean` / `Boolean` |
| DateTime | `Instant` |
| Date | `LocalDate` |
| Duration | `Duration` |
| collection=true | `List<T>` |
| diğer adlar | passthrough (üretilen tip) |

Üye adlandırma: metotlar/alanlar camelCase (`validation0`, `rule0`, `execute`); sabitler
UPPER_SNAKE (`REQUIRED_ROLES`); sınıflar PascalCase; paketler lowercase.

### 6.5 Java predicate renderer (INV-4'ün Java'ya özgü kritik kısmı)

ExprBuild'in nötr AST yürüyüşü gen-core'da; **render stratejisi gen-spring'de**:
- Path → `request.{camelField}()` (record accessor) ya da bağlama göre tipli input record accessor'ı;
  collision-safe camel-join (`resource.creditLimit` → `resourceCreditLimit`).
- Karşılaştırma tip-duyarlı: **BigDecimal bağlamında** `a.compareTo(b) {op} 0` biçimi
  (`amount > 0` → `request.amount().compareTo(new BigDecimal("0")) > 0`); **String eşitliği**
  `a.equals(b)`; primitif sayısal → doğal operatörler. Tip-baskınlık: Decimal > Double > Int (birebir).
- **Temporal (Date/DateTime) alanlar da compareTo formuna alınır** (T6.3-FIX #3): nötr `Date`→
  `LocalDate`, `DateTime`→`Instant` (§6.4); Java'da bu tipler `>=`/`<=`/`>`/`<` operatörünü
  DESTEKLEMEZ (derlenmez) — CoreTemplate1'de `DateTime` operatör-aşırıyüklemesiyle doğal
  çözüldüğünden bu, .NET'ten **bilinçli render-sapması** (davranışsal parite: aynı karşılaştırma
  semantiği, dile-özgü render — BigDecimal ile aynı gerekçe).
- and→`&&`, or→`||`, `=`→eşitlik; her binary parantezli; distinct path sırası korunur.
- AggNode/CallNode/DurationNode → `UnsupportedConstruct`.

### 6.6 gen.config.json (dile-özel)

`GenConfig(String dbProvider, String testDbProvider)` — şema birebir iki alan.
- `dbProvider` whitelist: `postgres` | `sqlserver` | `h2` | `inmemory` (→H2 in-memory). Etkiler:
  parent POM'a driver bağımlılığı + `application-gen.yml`/datasource kaydı. null → seam yorumu;
  whitelist-dışı (ör. `sqlite`) → `Unsupported("dbProvider", ...)` + seam. (.NET whitelist'inden fark:
  sqlite yerine h2 — Java ekosistem gerçeği; policy olarak rapor edilir.)
- `testDbProvider` default `inmemory` (H2, test başına benzersiz db-adı **runtime'da** üretilir —
  emit-time deterministik metin).

### 6.7 Construct disposition paritesi

Referans §7 tablosu birebir uygulanır: aynı `Realized/Unsupported` koşulları, aynı policy adları
(`deployment-topology`, `saga-orchestration-state=in-memory`, `consistency-mode`, `dedup-store=in-memory`,
`pagination-strategy`, `cursor-token=opaque`, `trigger-wiring` (değer: `SmartLifecycle stub`),
`http-binding`, `guard-linkage`, `source-of-truth`, `uncharted-realization`, `visibility`,
`serving-{proto}`, `{ns}-realization`). **serving non-rest → Unsupported (exit'i etkilemez).**

## 7. gen-cli

`java -jar gen-cli.jar <manifest.json> <outDir>` — akış: load → join/GM → config → emit →
`Completeness.check` → build-report.json + provenance.json yaz → **exit = silentDrop yoksa 0, varsa 1**.
Konsol çıktı biçimi birebir (`emit → {outDir} (clean=..., constructs=..., silentDrops=...)` +
`⚠ SESSİZ DROP: ...`). Shaded (fat) jar — SDK'sız `java -jar` ile koşar (skill bundle'ı için).

## 8. Conformance runner (Java)

Referans §A sözleşmesinin Java gerçeklemesi:
- `java -jar conformance.jar <appClasspath> <specsPath>` — `<appClasspath>` = üretilen app'in
  derlenmiş sınıfları + bağımlılık classpath'i (`target/classes` + `mvn dependency:build-classpath`
  çıktısı ya da `:` ile bitişik path listesi).
- İzole `URLClassLoader` (child-first değil; Spring sınıfları runner'la paylaşılır — runner'ın kendisi
  Spring-core/context'i provided taşır) → `app.GeneratedBootstrap` + `AnnotationConfigApplicationContext`
  (web-server'sız) → `{opId}Handler` bean'ini tip adından çöz → `execute(request)` reflection invoke →
  dönen `Result` alt-tipinin `getClass().getSimpleName()` + varsa `code()` → SPEC assert.
- SPEC JSON şeması birebir (`{construct,opId,arrange,act,assert}`; dosya=1 spec; stub→SKIP).
- Invariant property: 50 tur, `new Random(20260625)` ile `[0,1000)` BigDecimal üretimi, ilk numeric
  alan değiştirilir, Success.value alanı predicate'e karşı denetlenir — deterministik.
- Exit 0/1/2 ve `[PASS|FAIL|SKIP] construct/opId: detail` + özet satırı birebir.
- Acceptance testleri: doğru-seam→PASS, yanlış-seam→FAIL(+seçicilik), property-üreteci-koşuyor.

## 9. Test stratejisi (üreteç repo'sunun kendi testleri)

Referans §B'deki davranışlar JUnit 5 ile port edilir:
- **Golden snapshot:** `gen-spring/src/test/resources/golden/emit-snapshot.txt` — `relpath\tsha256`,
  ordinal; `UPDATE_GOLDEN=1` ile yenilenir. build-report/provenance hariç.
- **Characterization:** write-only-if-changed (mtime), prune-keeps-human, HumanShell hayatta kalır.
- **Emit davranışları:** üretilen app **`mvn -q compile` exit 0** + 0 silentDrop; request record +
  HandlerBase imzası; JPA + @Version; error+throws fabrikası; ResultHttp; pagination; byte-determinizm
  (iki dizinde emit özdeş).
- **LatentConstruct portu:** fixture in-memory mutasyonlarıyla her keyword'ün emit+census kapsaması
  (grpc/queue→explicit Unsupported dahil).
- **Ratchet:** `KnownDebt` allowlist'i boş başlar; yeni silentDrop testi kırar.
- **Ölçek:** `studyo.manifest.json` (43 op) emit + compile smoke.
- **Süre bütçesi:** `mvn compile` içeren testler `@Tag("e2e")` — CI'da ayrı aşama.

## 10. base-springboot-rest skill paketi

`plugins/codegen-spring/skills/base-springboot-rest/`:
- **capability.json** — id `techgen-spring`, invocation `java -jar ${CLAUDE_SKILL_DIR}/techgen/gen-cli.jar`,
  languages `["java"]`, persistence `["postgres","sqlserver","h2","inmemory"]`, transports `["rest"]`,
  constructsCovered birebir 31'lik liste; emissionContract: seamPath şablonları
  (`src/main/java/app/{module}/{op}/{Op}Handler.java` vb.), `emptyStubMarker: "doldurulacak"`,
  `ownedTree: "gen/**"`, humanTree `["src/**","pom.xml"]`, handlerSurfaceMap (Java üye adlarıyla),
  canonicalOrder birebir (idempotency→authz→validation→external-input→rule→entity+invariant→persist→
  emit→return); build `mvn -q -f {targetDir}/pom.xml compile` success exit 0; conformance `java -jar
  ${CLAUDE_SKILL_DIR}/conformance/conformance.jar {appClasspath} {specsPath}`; audit bölümü birebir.
- **SKILL.md** — [dsl-generator] token'lı description; describe-modu sözleşmesi; 6 faz + Faz 0.5 birebir
  (referans §C mekanikleri); Java/Spring bağlamına uyarlanmış altın kurallar.
- **references/** — gap-protocol.md (K1-K4, rung 1-4, §0.5, ASSUMPTIONS.md), verify-loop.md (Kapı 0-3,
  retry≤3+fresh-start-1, halüsinasyon kapısı), archetype-playbooks.md (8 arketip, kanonik sıralar,
  Spring idyomlu few-shot'lar: `@Transactional` sınırı, JpaRepository, ResponseEntity), gap-registry.md.
- **evals/evals.json** — 9 senaryonun Java uyarlaması.
- **techgen/** + **conformance/** — bundle edilen shaded jar'lar (pack script kopyalar).
- Kök: `.claude-plugin/marketplace.json` + `plugins/codegen-spring/.claude-plugin/plugin.json`.

## 11. Sınırlar

- **Always:** determinizm; build-report; gerçek-derleme doğrulaması; WriteIfAbsent seam; INV-7 gate;
  CoreTemplate1'e dokunmama.
- **Ask first:** üretilen app'in hedef şeklini değiştirmek; yeni dış bağımlılık; whitelist genişletme;
  manifest şemasına alan ekleme (sözleşme ortak — tek taraflı değişmez).
- **Never:** dile-özel manifest varyantı; sessiz construct düşürme; framework icadı; LLM-judge;
  `gen/**`'e insan/LLM yazımı; Repository origin'e onaysız push.

## 12. Tasarım çatalları (KARARA BAĞLANDI — kullanıcı onayı 2026-07-03)

1. **Seam mekaniği = Generation Gap (abstract base + human subclass)** — §6.2'deki gibi. ✅ ONAYLI.
2. **Parent POM yaklaşımı** (üreteç-sahibi `gen/parent/pom.xml`) — Generated.props'un Maven karşılığı.
   ✅ ONAYLI.
3. **dbProvider whitelist'inde sqlite→h2 ikamesi** — Java ekosistem gerçeği. ✅ ONAYLI.
4. **Bean kayıt stratejisi = TAM-AÇIK KAYIT** ✅ ONAYLI (2026-07-03, uyumluluk raporu §4.1 seçenek A):
   controller'lar dahil TÜM üretilen bean'ler gen-owned Wiring/Bootstrap `@Bean` kayıtlarıyla girer;
   `Application.java` component-scan YAPMAZ (`@SpringBootApplication` yerine
   `@EnableAutoConfiguration + @Import(GeneratedBootstrap.class)`). Endpoint sınıfları `@RestController`
   anotasyonunu TAŞIR (mapping keşfi için) ama scan'le değil `@Bean` ile kaydedilir. Gerekçe:
   conformance/test context'i ≡ production context; K3 denetiminin tek kaynağı Bootstrap/Wiring;
   izlenebilirlik. İnsan kendi bean'lerini Application.java'dan (insan-sahibi) kaydeder ya da
   bilinçli olarak kendi `@ComponentScan`'ini açar.
