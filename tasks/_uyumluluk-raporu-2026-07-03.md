# Uyumluluk Doğrulama Raporu

_Tarih:_ 2026-07-03 (UTC)
_Kaynak:_ SPEC.md (+3 referans eki: gen-core / gen-dotnet-emisyon / conformance-testler)
_Plan:_ tasks/IMPLEMENTATION-PLAN.md (+19 tam-format task dosyası)
_Reviewer:_ bağımsız Opus agent (subagent_type=general-purpose) — plan yazarı DEĞİL
_Çapraz-doğrulama:_ CoreTemplate1 (READ-ONLY) `DotnetEmitter.cs` / `Completeness.cs` + fixture `manifest.json`

---

## 1. Doc mental model özeti

- **Ne inşa ediliyor:** manifest.json (+opsiyonel operations.json) girdisinden, iş-mantığı gövdeleri boş
  (marker `doldurulacak`) ama **derlenen** bir Spring Boot 3.5 / Java 21 / Maven uygulaması üreten,
  deterministik **Java-native** üreteç + onun LLM-doldurucu skill'i (base-springboot-rest).
- **Kararlaştırılan çatallar:** (1) Standalone Java-native pipeline — CoreTemplate1'in Gen.Core'una
  bağımlılık YOK, davranış birebir yeniden yazılır; (2) Seam = **Generation Gap** (abstract base + human
  subclass, Java'da partial yok); (3) **Parent-POM** = Generated.props'un Maven karşılığı (gen/parent/pom.xml);
  (4) dbProvider whitelist `postgres|sqlserver|h2|inmemory` — .NET'in sqlite'ı yerine **h2**; (5) ince
  `@RestController` + CQRS handler bean + Spring Data JPA + kapalı (sealed) Result taksonomisi.
- **Hâlâ açık (SPEC §12):** üç çatal (Generation Gap / parent-POM / sqlite→h2) "plan onayında kullanıcıya
  sorulacak" diye işaretli. Planın TAMAMI bu üç çatalın SPEC-önerilen seçeneğini önceden benimsemiş
  (çelişki yok — SPEC §6 zaten normatif olarak bu seçenekleri tarif ediyor; §4'te not).
- **Scope sınırı (Never):** dile-özel manifest varyantı; sessiz construct düşürme; framework icadı;
  LLM-judge; `gen/**`'e insan/LLM yazımı; Repository origin'e onaysız push. **Ask-first:** üretilen app
  şekli, yeni bağımlılık, whitelist genişletme, manifest şemasına alan ekleme.
- **Cross-cutting:** INV-D (determinizm/ordinal/write-if-changed), INV-7 (no-silent-loss, exit 1 ⟺
  ≥1 silentDrop; unsupported exit'i etkilemez; Clean≠exit), INV-S (seam WriteIfAbsent, marker
  `doldurulacak`, gen-tree her run ezilir+prune), INV-4 (tipli predicate; agg/call/duration→Unsupported),
  INV-5 (6'lı sealed Result), INV-9 (standalone join atlar), INV-A3 (assertion SPEC'te, runner'a literal
  gömülmez), gerçek-derleme doğrulaması (`mvn compile` exit 0 + golden). CoreTemplate1 READ-ONLY.

---

## 2. Alignment matrisi (32 task)

| Task | Doc bölümü | Durum | Boyut | Uyumsuzluk özeti |
|---|---|---|---|---|
| T0.1 | SPEC §4 | ✓ | a-i | Multi-module iskelet; sorun yok |
| T0.2 | SPEC §2,§9 | ✓ | a-f | Fixture kopya + sürüm pin; sorun yok |
| T1.1 | SPEC §5; core §3,§8 | ✓ | a-j | ExprNode AST + deserializer asimetrisi birebir |
| T1.2 | SPEC §2,§5; core §1,§2,§4 | ✓ | a-j | Manifest/Contract model + 4↔2 anahtar asimetrisi doğru |
| T1.3 | SPEC §5; core §4,§8 | ✓ | a,f | Loader fatal↔null; 4 negatif test var |
| T2.1 | core §5,§3,§9 | ⚠️ | e,j | GenerationModel.testPlan, T2.2'nin tipini tüketir; §1 grafı T2.1↔T2.2 kenarını göstermiyor (task içi placeholder ile self-mitige — bkz §4.3) |
| T2.2 | core §6 | ✓ | a-j | TestPlan: creator asla seçilmez + Items-sırası + topo-fallback doğru |
| T2.3 | core §7 | ✓ | a-j | Census TAM liste + IdMatches sınır-ayracı + INV-7 gate birebir |
| T2.4 | dotnet §5 | ✓ | a-f | Provenance atomik yazım + tryRead-null doğru |
| T3.1 | SPEC §3,§6.1-6.2; dotnet §2,§5,§8 | ✓ | a-j | WriteAlways/IfAbsent/prune/migrate mekaniği doğru |
| T3.2 | SPEC §6.1,6.2,6.4; dotnet §1,§3,§6 | ✓ | a-i | Parent-POM + HumanShell + Naming; (Application scan detayı T3.6'ya bağlı — bkz §4.1) |
| **T3.3** | SPEC §6.3; dotnet §1 | 🔧 | c,j | **Type-level ext realize eksikti** (fixture `AuditMeta @schema.versioned` silentDrop olurdu) — eklendi |
| T3.4 | SPEC §6.3,6.4; dotnet §1,§7 | ✓ | c,h | Entity/JPA/@Version/sourceOfTruth-navigasyon-yok doğru |
| T3.5 | SPEC §6.5; core §3; dotnet §1,§7 | ✓ | a-j | BigDecimal.compareTo / String.equals / Int primitif render doğru |
| **T3.6** | SPEC §6.2,6.3,6.4; dotnet §1,§2,§3 | ⚠️ | d,h | **Controller kaydı component-scan'e bırakılmış** — SPEC §6.2 "component-scan'e güvenilmez" ile çelişir (bkz §4.1) |
| **T3.7** | SPEC §6.3; dotnet §1,§7 | 🔧 | c,j | **`http-binding` policy eksikti** (fixture `WriteAuditLog @http.endpoint`) + **note çift-realize** (T3.6 ile) — düzeltildi |
| T4.1 | dotnet §1,§7 | ✓ | c | Idempotency+Pagination disposition doğru |
| T4.2 | SPEC §6.2,6.3; dotnet §1 | ✓ | a-h | Consumer CONSUMER modülünde + EventBus alanı koşullu doğru |
| T4.3 | dotnet §1,§2 | ✓ | c,f | Trigger SmartLifecycle stub + seam ezilmez |
| T4.4 | SPEC §6.3,6.2; dotnet §1,§7 | ✓ | c,h | external↔uncharted sahiplik farkı + caller-side validation doğru |
| **T4.5** | SPEC §6.7; dotnet §7,§1 | 🔧 | c | **`http-binding` policy-set assert'inde eksikti** — eklendi (üretici T3.7) |
| **T5.1** | SPEC §6.6,§7; dotnet §4,§6 | 🔧 | d,j | **`db-provider` policy uydurmaydı** — .NET `Realized("dbProvider",v)` (policy DEĞİL) ile hizalandı |
| T6.1 | SPEC §3,§9; dotnet §8; conf §B | ✓ | a-f | Golden (build-report/prov HARİÇ) + 5 characterization doğru |
| T6.2 | conf §B | ✓ | c,f | EmitTests/LatentConstruct/Report portu; grpc/queue→Unsupported |
| T6.3 | SPEC §9 | ✓ | f | E2E compile + studyo smoke `@Tag(e2e)` |
| T7.1 | SPEC §6.3,6.6; dotnet §1; core §6 | ✓ | c,g,h | Fixture harness + ARRANGE-human/ASSERT-owned + test-prereq Unsupported |
| T8.1 | SPEC §8; conf §A.1-3 | ✓ | a-j | Spec DTO + izole classloader (parent-delegation) + inspect doğru |
| T8.2 | SPEC §8; conf §A.2,A.4,A.5 | ✓ | f | Invariant property (seed 20260625/50 tur) + exit 0/1/2 + acceptance |
| T9.1 | SPEC §10,§6.2-6.4; conf §C | ✓ | a-h | capability.json + SKILL.md; seamPath gerçek-app doğrulaması |
| T9.2 | conf §C | ✓ | c,f | 4 referans + evals Java uyarlaması; mekanikler birebir |
| T9.3 | SPEC §10 | ✓ | f | plugin/marketplace/pack-script |
| T10.1 | SPEC §11 (bütün) | ✓ | f | E2E demo + README + SPEC senkron |

Legend: ✓ uyumlu · 🔧 mekanik fix uygulandı · ⚠️ kavramsal (kullanıcı kararı)

---

## 3. Uygulanan mekanik fix'ler (🔧)

Tüm fix'ler CoreTemplate1 birincil-kaynak (READ-ONLY) kanıtına dayanır; tümü **BROADEN** (her biri
tek mantıksal boşluk, 1 asıl task'ı SPEC/parite talebine kadar genişletir). Milestone-LIFT gerekmedi.

### 3.1 T3.3 — Type-level ext realize (kapsam-eksik, j/c)
- **Dosya:** `tasks/T3-3-result-tipler.md` Step 5.4 + test assert.
- **Kanıt:** `DotnetEmitter.cs:741` `ExtComment(t.Ext, t.Id, report)` (type-level ext, owner=typeId);
  `Completeness.cs:52` census `AddExt(x, t.Ext, t.Id)`. Fixture `AuditMeta` (composite) top-level
  `@schema.versioned` taşır.
- **Önce:** `... → report.realized(t.kind(), id).` (type ext'ten hiç söz yok).
- **Sonra:** "**Type-level ext** (`t.ext()` varsa): her ext → prelude yorum + `realized("@{ns}.{name}",
  typeId)` + `policy("{ns}-realization", ...)` ... realize edilmezse census `("@schema.versioned",
  "AuditMeta")` çiftini silentDrop yapar, T4.5 ZeroDropTest kırılır." + test'e `("@schema.versioned",
  "AuditMeta")` assert'i.
- **Gerekçe:** NEITHER T3.3 NOR T4.5 type-LEVEL ext'i realize etmiyordu (T4.5 yalnız "type field").
  Fixture'da canlı → 0-drop hedefi (M4 DoD) sağlanamazdı. T3.4'ün entity-level ext deseniyle simetrik.

### 3.2 T3.7 — `http-binding` policy (kapsam-eksik, j) + note çift-realize (doc-çelişki, j)
- **Dosya:** `tasks/IMPLEMENTATION-PLAN.md` T3.7 (short-format inline).
- **Kanıt (http-binding):** `DotnetEmitter.cs:1160` `report.Policy("http-binding","route/query/header
  detail (generator-policy)")` — `ExtPartial` içinde, op `@http` ns'li ext taşıyorsa. SPEC §6.7 policy
  listesinde `http-binding` var. Fixture `WriteAuditLog @http.endpoint`.
- **Önce:** op-Ext bölümü yalnız `{ns}-realization` policy'sinden bahsediyordu.
- **Sonra:** "**`@http` ns'li op ext varsa AYRICA** `policy("http-binding","route/query/header detail")`
  (.NET DotnetEmitter.cs:1160 paritesi; SPEC §6.7)."
- **Kanıt (note):** T3.6 Step 5.1 zaten `note → Javadoc + realized("note", opId)` yapıyor; `realized()`
  dedup ETMEZ (yalnız census dedup eder) → çift entry.
- **Önce:** "note: Javadoc'a (T3.6'da altyapı var) → `realized("note", opId)`."
- **Sonra:** "note: `realized("note", opId)` **T3.6 Step 5.1'de yapılır**; BURADA TEKRAR EDİLMEZ
  (T4.5'in visibility'yi T3.6'ya bırakması ile aynı desen)." + DoD bulgusu güncellendi.
- **Gerekçe:** http-binding = SPEC §6.7 zorunlu policy'si, hiçbir task üretmiyordu; note = tek realize
  (.NET paritesi) olmalı, iki task çakışıyordu.

### 3.3 T4.5 — `http-binding` policy-set assert'i (test-eksik, c)
- **Dosya:** `tasks/T4-5-disposition-suprmesi.md` Step 5.5 (ZeroDropTest policy listesi).
- **Önce:** policy assert listesi `... trigger-wiring, guard-linkage, ...` (http-binding YOK).
- **Sonra:** `... trigger-wiring, http-binding, guard-linkage, ...` + "http-binding T3.7'de emit edilir"
  notu.
- **Gerekçe:** T4.5 "TÜM policy'leri tamamla/doğrula" görevini üstlenir; SPEC §6.7'nin http-binding'i
  completeness assert'inde eksikti (3.2'nin doğrulama-yüzü; producer T3.7, verifier T4.5).

### 3.4 T5.1 — `db-provider` policy uydurması → `realized("dbProvider", value)` (kapsam-fazla/parite, d/j)
- **Dosya:** `tasks/T5-1-cli.md` Step 5.1.
- **Kanıt:** `DotnetEmitter.cs:1606` `report.Realized("dbProvider", provider)` (whitelist-içi);
  `:1603` `report.Unsupported("dbProvider", provider, ...)` (whitelist-dışı); null → yalnız seam.
  **.NET'te `Policy("db-provider",...)` YOK.** SPEC §6.7 policy listesi de db-provider içermez.
- **Önce:** whitelist-içi → `policy db-provider={value}`; null → `policy db-provider=seam`.
- **Sonra:** whitelist-içi → `realized("dbProvider", value)` (POLICY DEĞİL; dbProvider census'ta
  olmadığından gate'i etkilemez); null → **rapor entry'si YOK**.
- **Gerekçe:** "birebir parite" (SPEC §3/§1) mandası + birincil kaynak; policy adı uydurmaydı,
  faithful davranış `realized`.

---

## 4. Kavramsal uyumsuzluklar (⚠️ — KULLANICI KARARI GEREKİR)

### 4.1 T3.6 — Controller kaydı component-scan'e bırakılmış (mental-model-farkı / doc-çelişki)
- **Doc:** SPEC §6.2: "Bean kaydı gen-owned GeneratedBootstrap/modül wiring `@Configuration`'ında
  `@Bean` ile yapılır (... **component-scan'e güvenilmez — determinizm + izlenebilirlik**)."
  Referans (dotnet §9): "AddGenerated/MapGenerated → `@Configuration` + açık `@Import` zinciri
  (**determinizm/izlenebilirlik için component-scan yerine açık kayıt önerilir**)."
- **Task (T3.6 Step 5.5):** "**Karar:** Endpoint'lerde `@RestController` kullan, Wiring'den @Bean YAZMA;
  **component-scan onları bulur**; Handler bean'leri Wiring @Bean ile... Bu karar SPEC §6.2'nin 'açık
  kayıt' ilkesinin controller'lar için scan-istisnasıdır."
- **Neden kavramsal:** Tek-anlamlı düzeltme yok + gerçek bir Spring gerilimi var:
  `@SpringBootApplication` (Application `app` paketinde) default olarak `app.*`'ı component-scan eder —
  gen/java'daki `@RestController`'lar (ve tüm `app.*` `@Configuration`'ları) taranır. Task bu gerilimi
  "controller'lar için scan, handler'lar için @Bean" diye çözüyor; ama bu SPEC §6.2'nin explicit
  yasağıyla doğrudan çelişiyor. NOT: gen-owned Endpoint'e `@RestController` koymak "human sınıfına
  anotasyon konmaz" kuralını İHLAL ETMEZ (Endpoint gen-owned); ihlal edilen ilke **"scan yerine açık
  kayıt"**.
- **Yorumlar:**
  - **A (öneri):** Tam-açık kayıt — controller'ları da Wiring'de `@Bean` ile kaydet, Application'ın
    `app.*` component-scan'ini bastır (`@SpringBootApplication` yerine `@EnableAutoConfiguration +
    @Import(GeneratedBootstrap)`, ya da scanBasePackages'i boş/dar tut). Spring, `@Bean`-kayıtlı
    controller'ın `@RequestMapping`'lerini yine keşfeder → REST çalışır, INV-D izlenebilirliği korunur,
    SPEC §6.2/ref-§9 ile tam uyum. Fix: T3.6 Step 5.5 + T3.2 Application şablonu.
  - **B:** Task'ın mevcut kararı (controller=scan, handler=@Bean). Daha basit ama §6.2'yi ihlal eder ve
    tüm `app.*` (@Configuration/@Component) taranır — sürpriz-kayıt riski. Seçilirse **SPEC §6.2
    güncellenmeli** (controller-scan istisnası normatif yazılmalı — "önce SPEC güncellenir" kuralı,
    plan §4).
- **Reviewer önerisi:** **A**. Determinizm + izlenebilirlik SPEC'in açık gerekçesi; scan-istisnası
  taşınabilirlik/sürpriz-kayıt açısından zayıf. B seçilirse SPEC senkronize edilmeli (drift bırakılmamalı).

### 4.2 SPEC §12 üç çatalı task'larda önceden kapatılmış (process notu)
- **Doc:** SPEC §12 üç çatalı "plan onayında kullanıcıya sorulacak" diye işaretler (Generation Gap /
  parent-POM / sqlite→h2), her biri için bir "Öneri" verir.
- **Task:** Plan+task'lar üçünü de SPEC-önerilen seçenekle uygular (T3.6/T3.2 Generation Gap, T3.2
  parent-POM, T0.2/T5.1/T9.1 h2). Çelişki yok — SPEC §6 zaten bunları normatif tarif ediyor.
- **Neden kavramsal:** §12 bu üçünü açık-onay kapısı olarak işaretlemiş; plan onayı bu üç seçeneği de
  içeriyor mu, kullanıcı teyidi gerekir (teknik uyumsuzluk değil, onay-kapısı).

### 4.3 T2.1 ↔ T2.2 TestPlan tip-sırası §1 grafında görünmüyor (minör, topological)
- **Doc/Plan:** §1 grafı T1.3'ten sonra T2.1/T2.2/T2.3/T2.4'ü paralel (P1) gösterir, T2.1→T2.2 kenarı YOK.
- **Task:** `GenerationModel` (T2.1) `testPlan:TestPlan` alanı taşır; `TestPlan` tipi T2.2 üretir. T2.1
  bunu bir "geçici boş-record placeholder + T2.2 genişletir" ile self-mitige eder ve raporlamayı ister.
- **Neden kavramsal/minör:** Placeholder yüzünden T2.1 derlenmek için T2.2'ye HARD-bağımlı DEĞİL →
  pre-condition kenarı eklemek YANLIŞ olur. Tek gerçek boşluk: §1 grafının ilişkiyi göstermemesi.
  Öneri: grafa açıklayıcı bir dipnot; PM sıralamada T2.1→T2.2'yi (ID sırası zaten böyle) korusun. Fix
  uygulanmadı (edit-değeri düşük, yanlış-kenar riski yüksek).

---

## 5. Self-check

- [x] SPEC.md + 3 referans eki + plan + 19 tam-format task + 13 short-format task TAMAMEN okundu.
- [x] 31 construct + ext, tek tek bir realize-task'a eşlendi (§1 kapsam matrisi); type-LEVEL ext boşluğu
  yakalandı ve fixture ile doğrulandı (`AuditMeta @schema.versioned`).
- [x] Sembol-Task tablosu VERBATIM kabul edilmedi; task Changes'lerinden yeniden çıkarıldı, pre-cond
  tip-eksik/fazla ve topolojik tutarlılık çapraz-kontrol edildi (T2.1↔T2.2 yakalandı).
- [x] Her mekanik fix CoreTemplate1 birincil kaynağıyla (DotnetEmitter.cs satır kanıtı) doğrulandı —
  ezbere/varsayımla değil; http-binding'in doğru sahibinin T4.5 değil T3.7 olduğu grep ile düzeltildi.
- [x] Kavramsal sorunlar kendi başıma KAPATILMADI; kullanıcı kararına A/B seçenekleriyle sunuldu.
- [x] SPEC.md / docs/referans/*.md DEĞİŞTİRİLMEDİ; yalnız plan + task dosyalarına atomik fix uygulandı;
  CoreTemplate1'e yazılmadı.

---

## 6. Sayısal özet

| Ölçüt | Sayı |
|---|---|
| Toplam task | 32 |
| Uyumlu (✓) | 26 |
| Mekanik fix uygulanan task (🔧) | 4 (T3.3, T3.7, T4.5, T5.1) |
| Uygulanan mekanik fix (distinct) | 5 (type-ext / http-binding-emit / http-binding-assert / dbProvider / note-dedup) |
| Kavramsal açık (⚠️, kullanıcı kararı) | 3 (T3.6 controller-scan · §12 çatal-onayı · T2.1↔T2.2 graf) |
| LIFT (milestone) | 0 |
| BROADEN (1-task genişletme) | 5 |
| Değişen dosya | 4 (IMPLEMENTATION-PLAN.md, T3-3, T4-5, T5-1) |

**Sonuç:** Plan SPEC ile büyük ölçüde hizalı; INV-D/INV-7/INV-S/INV-4/INV-5 çekirdek sözleşmeleri
task'lara doğru dağıtılmış. 5 mekanik parite/kapsam boşluğu (hepsi fixture'da canlı, 0-drop hedefini
etkileyen) düzeltildi. Tek gerçek tasarım-kararı bekleyen konu **T3.6 controller kayıt stratejisi**
(SPEC §6.2 explicit-registration vs component-scan); reviewer açık kayıt (A) öneriyor.

---

## 7. Karar kaydı (rapor-sonrası, kullanıcı — 2026-07-03)

- **§4.1 (controller kaydı):** Kullanıcı **A — tam-açık kayıt** seçti. Yansıtıldı: SPEC §12/4 (karar
  normatif yazıldı) + SPEC §6.1 (Application şablonu `@EnableAutoConfiguration + @Import`) +
  `tasks/T3-2-app-iskeleti.md` Step 5.4 + `tasks/T3-6-operation-slice.md` Step 5.5.
- **§4.2 (SPEC §12 üç çatalı):** Kullanıcı üçünü de onayladı (Generation Gap / parent-POM / sqlite→h2).
  SPEC §12 "KARARA BAĞLANDI" olarak güncellendi.
- **§4.3 (T2.1↔T2.2 graf):** Kullanıcı dipnot seçeneğini onayladı; `tasks/IMPLEMENTATION-PLAN.md`
  §1 grafının altına dipnot eklendi.
- **Plan durumu:** KİLİTLİ — implementasyona hazır (kullanıcı onayı).
