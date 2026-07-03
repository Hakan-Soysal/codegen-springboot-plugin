# techgen-spring — İmplementasyon Planı

> Kaynak tasarım: `SPEC.md` (+ ekleri `docs/referans/*.md`). Bu plan `project-manager` skill'i ile
> Sonnet executor agent'lara dağıtılmak üzere yazılmıştır. Kritik yol task'ları (🔴) `tasks/T-*.md`
> dosyalarında **tam format** (10 bölüm); düşük riskli task'lar (🟢) bu dosyada **kısa format**.

## 0. Nasıl okunmalı

- Her task tek bir executor-agent oturumunda bitecek boyuttadır.
- Executor bir task'a başlamadan önce task'ın **Inputs** listesindeki her dosyayı okur; Pre-conditions
  komutlarını koşar; herhangi biri FAIL ise DURUR ve PM'e rapor eder.
- **CoreTemplate1 READ-ONLY'dir**: `/Users/hakansoysal/Desktop/ClaudeCode Denemeler/CoreTemplate1`
  altındaki dosyalar okunabilir, ASLA yazılamaz. Tüm yazımlar bu repo
  (`/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template`) içinde kalır.
- "Bitti" = tüm Acceptance testleri GERÇEKTEN koşuldu + çıktılar okundu + DoD işaretlendi.
  `mvn -q compile` / `mvn -q test` çıktısı görülmeden "derleniyor" DENMEZ.
- Golden dosya güncellemesi yalnız bilinçli davranış değişikliğinde, `UPDATE_GOLDEN=1` ile yapılır ve
  diff gerekçesi task raporuna yazılır.

## 1. Bağımlılık haritası ve paralel pencereler

**Not: ID sırası ≈ yürütme sırası, tek istisna M1 içi: T1.1 (ExprNode) → T1.2 (model) → T1.3 (loader)
zaten bu sırada numaralandırıldı.** Graf gerçek topolojik sıraya göredir:

```
T0.1 ─┬─> T0.2
      └─> T1.1 ──> T1.2 ──> T1.3
                     │
        ┌────────────┼─────────────┬──────────────┐
        v            v             v               v
      T2.1         T2.2          T2.3            T2.4          (paralel pencere P1)
        │            │             │               │
        │            │             └───────┬───────┘
        │            │                     v
        │            │                   T3.1
        │            │                     │
        │            │            ┌────────┴────────┐
        │            │            v                 │
        │            │          T3.2                │
        │            │            │                 │
        │            │      ┌─────┴──────┬──────────┤
        │            │      v            v          v
        │            │    T3.3         T3.4       T3.5 <──(T2.1: TypeEnv)   (P2: T3.3/T3.4/T3.5 paralel)
        │            │      └─────┬──────┴──────────┘
        │            │            v
        │            │          T3.6 ──> T3.7
        │            │            │
        │            │   ┌────────┼─────────┬─────────┐
        │            │   v        v         v         v
        │            │ T4.1     T4.2      T4.3      T4.4      (P3: paralel)
        │            │   └────────┴────┬────┴─────────┘
        │            │                 v
        │            │               T4.5
        │            │                 │
        │            │                 v
        │            │               T5.1
        │            │                 │
        │            │        ┌────────┼────────┬──────────┐
        │            v        v        v        v          v
        │          T7.1 <── (T2.2)   T6.1     T6.2       T6.3   (P4: paralel; T7.1 ayrıca T3.6'ya bağlı)
        │                              │        │          │
        │                              └────┬───┴──────────┘
        │                                   v
        └──────────────────────────────>  T8.1 ──> T8.2
                                            │
                                            v
                                          T9.1 ──> T9.2
                                            │        │
                                            v        v
                                          T9.3 ──> T10.1
```

> **Dipnot (T2.1↔T2.2):** `GenerationModel.testPlan` alanının TİPİNİ (`TestPlan`) T2.2 üretir; T2.1
> geçici boş-record placeholder'ı ile derlenir (hard bağımlılık YOK, bu yüzden grafa kenar
> ÇİZİLMEMİŞTİR). PM, P1 penceresinde ID sırasını (T2.1→T2.2) koruyarak placeholder'ın ömrünü
> kısaltmalı; T2.2 placeholder'ı gerçek IR ile değiştirir.

Milestone-seviyesi genel kurallar (LIFT):
- **M3+ hiçbir task'ı M2 tamamlanmadan başlamaz** (report/GM API'leri).
- **M6/M7/M8 task'ları T5.1 tamamlanmadan başlamaz** (CLI uçtan-uca zinciri golden/E2E'nin ön-şartı;
  istisna: T7.1 emitter API ile koşabilir ama DoD'sindeki E2E adımı T5.1 ister).
- **M9 task'ları M6 yeşil olmadan başlamaz** (davranış kilitlenmeden skill sözleşmesi yazılmaz).

## 2. Milestone'lar

### M0 — Bootstrap ve araç zinciri
**Amaç:** Derlenen boş multi-module Maven iskeleti + fixture'lar + pinlenmiş sürümler.
**Milestone DoD:** `mvn -q compile` kökten exit 0; `git log` ilk commit; `fixtures/` dolu;
`docs/surumler.md` pinli sürümleri listeliyor.

#### T0.1 🟢 — Maven multi-module iskeleti + git init
**Önkoşullar:** yok. **Referans:** SPEC §4.
**Yapılacaklar:**
- `git init` (branch: main). `.gitignore`: `target/`, `.idea/`, `*.iml`, `.DS_Store`.
- Kök `pom.xml` (packaging=pom, `com.vennyx.techgen:techgen-parent:0.1.0-SNAPSHOT`):
  `<maven.compiler.release>21</maven.compiler.release>`, UTF-8, modüller: gen-core, gen-spring,
  gen-cli, conformance. `dependencyManagement`: Jackson BOM (2.x güncel), JUnit 5 BOM.
- Dört modül pom'u + boş paket dizinleri: `techgen.core`, `techgen.spring` (gen-core'a bağımlı),
  `techgen.cli` (gen-core+gen-spring), `techgen.conformance` (bağımsız; Spring context bağımlılıkları
  T8.1'de eklenecek).
- Her modüle 1 placeholder sınıf + 1 smoke test (JUnit 5) — `mvn -q test` yeşil.
**Dosyalar:** `pom.xml`, `gen-core/pom.xml`, `gen-spring/pom.xml`, `gen-cli/pom.xml`,
`conformance/pom.xml`, `.gitignore`.
**DoD:**
- [ ] `mvn -q test` kökten exit 0 (çıktı okundu)
- [ ] `java -version` 21.x doğrulandı; `docs/surumler.md`'ye yazıldı
- [ ] İlk commit atıldı (`git log --oneline` 1 satır)
- [ ] CoreTemplate1'e hiçbir yazma yok (`git -C <CoreTemplate1> status` temiz — salt kontrol)

#### T0.2 🟢 — Fixture'lar + sürüm pinleme
**Önkoşullar:** T0.1 done. **Referans:** SPEC §2, §9; CoreTemplate1 `tests/fixtures/`.
**Yapılacaklar:**
- CoreTemplate1 `tests/fixtures/{manifest,operations,studyo.manifest,studyo.operations}.json` →
  bu repo `fixtures/` altına **kopyala** (içerik değiştirilmez; kaynak dosyalara dokunulmaz).
- `fixtures/gen.config.ornek.json` yaz: `{"dbProvider":"h2"}`.
- Context7 ile pinle ve `docs/surumler.md`'ye yaz: Spring Boot 3.5.x en güncel patch, Jackson,
  JUnit 5, build-helper-maven-plugin, maven-shade-plugin sürümleri. (Bu task'ın executor'ı Context7
  MCP'sine erişemiyorsa Maven Central üzerinden doğrular ve bunu raporlar.)
**Dosyalar:** `fixtures/*.json`, `docs/surumler.md`.
**DoD:**
- [ ] 4 fixture byte-aynı kopyalandı (`diff` ile doğrulandı, çıktı boş)
- [ ] `python3 -c "import json,glob;[json.load(open(f)) for f in glob.glob('fixtures/*.json')]"` exit 0
- [ ] `docs/surumler.md` en az 5 pinli sürüm içeriyor + doğrulama kaynağı yazılı

---

### M1 — gen-core: model katmanı
**Amaç:** manifest/operations şemalarının ve ExprNode AST'nin Java modeli — davranış sözleşmesi birebir.
**Milestone DoD:** `mvn -q -pl gen-core test` yeşil; fixture'daki iki dosya + studyo çifti hatasız
parse ediliyor; ExprNode round-trip kayıpsız.

- **T1.1 🔴 — ExprNode AST + polymorphic (de)serializer + hata tipleri** → `tasks/T1-1-exprnode.md`
- **T1.2 🔴 — Manifest/Contract POJO'ları + Jackson yapılandırması** → `tasks/T1-2-model.md`

#### T1.3 🟢 — Loader (fatal vs sessiz-null)
**Önkoşullar:** T1.2 done (`techgen.core.Json`, `ManifestJson`, `ContractFile` mevcut).
**Referans:** SPEC §5; `docs/referans/gen-core-davranis-sozlesmesi.md` §4, §8.
**Yapılacaklar:**
- `techgen.core.pipeline.Loader`: `loadManifest(Path)` — dosya yok → `LoadError("manifest bulunamadı: {path}")`;
  parse hatası → `LoadError("manifest ayrıştırılamadı: {msg}")`. `loadContract(Path manifestPath, String contractPath)` —
  null contractPath → null; yol manifest dizinine göreli çözülür; **dosya yok VEYA parse hatası → null (throw YOK)**.
- Unit testler: 4 pozitif (fixture manifest+contract, studyo çifti) + 4 negatif (yok-dosya→LoadError,
  bozuk-json→LoadError, contract-yok→null, contract-bozuk→null).
**Dosyalar:** `gen-core/src/main/java/techgen/core/pipeline/Loader.java` + test.
**DoD:**
- [ ] `mvn -q -pl gen-core test` exit 0; 8 test görüldü
- [ ] Negatif testler assert'leri mesaj içeriğini de doğruluyor ("bulunamadı"/"ayrıştırılamadı")

---

### M2 — gen-core: pipeline (join/GM/TestPlan/report)
**Amaç:** GM inşası, determinizm sıralaması, TestPlan türetimi, INV-7 census/gate — birebir.
**Milestone DoD:** `mvn -q -pl gen-core test` yeşil; fixture GM'i .NET davranışıyla eşdeğer
(op sırası, JoinError koşulları, census listesi) — testlerle sabitlenmiş.

- **T2.1 🔴 — GmBuilder + TypeEnv + nötr ExprWalk** → `tasks/T2-1-gmbuilder.md`
- **T2.2 🔴 — TestPlanBuilder** → `tasks/T2-2-testplan.md`
- **T2.3 🔴 — BuildReport + Census + Completeness gate** → `tasks/T2-3-report.md`

#### T2.4 🟢 — Provenance IO
**Önkoşullar:** T0.1 done. (gen-spring modülünde yaşar; T3.1'in girdisidir.)
**Referans:** `docs/referans/gen-dotnet-emisyon-sozlesmesi.md` §5.
**Yapılacaklar:**
- `techgen.spring.ProvenanceIo`: `Provenance(generator, version, files[])`,
  `ProvenanceEntry(path, clazz, sha256)`; dosya adı `provenance.json`; yalnız `Generated` sınıfı
  yazılır; path outDir-göreli `/` ayraçlı ordinal-sıralı; sha256 = UTF-8 → lowercase hex.
- **Atomik yazım**: temp dosya + `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`; içerik sonu `\n`.
- `tryRead(Path)`: yok/bozuk → null (prune atlanır — canlı dosya silinmez).
- Unit test: yaz-oku round-trip; bozuk dosya → null; sıralama deterministik.
**Dosyalar:** `gen-spring/src/main/java/techgen/spring/ProvenanceIo.java` + test.
**DoD:**
- [ ] `mvn -q -pl gen-spring test` exit 0
- [ ] Round-trip + bozuk-dosya-null + ordinal-sıra testleri var ve geçiyor

---

### M3 — gen-spring: çekirdek emisyon
**Amaç:** Fixture'dan derlenen Spring Boot app'i üreten dikey dilim: yazım altyapısı → app iskeleti →
tipler/Result → JPA → predicate/guards → operation slice.
**Milestone DoD:** `fixtures/manifest.json`'dan emit edilen hedef app `mvn -q compile` exit 0;
insan seam'leri WriteIfAbsent + marker'lı; build-report'ta çekirdek construct'lar realized.

- **T3.1 🔴 — Emit yazım altyapısı (WriteAlways/WriteIfAbsent/prune)** → `tasks/T3-1-emit-infra.md`
- **T3.2 🔴 — App iskeleti: parent POM + HumanShell'ler + Bootstrap/Wiring + Naming** → `tasks/T3-2-app-iskeleti.md`
- **T3.3 🔴 — Result taksonomisi + ResultHttp + Errors + Types + Events** → `tasks/T3-3-result-tipler.md`
- **T3.4 🔴 — Entity→JPA + repository + PersistenceConfig** → `tasks/T3-4-jpa.md`
- **T3.5 🔴 — Java predicate renderer + Guards + Invariants** → `tasks/T3-5-predicate.md`
- **T3.6 🔴 — Operation slice: request + HandlerBase + human seam + Endpoint** → `tasks/T3-6-operation-slice.md`

#### T3.7 🟢 — HandlerBase tamamlayıcıları: Auth / Throws / Consistency / Ext / Note
**Önkoşullar:** T3.6 done. **Referans:** SPEC §6.3; emisyon referansı §1 (Auth/Throws/Consistency/Ext) + §7.
**Yapılacaklar (hepsi `{Op}HandlerBase` emisyonuna bölüm ekler; koşullar birebir .NET):**
- roles/scopes/ownership varsa: `public static final List<String> REQUIRED_ROLES = List.of(...);`
  `REQUIRED_SCOPES`, `OWNERSHIP` (String). → `report.realized("roles"|"scopes"|"ownership", opId)`.
- throws varsa: `THROWABLE_ERRORS` array + hata fabrikaları — resultType→Result alt-tipi eşlemesi
  (NotValid→Map param, NotAuthorized/NotAuthenticated/ServerError→String param, tanınmayan→
  `NotProcessable(Errors.{Id}, message)`); fabrika adı camelCase error id. → `realized("throws", "{op}->{err}")` her biri.
- consistency (mode≠null || risk==eventual): `CONSISTENCY_RISK`/`CONSISTENCY_MODE` sabitleri +
  policy `consistency-mode={risk}/{mode}`; eventual→outbox yorum iskeleti. → `realized("consistency", opId)`.
- op Ext: prelude yorumları + tanınan sabitler (`@audit.logged`→`AUDIT_CATEGORY`, `@metric.emit`→
  `METRIC_NAME`, `@http.endpoint`→`HTTP_ROUTE`); policy `{ns}-realization`. → `realized("@{ns}.{name}", opId)`.
  **`@http` ns'li op ext varsa AYRICA** `policy("http-binding", "route/query/header detail")`
  (.NET `DotnetEmitter.cs:1160` `ExtPartial` paritesi; SPEC §6.7'nin `http-binding` policy'si; fixture
  `WriteAuditLog` `@http.endpoint` bunun canlı örneği).
- note: `realized("note", opId)` **T3.6 Step 5.1'de yapılır** (record Javadoc + realized birlikte);
  BURADA TEKRAR EDİLMEZ — çift entry olmasın (T4.5'in `visibility`'yi T3.6'ya bırakması ile aynı desen).
**Dosyalar:** `gen-spring/.../SpringEmitter.java` (HandlerBase bölümleri) + emit testleri.
**DoD:**
- [ ] Fixture'da WriteAuditLog (roles+scopes+ownership+ext+consistency mode=durable) ve CreateInvoice
  (throws+idempotent+ext) HandlerBase'lerinde ilgili bölümler görülüyor (test assert)
- [ ] build-report'ta roles/scopes/ownership/throws/consistency/@ns.name entry'leri realized
  (note T3.6'da realized); WriteAuditLog için `http-binding` policy'si var
- [ ] Üretilen app `mvn -q compile` exit 0

---

### M4 — gen-spring: ileri construct'lar (parite süpürmesi)
**Amaç:** Kalan tüm construct'lar birebir disposition'la; fixture'ın TAMAMI 0 silentDrop.
**Milestone DoD:** fixture emit → `Completeness.check` 0 silentDrop; grpc serving explicit
Unsupported; üretilen app compile yeşil.

#### T4.1 🟢 — Idempotency + Pagination
**Önkoşullar:** T3.6 done. **Referans:** emisyon referansı §1 (Idem/Page/Idempotency.g.cs) + §7.
**Yapılacaklar:**
- Herhangi op idempotent ise kök `gen/java/app/IdempotencyStore.java`: `interface IdempotencyStore
  { boolean tryBegin(String key); }` + `InMemoryIdempotencyStore` (ConcurrentHashMap) + Bootstrap bean
  kaydı; policy `dedup-store=in-memory`. Op HandlerBase'e `IDEMPOTENCY_KEYS` sabiti + ctor'a store.
  → `realized("idempotent", opId)`.
- pagination varsa: HandlerBase `PAGINATION_STRATEGY`/`DEFAULT_PAGE_SIZE`; dönüş tipi `Result<Page<{Ret}>>`;
  endpoint'e `cursor`/`size` query paramları; policies `pagination-strategy={strategy}`, `cursor-token=opaque`.
  → `realized("pagination", opId)`.
**DoD:**
- [ ] CreateInvoice (idem) + ListInvoices (cursor pagination) fixture'da doğru emit (test assert)
- [ ] Üretilen app compile yeşil; build-report policy'leri içeriyor

- **T4.2 🔴 — EventBus + emits + Subscription consumer + seam** → `tasks/T4-2-events-subscription.md`

#### T4.3 🟢 — Trigger (`@trigger.*` ext → SmartLifecycle stub + seam)
**Önkoşullar:** T3.6 done. **Referans:** emisyon referansı §1 (Trigger) + seam marker'ları §2.
**Yapılacaklar:**
- Op'ta `@trigger.{name}` ext varsa: gen `{op}/{Op}{T}TriggerBase.java` — abstract, `SmartLifecycle`
  metotları gen-owned (`isRunning`/`stop` basit state), `public abstract void start();` bildirimi;
  ctor'da `{Op}Handler` alanı. Human seam `src/.../{Op}{T}Trigger.java` (WriteIfAbsent):
  `start()` gövdesi `throw new UnsupportedOperationException("{opId}{T}Trigger.start: doldurulacak");`.
  Wiring'e bean kaydı. Policy `trigger-wiring=SmartLifecycle stub`. → `realized("@trigger.{name}", opId)`.
**DoD:**
- [ ] WriteAuditLog `@trigger.cron` fixture'da TriggerBase + seam üretiyor; ikinci emit seam'i EZMİYOR (test)
- [ ] Compile yeşil; policy + realized entry'ler var

- **T4.4 🔴 — Boundary externals + client seam + boundary validation + Uncharted** → `tasks/T4-4-boundary-uncharted.md`
- **T4.5 🔴 — Kalan dispositionlar: saga/calls/host/sourceOfTruth/visibility/serving/module-ext süpürmesi** → `tasks/T4-5-disposition-suprmesi.md`

---

### M5 — gen-cli + config
**Amaç:** Uçtan uca CLI zinciri + INV-7 exit sözleşmesi + shaded jar.
- **T5.1 🔴 — GenConfig + CLI main + exit-code + shaded jar** → `tasks/T5-1-cli.md`
**Milestone DoD:** `java -jar gen-cli/target/gen-cli.jar fixtures/manifest.json /tmp/out` exit 0,
build-report + provenance yazılmış, üretilen app compile yeşil.

---

### M6 — üreteç test süiti (golden + characterization + parite)
**Amaç:** Determinizm/regen/prune/INV-7 davranışlarını kalıcı regresyon ağına bağlamak.
**Milestone DoD:** `mvn -q test` kökten yeşil; golden snapshot commit'li; ratchet allowlist boş.

- **T6.1 🔴 — Golden snapshot + characterization + byte-determinizm** → `tasks/T6-1-golden.md`

#### T6.2 🟢 — Emit davranış + LatentConstruct + Report test portu
**Önkoşullar:** T6.1 done. **Referans:** `docs/referans/conformance-testler-skill-sozlesmesi.md` §B.
**Yapılacaklar:** Referans §B'deki EmitTests/LatentConstructTests/ReportTests/CompletenessTests
davranış listesini JUnit'e port et (her satır bir test; fixture in-memory mutasyonları `with`-benzeri
kopya kurucularla). Kritik olanlar: grpc/queue→explicit Unsupported; @internal route bastırma;
GET param binding; sourceOfTruth FK yorumu; Covers compound-id/prefix; KnownDebt ratchet (boş).
**DoD:**
- [ ] En az 25 yeni test; `mvn -q test` yeşil
- [ ] Her referans-§B maddesi ya test edildi ya da "kapsam dışı + neden" satırıyla task raporunda listelendi

#### T6.3 🟢 — E2E compile + studyo ölçek smoke
**Önkoşullar:** T5.1 done, T0.2 done.
**Yapılacaklar:** `@Tag("e2e")` iki test: (1) fixture emit → `mvn -q compile` exit 0 (üretilen app);
(2) `fixtures/studyo.manifest.json` emit → exit 0 + silentDrop=0 + compile yeşil. CI-dışı hızlı koşum
için tag'li ayrım.
**DoD:**
- [ ] İki test yeşil; studyo emit süresi + üretilen dosya sayısı rapora yazıldı

---

### M7 — üretilen test iskeleti (TestPlan → JUnit)
- **T7.1 🔴 — Fixture harness + test skeleton + ARRANGE seam emisyonu** → `tasks/T7-1-test-emisyonu.md`
**Milestone DoD:** fixture'dan üretilen app'te `gen/test-java` + `src/test/java` seam'leri var;
`mvn -q test-compile` yeşil; Single-dışı prereq'li testler `Unsupported("test-prereq")`.

---

### M8 — conformance runner (Java)
**Amaç:** Dil-nötr SPEC'leri üretilen app'e karşı koşan bağımsız runner.
**Milestone DoD:** acceptance testleri yeşil (doğru-seam→PASS, yanlış-seam→FAIL, property koşuyor);
shaded jar `java -jar` ile çalışıyor.

- **T8.1 🔴 — Spec DTO + SpecRunner + GeneratedApp bootstrap** → `tasks/T8-1-conformance-core.md`

#### T8.2 🟢 — Invariant property + acceptance + CLI main + shaded jar
**Önkoşullar:** T8.1 done. **Referans:** conformance referansı §A.2 (property), §A.4 (exit), §A.5.
**Yapılacaklar:**
- Invariant property dalı: 50 tur, `new Random(20260625)`, `[0,1000)` BigDecimal; ilk numeric alan
  değiştirilir; Success.value field predicate (`>=,>,<=,<,==,=`); ihlalde ilk karşı-örnek Fail;
  Success-olmayan tur skip.
- `Main`: `<appClasspath> <specsPath>`; dizin→recursive `*.json` ordinal; exit 0/1/2; çıktı
  `[PASS|FAIL|SKIP] construct/opId: detail` + `conformance: N pass, N fail, N skip`.
- maven-shade ile `conformance.jar`. Acceptance testleri (§A.5'in üçü) — fixture app'i emit+doldur+derle
  scaffold'ı test-fixture olarak.
**DoD:**
- [ ] 3 acceptance testi yeşil (`mvn -q -pl conformance test` çıktısı okundu)
- [ ] `java -jar conformance.jar` iki arg'sız → usage + exit 2 (elle koşuldu)

---

### M9 — base-springboot-rest skill paketi
**Amaç:** Keşfedilebilir ([dsl-generator]) LLM-doldurucu skill + bundle.
**Milestone DoD:** paket dizini tam; describe sözleşmesi + capability şeması .NET kardeşiyle yapısal
eşdeğer; jar bundle'ları pack script'iyle yerleşiyor.

- **T9.1 🔴 — capability.json + SKILL.md** → `tasks/T9-1-skill.md`

#### T9.2 🟢 — references/ + evals
**Önkoşullar:** T9.1 done. **Referans:** conformance referansı §C; CoreTemplate1 skill references/ (oku).
**Yapılacaklar:** 4 referans dosyasını (gap-protocol / verify-loop / archetype-playbooks / gap-registry)
Spring bağlamına uyarlayarak yaz — mekanikler (K1-K4, rung 1-4, §0.5 P1-P4, Kapı 0-3, retry≤3+fresh-start-1,
ASSUMPTIONS.md formatı, registry formatı) birebir; yalnız dil idyomları değişir (dotnet build→mvn compile,
partial→Generation Gap, EF→JPA, `@Transactional` sınırı, few-shot örnekler Java). `evals/evals.json`:
9 senaryonun Java uyarlaması (senaryo başlıkları/beklentileri korunur).
**DoD:**
- [ ] 4 referans + evals.json yazıldı; her dosyada .NET metaforu kalmadı (grep: "dotnet build", ".g.cs",
  "partial class" → yalnız karşılaştırma bağlamında)
- [ ] Kanonik sıra ve marker (`doldurulacak`) tutarlı (capability.json ile grep-eşleşme)

#### T9.3 🟢 — plugin.json + marketplace.json + pack script
**Önkoşullar:** T9.1 done; T5.1, T8.2 done (jar'lar). **Referans:** CoreTemplate1 `.claude-plugin/` +
`scripts/pack-plugin-bundles.sh` (oku, uyarla).
**Yapılacaklar:** `plugins/codegen-spring/.claude-plugin/plugin.json` (name `codegen-spring`, sürüm 0.1.0,
[dsl-generator] içerikli description); kök `.claude-plugin/marketplace.json`;
`scripts/pack-plugin-bundles.sh`: `mvn -q package` → gen-cli.jar + conformance.jar'ı skill'in
`techgen/` + `conformance/` klasörlerine kopyalar.
**DoD:**
- [ ] Script koşuldu; skill klasöründe iki jar var; `java -jar` ikisi de usage veriyor
- [ ] JSON'lar valid (`python3 -m json.tool`)

---

### M10 — uçtan uca doğrulama + dokümantasyon
#### T10.1 🟢 — E2E senaryo + README
**Önkoşullar:** M9 tamam.
**Yapılacaklar:**
- E2E el senaryosu (script'leştir: `scripts/e2e-demo.sh`): temiz dizine generate → `GetInvoice` seam'ini
  örnek impl ile doldur → `mvn -q compile` → basit conformance spec'i koş → PASS gör.
- `README.md`: kurulum, CLI kullanımı, pair mimarisi, skill kurulumu (CoreTemplate1 README yapısında).
- SPEC.md'yi gerçekleşen kararlarla senkronla (drift varsa güncelle + raporla).
**DoD:**
- [ ] `scripts/e2e-demo.sh` exit 0 (çıktı okundu; conformance satırında PASS görüldü)
- [ ] README kurulum komutları birebir çalışır durumda

---

## 3. Genel doğrulama adımları (her milestone sonunda standart)

1. `mvn -q test` kökten exit 0 (e2e tag'i M6+ sonrası dahil).
2. `git status` — yalnız task allowlist'indeki dosyalar değişmiş.
3. CoreTemplate1'e yazılmadığının kontrolü (salt-okunur değişmezi).
4. Golden değiştiyse: diff gerekçesi milestone raporunda.
5. Milestone DoD maddeleri tek tek işaretlendi; işaretlenemeyen madde = milestone BİTMEDİ.

## 4. Doküman bakımı

- Davranış SPEC'ten saparsa: önce SPEC güncellenir (gerekçeyle), sonra kod — asla sessiz sapma.
- `docs/referans/*.md` CoreTemplate1'in okumasıdır; CoreTemplate1 değişirse (beklenmiyor) yeniden türetilir.
- Task raporları `tasks/raporlar/T{X}-{Y}.md` olarak yazılır (executor çıktısı; PM toplar).

## 5. Sembol-Task Tablosu (author ön-taraması — reviewer bağımsız yeniden çıkarır)

| Sembol (tam nitelikli) | Üreten task |
|---|---|
| `techgen.core.model.ExprNode` (+Binary/Agg/Call/Path/Literal/Duration) | T1.1 |
| `techgen.core.errors.{LoadError,JoinError,ModelError,UnsupportedConstruct}` | T1.1 |
| `techgen.core.model.{ManifestJson,OperationJson,EntityJson,TypeJson,ErrorJson,EventJson,SubscriptionJson,ExternalJson,UnchartedJson,CallEdgeJson,GuardedExpr,AccessJson,ExtJson,...}` | T1.2 |
| `techgen.core.model.{ContractFile,ContractOp,ContractEntity,ContractAccess,ContractEffect,ProcessJson,FlowJson,...}` | T1.2 |
| `techgen.core.Json` (ObjectMapper fabrikası) | T1.2 |
| `techgen.core.pipeline.Loader` | T1.3 |
| `techgen.core.gm.{GenerationModel,GmOperation,TypeEnv}` · `techgen.core.pipeline.GmBuilder` · `techgen.core.predicate.ExprWalk` | T2.1 |
| `techgen.core.gm.{TestPlan,ProcessTest,ScenarioTest,PrereqStep,PrereqKind}` · `techgen.core.pipeline.TestPlanBuilder` | T2.2 |
| `techgen.core.report.{BuildReport,BuildEntry,ConstructStatus,Completeness}` | T2.3 |
| `techgen.spring.{ProvenanceIo,Provenance,ProvenanceEntry}` | T2.4 |
| `techgen.spring.EmitWriter` | T3.1 |
| `techgen.spring.{SpringEmitter,Naming}` | T3.2 |
| `techgen.spring.JavaPredicateRenderer` | T3.5 |
| `techgen.spring.GenConfig` · `techgen.cli.Main` | T5.1 |
| `techgen.conformance.{Spec,SpecAct,SpecAssert,SpecRunner,SpecResult,GeneratedApp}` | T8.1 |
| `techgen.conformance.Main` | T8.2 |
| Üretilen-app sembolleri (`app.Result`, `app.{module}.{Entity}`, `app.{module}.{op}.{Op}HandlerBase`...) | ilgili emisyon task'ının ÇIKTISIDIR (üreteç şablonu); tüketen testler o task'a bağımlıdır |
