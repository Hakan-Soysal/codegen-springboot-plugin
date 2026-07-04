---
name: base-springboot-rest
description: >-
  [dsl-generator] Spring Boot/Java REST üreticisinin (techgen-spring) referans paketinin LLM-doldurucu
  (seam) yarısı. Statik üretilmiş (`gen/**`) bir Spring Boot projesindeki BOŞ insan-seam'lerini
  (`{Op}Handler.java` `execute` gövdesi ve T4-sonrası tek-tip in-place trigger/subscription/boundary/
  test-arrange seam'leri) arketip-bazlı uzman playbook'larla, kanonik sırada, contract'a SADIK biçimde
  doldurur; her seam'i `mvn compile` + conformance oracle + bağımsız adversarial denetim
  (integrity/security/performance) ile doğrular — bağımsız denetimden geçmeyen LLM-seam kalmaz.
  Bağlam-derleme → arketip → gap-gate → doldur → verify → rapor fazlarını yürütür. `describe`
  argümanıyla çağrılırsa YALNIZ kendi `capability.json` descriptor'ını döndürür (keşfin self-describe
  sözleşmesi; üretim YAPMAZ). Şu durumlarda kullan: keşif/aile bu paketi seçip devrettiğinde — "seam
  doldur", "execute gövdesini yaz", "iş mantığını yaz", "statik üretilmiş Spring projesini tamamla",
  "generator çıktısını doldur" — ya da `describe` modunda capability sorgusu geldiğinde. Statik üretimi
  (`gen/**`) bu skill YAPMAZ (generator binary'sinin işi); yalnız insan-seam'i doldurur.
  `[dsl-generator]`
---

# base-springboot-rest — Spring Boot/Java REST projesindeki insan-seam'lerini doldur

Bu skill, eşli generator'ın **statik kattı** (`gen/**`) ürettikten sonra geriye kalan **boş
insan-seam'lerini** (iş mantığı = orkestrasyon gövdesi) doldurur. Statik kat zaten üyeleri (adlı
validation/rule/invariant, hata fabrikaları, idempotency anahtarları, boundary client interface'leri,
kapalı `Result<T>`) emit etti; senin işin bunları **kanonik sırada bağlamak** — halüsinasyon yüzeyi dar
(Generation Gap deseni: `.NET`'in `partial class`'ı Java'da **abstract taban sınıf + human `extends`**
alt sınıfıdır). Tasarım kaynağı: `SPEC.md` §6.2, §10.

> **Bu skill paket-içi mekaniğin LLM yarısıdır.** Aile (`is-analizi → teknik-analiz → kesif`)
> üretmez/doldurmaz; **seçer + devreder + doğrular** (kapı). Sen yalnız kendi sözleşmelerine
> (capability beyanı, gap-protokol uyumu, owned-tree dokunulmazlığı) uyduğun sürece keşif seni seçer
> ve aile kapısı çıktını geçirir.

---

## 0. `describe` MODU — HER ŞEYDEN ÖNCE (self-describe sözleşmesi)

**Bu kapı, fazlardan ÖNCE çalışır.** Çağrı argümanı `describe` ise:

1. **YALNIZCA** `${CLAUDE_SKILL_DIR}/capability.json` dosyasını oku ve içeriğini **birebir** döndür.
2. **DUR.** Başka **hiçbir şey** yapma: statik üretim yok, seam doldurma yok, build yok, faz
   yürütme yok. `describe` üretim modunun yan-kapısı **değildir**.
3. Yalnız **kendi** `${CLAUDE_SKILL_DIR}` dizinini okursun (sanctioned alan); başka paketin/projenin
   dosyasına uzanmazsın.

Bu, keşfin self-describe sözleşmesidir: keşif kurulu-skill listesinde `description`'ında
**`[dsl-generator]`** token'ını taşıyan filler-skill'leri **aday** sayar, sonra her adayı `describe`
modunda çağırıp descriptor'ını (`capability.json`) alır. `describe` çağrısı ASLA kod/dosya üretmez.

> Argüman `describe` **değilse** → aşağıdaki 6-faz doldurma akışını yürüt.

---

## Neyi neden böyle yapıyoruz (özü kavra)

Amaç "field eşleşiyor mu" değil; **"üretilen + doldurulan kod contract'a sadık `mvn compile` ediyor ve
davranışsal kapsanıyor mu"**. Üç değişmez tüm tasarımı dayatır:

1. **`gen/**` paket-sahibi, dokunulmaz; `src/**` insan-sahibi, doldurulan.** Statik kat deterministik
   (id-sıralı, byte-aynı) ve yeniden-üretimde ezilir; seam katı LLM-üretilir.
2. **Determinizm = STATİK kat.** "Aynı girdi → aynı çıktı" yalnız `gen/**` için geçerli. Seam
   deterministik DEĞİL — ama **bir kez doldurulur** (`WriteIfAbsent`, yeniden-roll YOK) ve
   **commit'lenir → o andan sonra DONMUŞ insan-kodu** gibi davranır; yeniden-üretim onu ezmez.
   Tekrar-üretilebilirlik **commit ile** sağlanır, LLM ile değil.
3. **Bilinmeyen boşlukta improvise YASAK → DUR + sor.** "Yorum ≠ STOP."

---

## Altın kurallar (her oturumda geçerli — değişmezler)

- **Kapalı `Result<T>` taksonomisi dışına ASLA çıkma.** `execute` yalnız şu 6 alt-tipten birini
  döndürür (descriptor `handlerSurfaceMap.resultTaxonomy` birebir): `Success`, `NotAuthenticated`,
  `NotAuthorized`, `NotValid`, `NotProcessable`, `ServerError`. Yeni alt-tip icat etme; exception
  fırlatma (handler-seam içinde) — hepsi bu 6 tipten birine map'lenir.
- **`gen/**` ağacına ASLA yazma — SERT YASAK.** `gen/**` generator-sahibidir (descriptor
  `emissionContract.ownedTree`). Tek bir satır bile düzenleme; bir sonraki yeniden-üretim ezer ve
  aile kapısı `provenance` ile dokunulmuşluğu yakalayıp **RED** verir. Yalnız **human-seam**'e yaz:
  `{Op}Handler.java` `execute` gövdesi + T4-sonrası tek-tip in-place boundary/trigger/subscription/
  test-arrange seam'leri (hepsi `descriptor.emissionContract.humanTree` altında).
- **Seam tespiti = BOŞ-marker substring'i** (descriptor-tahrikli, üreteç-nötr). Boş insan-seam'i,
  `descriptor.emissionContract.seamPath` + `emptyStubMarker`'dan bulunur. Marker **tek bir literal
  string'e hardcode DEĞİL** — descriptor'dan gelir; tespit **substring `doldurulacak`** ile yapılır.
  Geçerli marker biçimleri (hepsi `doldurulacak` substring'ini taşır):
  `"{opId}: iş mantığı doldurulacak"`, `"{Ext}.{op}: doldurulacak"`,
  `"{Op}{T}Trigger.start: doldurulacak"`, `"{cls}.handle: doldurulacak"`,
  `// doldurulacak` (test ARRANGE). Bu substring'i taşıyan seam = doldurulacak boş seam; taşımayan =
  dolu (dokunma — v1 bayat-seam fill yapmaz).
- **Bilinmeyen gap → DUR. improvise YASAK.** Contract'ta karşılığı bulunamayan, tanımlı çözümü
  (üreteç-policy / kayıtlı çözüm) olmayan her boşlukta **dur ve kullanıcıya sor** — uydurma, tahmin,
  "makul varsayım" YOK. **"Yorum ≠ STOP":** bir şeyi açıklayıcı yorumla geçiştirmek STOP değildir;
  gerçek STOP = yazımı durdurup gap'i kesin sunmak. Bir alan bağlanamıyorsa ama STOP edilmezse →
  improvisation. Bu yasak.
- **Codebase-grounded KESİN inference → kaydet + devam (dar istisna).** Contract'ta açık olmayan bir
  eşlemeyi (ör. iş-dili "Müşteri" → hangi entity; Customer↔User bağı) mevcut codebase **tek-aday-
  deterministik** çözüyorsa (tam **bir** FK/nav/tip), varsayımı app-kökündeki **`ASSUMPTIONS.md`**'ye
  (insan-tree; `gen/**` DEĞİL) **NE + NEDEN-kanıt** olarak yaz ve DEVAM et. **0 ya da ≥2 aday → yine
  DUR + sor.** Bu "makul varsayım"a kapı DEĞİL — yalnız somut kod-kanıtlı tek-aday geçer. Mekanik +
  format: `references/gap-protocol.md` §2b.
- **Tek-tip in-place seam varsayımı (T4-sonrası).** Trigger / subscription / boundary / test-arrange'i
  "ayrı dosya / farklı mekanik" diye işleme. T4-sonrası HEPSİ aynı desende: gen-owned abstract taban
  sınıf (`{X}Base.java`, WriteAlways) + human `extends` alt sınıf (WriteIfAbsent, marker). Hepsini
  **in-place** doldur.
- **Onaylanmamış/üretilmiş katı kabul etmeden doldurma.** Statik kat eksikse (gen yok) DUR — statik
  üretim senin işin değil (generator binary'si; doldurma akışı üretilmiş `gen/**`'i varsayar).
- **Access/effect yüzeyinin TEK kaynağı = `manifest.json` (tech-resolved).** Bir op'un dokunduğu
  entity kümesi (persist/effect) **yalnız** `manifest.json operations[].access.{reads,creates,updates,
  deletes}`'ten alınır (tech-resolved, geniş — cascade effect'leri içerir). `operations.json` `access`
  (`{reads,writes}`, business intent) effect-surface kaynağı **DEĞİL** — yalnız iş-niyeti/açıklama;
  dardır (cascade rezervleri eksik olabilir). **Mekanik tripwire:** okuduğun `access` nesnesinde
  `writes` anahtarı varsa → `operations.json`'dasın (YANLIŞ kaynak) → `manifest`'in 4-anahtarlı
  access'ine dön.

---

## Başlamadan

- **Descriptor'ı çöz.** `${CLAUDE_SKILL_DIR}/capability.json` → `emissionContract` (seamPath,
  emptyStubMarker, ownedTree, humanTree, handlerSurfaceMap, canonicalOrder), `gapProtocol`, `build`,
  `conformance`. **Tüm yol/marker/sıra değerleri buradan; hardcode etme.**
- **Devretme paketini doğrula:** `manifest.json`/`operations.json` (domain niyeti + linked contract),
  `gen.config.json` (paket-spesifik profil: dbProvider vb., manifest dizininden okunur), `targetDir`,
  üretilmiş `gen/**` + `build-report.json`. Biri yoksa DUR + bildir.

Sonra önce **Faz 0.5 fill-öncesi ön-kapısını** koş; geçerse altı fazı **sırayla** yürüt.

---

## Faz 0.5 — Fill-öncesi tüm-manifest yapılabilirlik ön-kapısı (contract-only)

**Amaç:** Seam doldurmaya başlamadan ÖNCE, manifest'in (`manifest.json`/`operations.json`) **kendi-içinde
yapılabilir** olup olmadığını kavramsal olarak doğrula — "alanlar var mı, doğru mu, ön-şartlar yerinde
mi". Üretilmiş yüzeyi (`gen/**`) ve `build-report.json`'u **OKUMAZ**; yalnız contract'a bakar → defektli
manifest'i, pahalı **fill+verify döngüsü başlamadan** yakalar ve upstream'e (teknik-analiz) yansıtır.

> **Konum dürüstlüğü:** statik üretim (`gen/**`) bu skill çağrılmadan ÖNCE generator binary'since
> (`gen-cli.jar`) yapılır; bu ön-kapı **fill-öncesidir, statik-üretim-öncesi DEĞİL**. Bu, Faz 3 detection
> gate'in (post-gen, seam-başına) **contract-türevli, tüm-manifest, fill-öncesi alt-kümesidir** — onu
> TEKRARLAMAZ, ondan ÖNCE koşar. K1/K2'yi yeniden tanımlamaz; aynı kuralların contract-only
> ön-koşumudur. Mekanik: `references/gap-protocol.md` §0.5.

**Dört contract-only denetim (tümü manifest'ten; geçmeyen → DUR):**
- **P1 — Manifest sağlık:** `meta.hasErrors=false` ∧ `coverage.unrealizedBusinessOps=[]` ∧
  `coverage.uncoveredEntities=[]`. Değilse → manifest güvenilmez/eksik.
- **P2 — Referential integrity (alanlar var mı/doğru mu):** her op-referansı manifest koleksiyonlarında
  **`id` ile** (koleksiyonlar `id`'le anahtarlı, `name` ile DEĞİL) çözülmeli — `realizes`,
  `signature.returns` + `params[].type` (→ `{types[].id}∪{entities[].id}∪skaler-küme}`; skaler-küme
  manifest'in `ref:"scalar"` markörüyle ground-truth), `throws[]` (→ `{errors[].id}`), `emits[]`
  (→ `{events[].id}`), `subscriptions` (→ event-id+op-id), `access.{reads,creates,updates,deletes}`
  (→ `{entities[].id}`), `callEdges`/`externals` referansları. Çözülemeyen referans ("havada"
  tip/hata/event) → defekt.
- **P3 — K2-contract (failable→named-error):** her failable `validation`/`rule`, ihlalinde `throws`
  kataloğunda (`operations[].throws[]` → adlı `errors[]`) bir adlı-hataya eşlenebilmeli. Eşlenemeyen
  (adsız) → defekt.
- **P4 — K1-contract (kaynak 1-3):** her `validation`/`rule`/`invariant` predicate-input alanı,
  request-param / entity-field / boundary-dönüş'ten **birine** bağlanabilmeli. **Kaynak-4
  (`build-report.policy`) üretim-sonrasıdır** → bu ön-kapıda kontrol EDİLMEZ; policy-bağımlı görünen
  alan "policy-bağımlı, Faz 3 post-gen kapıya ertelendi" diye işaretlenir, **sert-GAP sayılmaz**.

**Dispozisyon — manifest defekti upstream'dir, bu skill DÜZELTMEZ:** herhangi bir denetim defekt
çıkarırsa → **DUR**, defekti KESİN sun (hangi denetim · hangi construct · ne eksik/havada), eylem =
**`back-to-teknik-analiz`** (manifest'i üreten upstream'e dön) ya da kullanıcı contract'ı düzeltsin.
improvise/uydurma YASAK. Hepsi PASS → Faz 1'e geç.

**⚠ Anti-pattern — defekti seam-fill'e erteleme:** "Faz 3 zaten K1/K2 koşar, burada geçeyim" deme;
tüm-manifest contract soundness en erken burada doğrulanır (referential integrity **yalnız** buradadır).
**⚠ Anti-pattern — yanlış infeasibility:** kaynak-4 policy-bağımlı alanı bu ön-kapıda "bağlanamıyor"
diye GAP sayma → o post-gen Faz 3'ün işi.

---

## Faz 1 — Bağlam-derleme (deterministik)

**Amaç:** Her seam için sınırlı, kayıtlı bağlam paketi kur (agent'ın "tahmin etmesi"ne bırakma).
**Derle:**
- **`{Op}` prefix → handler taban-sınıf ailesi.** O op'un feature-slice klasöründen
  (`gen/java/app/{module}/{op}/`) üye yüzeyini topla: `{Op}HandlerBase.java` (DI alanları, sabitler),
  `{Op}Guards.java` (`validation0..N`/`rule0..N`), `{Entity}Invariants.java` (`invariant0..N`), hata
  fabrikaları/`THROWABLE_ERRORS`, `REQUIRED_ROLES`/`REQUIRED_SCOPES`/`OWNERSHIP`,
  `IDEMPOTENCY_KEYS`, `PAGINATION_STRATEGY`/`DEFAULT_PAGE_SIZE`, boundary interface (`boundary/
  {Ext}.java`).
- **`realizes` link → niyet.** Seam'in `realizes`'ı üzerinden `operations.json`/`manifest.json`
  iş niyetini bağla (bu op ne yapmalı, hangi construct'ları taşıyor).
- **Access/effect entity kümesi → yalnız `manifest.json`.** Op'un persist/effect yüzeyi =
  `manifest.json operations[].access.{reads,creates,updates,deletes}` (tech-resolved, geniş).
  `operations.json access` (`{reads,writes}`, business) **kaynak değildir** — dar; cascade rezervlerini
  içermeyebilir. Shape tripwire: `writes` anahtarı = `operations.json` = yanlış kaynak. Bu küme Faz 5
  access-coverage kapısının zorunlu-yazma referansıdır.
- **`build-report.json` policy/construct.** `build-report.policies` (consistency-mode, dedup-store,
  saga-orchestration-state…) + `build-report.constructs[].status` → seam'i değiştiren parametrik
  bağlam. Görmezsen "derlenir ama yanlış profil" riski.
- **`gen.config.json`.** dbProvider/testDbProvider alt-detayları.

> Bu paket **deterministik + sınırlıdır** — keyfi dosya gezme değil, descriptor-yönlü sabit küme.

---

## Faz 2 — Arketip sınıflandır

**Amaç:** Seam'i doğru uzman-playbook'a yönlendir. Aşağıdaki Java-tespit kurallarıyla arketipi belirle:

| Arketip | Tespit (Java yüzeyinde) |
|---|---|
| Command | request `*Command` record |
| Command+saga | Boundary çağrısı + hata→ters-sıra compensate (LIFO) gerektiren `// saga:` işaretli akış |
| Idempotent | `{Op}HandlerBase`'te `IDEMPOTENCY_KEYS` sabiti var |
| Query | request `*Query` record, mutasyon yok |
| Query+pagination | `{Op}HandlerBase`'te `PAGINATION_STRATEGY` sabiti var |
| Trigger-inbound | `{Op}{Trigger}TriggerBase` var (`SmartLifecycle` iskeleti) |
| Subscription-consumer | `{Event}To{Op}ConsumerBase` var |
| Boundary-client | `{Ext}Client` (boundary interface implementasyonu) stub |
| Test-arrange | `src/test/java/app/{scope}/{Name}Arrange.java` seam (Arrange marker) |

Arketipler birleşebilir (ör. Command + saga + idempotent). Birden çok arketip imzası varsa
hepsini uygula (kanonik sıra çakışmayı çözer).

> **Playbook İÇERİĞİ burada DEĞİL** — arketip→playbook eşlemesi + few-shot doğru-doldurulmuş
> örnekler `references/archetype-playbooks.md`'dedir. Bu faz yalnız **sınıflandırır**.

---

## Faz 3 — Gap-detection gate (doldurma-ÖNCESİ, deterministik)

**Amaç:** Gövde yazılmadan ÖNCE sonlu bağlanabilirlik denetimi koş — improvisation'ı kapıda kes.
Doldurma-öncesi dört denetim (K1–K4); geçmeyen → **GAP → STOP** (Faz 4'e geçme):

- **K1 — predicate-input bağlanabilirliği:** her `validation{N}`/`rule{N}`/`invariant{N}` input alanı
  request-param / entity-field / boundary-dönüş / `build-report.policy`'ye bağlanmalı.
- **K2 — failable→named-error:** başarısız olabilen her validation/rule, `THROWABLE_ERRORS`
  kataloğunda adlı-hataya eşlenmeli.
- **K3 — dependency çözünürlüğü:** seam'in ihtiyacı her bağımlılık DI'da (`GeneratedBootstrap`/
  `{Module}Wiring` `@Bean`'leri) ya da boundary olmalı.
- **K4 — unsupported:** `build-report.constructs[status=unsupported]` bu op'a değiyorsa → bilinen
  boşluk (bildirilmiş).

> **Gate RUNTIME DETAYLARI burada DEĞİL** — K1–K4'ün mekanik denetim algoritması + çözüm-kademesi
> (üreteç-policy → kayıtlı çözüm → unsupported → DUR+sor) + kayıt içeriği
> `${CLAUDE_SKILL_DIR}/references/gap-protocol.md`'dedir. Bu faz yalnız **kapıyı atfeder + sırasını
> sabitler** (fill-öncesi). Dual-layer: filler K1–K4'ü pakette erken-DUR koşar; aile **yalnız K1/K2'yi**
> ayrı bir kapıda bağımsız yeniden koşar (öz-beyana güvenmez).

---

## Faz 4 — Çözüm kademesi / doldur (in-place seam)

**Amaç:** Gate geçen seam'in gövdesini **kanonik sırada** yaz — yerinde (in-place).
- **Çözüm kademesi:** gap varsa ilk eşleşen otomatik uygulanır (rapora yazılır, sessiz değil):
  (1) üreteç-policy → (2) kayıtlı çözüm → (3) unsupported-bilinen → (3b) codebase-grounded KESİN
  inference → (4) hiçbiri ⇒ DUR+sor. Bu kademenin mekaniği `references/gap-protocol.md`; buraya atıf
  yeter.
- **Codebase-grounded eşleme → `ASSUMPTIONS.md` (rung-3b):** contract'ta açık olmayan bir eşlemeyi
  mevcut codebase **tek-aday-deterministik** çözdüyse, gövdeyi yazmadan önce app-kökündeki
  `ASSUMPTIONS.md`'ye ilgili op başlığı altına **NE + NEDEN-kanıt (`dosya:sembol`) + GÜVEN** maddesini
  yaz, sonra devam et. **0/≥2 aday → DUR+sor** (ledger'a yazma). Format + iki-bant ayrımı:
  `gap-protocol.md` §2b.
- **In-place fill:** arketip playbook'unun kanonik sırasında üyeleri bağla; gövdeyi
  **`{Op}Handler.java`'nın `execute` metodu** human-seam'ine yaz (`WriteIfAbsent` — boş-marker
  substring'i `doldurulacak` taşıyan seam'e bir kez). `gen/**`'e ASLA dokunma. Kanonik sıra
  descriptor'dan (`emissionContract.canonicalOrder`:
  `idempotency→authz→validation→external-input→rule→entity+invariant→persist→emit→return`).
- **Tek-tip:** trigger/subscription/boundary aynı in-place desenle doldurulur (ayrı dosya muamelesi
  yok) — hepsi gen-owned abstract taban sınıf + human `extends` alt sınıf.

### Faz 4 — `test-arrange` doldurma kuralı (ARRANGE-only seam)

`test-arrange` arketipi (`src/test/java/app/{scope}/{Name}Arrange.java` seam) **yalnız ARRANGE
gövdesini** doldurur — diğer seam'lerle aynı in-place mekanik (`WriteIfAbsent` + `doldurulacak`
marker), ama **scope ARRANGE ile sınırlı**:

- **Yalnız `Arrange` sınıfının gövdesini doldur** — temiz-data hazırlığı + ön-gereksinim (prereq)
  payload'ları. Bu seam'in tek işi, test'in çalışması için tutarlı başlangıç state'ini kurmaktır.
- **ASSERT, üretici-sahibi (owned) `.java`'dadır — ASLA dokunma/yazma.** Test'in assertion'ları
  generator tarafından `gen/test-java/app/{scope}/{Name}Test.java` içinde üretilir; LLM bunları
  yazarsa totoloji (kendi ürettiğini kendi doğrular) → anti-circularity ihlali (Altın kural). Seam
  yalnız ARRANGE.
- **Access-coverage kapısı burada da geçerli:** ARRANGE kurduğu state, `manifest.json
  operations[].access` yazma-kümesiyle (write-set) **tutarlı** olmalı — başlangıç state'i, test'in
  kapsadığı op'ların persist/effect yüzeyini karşılar. `operations.json`'un dar access'i kaynak DEĞİL.
- **Ön-gereksinim payload'ları yalnız `Single`:** üretici, çoklu-/sıfır-creator (`Ambiguous`/`Missing`)
  prereq'li test'lere **seam emit etmez** (zaten DUR-marker'ladı) → o test'ler için doldurulacak ARRANGE
  yoktur. Yalnız tek-creator (`Single`) ön-gereksinimlerin payload'ını kur.

---

## Faz 5 — Verify-loop

**Amaç:** Her doldurulan seam'i doğrula — "derlendi" yetmez (build-pass contract-sadakatini
söylemez; reward-hacking riski).
- **Kapı 0 (deterministik, post-fill, build-ÖNCESİ — access-coverage GARANTİSİ):** gövde,
  `manifest.json operations[].access.{creates,updates,deletes}`'teki **HER** entity için bir
  persist/mutate çağrısı içermeli (`entities_persisted(seam) ⊇ manifest-yazma-kümesi`). Eksik →
  **FAIL** (muhtemelen `operations.json`'un dar access'i referans alındı = access-divergence) →
  düzelt, tekrar koş. Kaynak **yalnız manifest**; `operations.json access` DEĞİL. Mekanik:
  `references/verify-loop.md` §0.5.
- **Birincil (zorunlu):** `descriptor.build.command` (`mvn -q -f {targetDir}/pom.xml compile`) →
  exit 0.
- **İkincil (conformance):** deterministik oracle (gerçek execution+assert; LLM-judge ASLA) —
  throws→negatif, invariant→property, validation/rule→sınır, idempotent→replay, saga→
  failure-injection+compensate, pagination→`Page<T>`, roles→403.
- **Kapı 3 (bağımsız adversarial denetim — son kapı, zorunlu):** build + conformance **bildirilmemiş**
  defect-sınıflarını göremez (gen-yüzeyin token koymadığı lost-update, manifest'in bildirmediği
  boundary-validation, state-precondition, idempotency-ordering, doldurulmamış audit-alanı,
  unbounded query). Bu yüzden her doldurulan `{Op}Handler.java` (ve eşleniği), **seam'i YAZMAYAN
  ayrı/temiz bağlamlı** bir denetleyiciden (subagent) geçer — **dosya başına × odak başına**
  (integrity · security · performance), adversarial (kusur ara, mitigation'ın kod-kanıtıyla yokluğunu
  doğrula, etkiyi persisted-state'ten gözle). **Dispozisyon:** seam-fixable bulgu → düzelt + loop'u
  yeniden koş; yapısal (gen/manifest/host) bulgu → seam icat ederek DÜZELTEMEZ → GAP → route + rapor
  (`improvise YASAK`). **Değişmez: bağımsız denetimden geçmeyen LLM-üretilmiş seam kodu KALMAZ.**
- **Retry:** seam başına ≤3 build-fix (build/conformance/Kapı-3-seam-fixable) → sonra fresh-start
  → o da olmazsa gap → DUR+sor. Kapı 3'ün **yapısal** bulgusu retry tüketmez → anında GAP.

> **Verify-loop DETAYLARI burada DEĞİL** — üç-kapılı oracle mekaniği (build + conformance + bağımsız
> adversarial denetim) + retry/fresh-start + halüsinasyon kapısı
> `${CLAUDE_SKILL_DIR}/references/verify-loop.md`'dedir. Bu faz yalnız **atfeder + sırasını sabitler**.
> **build gerekli ama yetersiz; conformance gerekli ama bildirilmemiş defect'i görmez** — Kapı 3
> (bağımsız, adversarial, descriptor `audit`) zorunlu. Kapı 3 LLM-judge DEĞİL (contract-fidelity'yi
> LLM'e sormaz; "defect-sınıfı var/yok" sorar).

---

## Faz 6 — Rapor

**Amaç:** Ne yapıldığını **sessiz olmayan** biçimde bildir.
- Doldurulan seam'ler (arketip + uygulanan kanonik sıra).
- Gate sonuçları (K1–K4: geçti/STOP), uygulanan çözüm-kademesi kararları (üreteç-policy / kayıtlı
  çözüm — her biri açıkça, sessiz değil).
- **`ASSUMPTIONS.md` varsayımları (rung-3b):** codebase-grounded eşlemeler (NE + NEDEN-kanıt) — varsa
  madde madde özetle; insanın denetlemesi için ledger dosyasına işaret et.
- Bilinmeyen gap → DUR olan seam'ler + kullanıcıya sorulan + (tekrar-edilebilirse) kayıt önerisi.
- Verify sonuçları (build exit, conformance oracle, **Kapı 3 bağımsız denetim** — lens başına
  PASS/bulgu).
- **Kapı 3 yapısal GAP'leri (route-edilen):** seam'in icat ederek düzeltemeyeceği, sahibi
  gen/manifest/host olan bulgular — sahibi + nereye route edildiği (techgen binary / teknik-analiz /
  `src/**` host) ile **açıkça** listele; "fix" SAYILMAZ ama sessizce de geçilmez.
- v1 kapsam-dışı ertelenen seam'ler (varsa) — açıkça.

---

## CreateInvoice (Command+saga+idempotent) — 6 fazda izlenebilir akış (PoC)

Referans PoC, fill akışının fazlardan nasıl geçtiğini somutlaştırır:

1. **Bağlam-derleme:** `CreateInvoice` prefix → `CreateInvoiceHandlerBase.java` (`IDEMPOTENCY_KEYS`,
   `REQUIRED_ROLES`, `THROWABLE_ERRORS`, `paymentGateway` DI alanı) + `CreateInvoiceGuards.java`
   (`validation0`, `rule0`) + `InvoiceInvariants.java` (`invariant0`); `realizes CreateInvoice` →
   operations.json niyeti; `build-report.policies`: `saga-orchestration-state`, `dedup-store`.
2. **Arketip:** request `CreateInvoiceCommand` + `IDEMPOTENCY_KEYS` + boundary çağrısı (`paymentGateway.
   charge`) → **Command + saga + idempotent** (üç imza birden).
3. **Gap-gate (K1–K4):** `validation0`/`rule0` input'ları request/entity'ye bağlı mı (K1); başarısız
   rule'lar `THROWABLE_ERRORS`'a adlı-eşli mi (K2); `paymentGateway` DI'da mı (K3); unsupported
   değiyor mu (K4). Geçerse Faz 4.
4. **Doldur (kanonik sıra):** `idempotencyStore` ile `tryBegin` (idempotent başta) → `REQUIRED_ROLES`
   authz → `CreateInvoiceGuards.validation0` → external-input (`paymentGateway.charge`) →
   `CreateInvoiceGuards.rule0` → entity+`InvoiceInvariants.invariant0` → `invoiceRepository.save` →
   saga: dış-çağrı sırası + hata→ters-sıra compensate (LIFO) → `eventBus.publish` →
   `new Success<>(...)`. Gövde **`CreateInvoiceHandler.java`**'nın `execute` metoduna yazılır
   (`doldurulacak` marker'lı), `gen/**` dokunulmaz.
5. **Verify:** `mvn -q -f {targetDir}/pom.xml compile` exit 0 + conformance: idempotent replay, saga
   failure-injection→compensate, throws→negatif, invariant→property.
6. **Rapor:** doldurulan seam + gate/kademe kararları + verify sonuçları.

---

## Referans dosyaları (gerektiğinde oku — İÇERİK ayrı task'larda)

- `${CLAUDE_SKILL_DIR}/capability.json` — descriptor (seamPath/marker/sıra/arketip kuralları).
- `${CLAUDE_SKILL_DIR}/references/gap-protocol.md` — gap-runtime: **§0.5 fill-öncesi tüm-manifest
  feasibility ön-kapısı (contract-only P1–P4)** + K1–K4 detection gate + çözüm-kademesi + DUR/sor/kayıt
  içeriği.
- `${CLAUDE_SKILL_DIR}/references/archetype-playbooks.md` — arketip playbook'ları + few-shot
  doğru-doldurulmuş örnekler (Spring idyomlu: `@Transactional` sınırı, `JpaRepository`,
  `ResponseEntity`).
- `${CLAUDE_SKILL_DIR}/references/verify-loop.md` — üç-kapılı oracle (build + conformance +
  **bağımsız adversarial denetim / Kapı 3**: 3 lens, dosya×odak, detect≠fix dispozisyonu) +
  retry/fresh-start + halüsinasyon kapısı.
- `${CLAUDE_SKILL_DIR}/references/gap-registry.md` — gap-signature kayıt formatı ve merge kuralları.
