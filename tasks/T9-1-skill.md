# T9.1 🔴 — base-springboot-rest: capability.json + SKILL.md

## 1. Goal
Skill paketinin çekirdeğini yaz: aile-nötr `capability.json` descriptor'ı (Java yüzeyiyle) ve
[dsl-generator] işaretli, describe-modu + 6-faz akışlı `SKILL.md`.

## 2. Why
Skill, CommandDSL kesif'inin self-describe keşfiyle bulunur — description token'ı, capability şeması
ve emissionContract alanları SÖZLEŞMEDİR; yanlış seamPath/marker/canonicalOrder doldurucuyu yanlış
dosyalara yönlendirir (geri dönüşü zor). .NET kardeşiyle YAPISAL eşdeğerlik şart.

## 3. Inputs
- `SPEC.md` §10 (paket içeriği), §6.2-6.4 (seam/marker/naming — capability'ye girecek gerçekler)
- **Pattern (READ-ONLY, tam oku):** CoreTemplate1
  `plugins/codegen/skills/base-dotnet-rest/capability.json` ve `SKILL.md`
- `docs/referans/conformance-testler-skill-sozlesmesi.md` §C (fazların mekanikleri)
- T3.6/T4.x çıktıları: GERÇEK seam path'leri ve üye adları (emin olmak için üretilmiş bir örnek app'e bak)

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q test    # expected: exit 0 (M6 yeşil — davranış kilitli)
java -jar gen-cli/target/gen-cli.jar fixtures/manifest.json /tmp/tg-skill-ref && \
  ls /tmp/tg-skill-ref/src/main/java/app/billing/createinvoice/CreateInvoiceHandler.java
# expected: dosya var (seamPath gerçeği)
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — capability.json
**File:** `plugins/codegen-spring/skills/base-springboot-rest/capability.json`
**Action:** .NET descriptor'ının alan-alan Java karşılığı:
- `id: "techgen-spring"`, `version: "0.1.0"`, source (marketplace adları T9.3 ile senkron).
- `invocation`: `{"type":"cli","entry":"java -jar ${CLAUDE_SKILL_DIR}/techgen/gen-cli.jar",
  "args":["manifest","targetDir"]}` (config manifest dizininden okunur — .NET'ten arg farkı raporda).
- `capability`: languages `["java"]`; architectures `["modular-monolith","vertical-slice"]`;
  persistence `["postgres","sqlserver","h2","inmemory"]`; transports `["rest"]`;
  constructsCovered = .NET'teki 31'lik liste BİREBİR.
- `emissionContract`:
  - seamPath: `["src/main/java/app/{module}/{op}/{Op}Handler.java",
    "src/main/java/app/boundary/{Ext}Client.java",
    "src/main/java/app/{module}/{op}/{Op}{Trigger}Trigger.java",
    "src/main/java/app/{consumerModule}/{consumerOp}/{Event}To{Op}Consumer.java",
    "src/test/java/app/{scope}/{Name}Arrange.java"]` — ÜRETİLMİŞ örnekle doğrula.
  - `emptyStubMarker: "doldurulacak"`; `ownedTree: "gen/**"`;
    humanTree: `["src/**","pom.xml"]`.
  - handlerSurfaceMap (Java adlarıyla): operation `"{Op}Handler.execute(request) : Result<T>"`;
    validation `"{Op}Guards.validation{N}"`; rule `"{Op}Guards.rule{N}"`; invariant
    `"{Entity}Invariants.invariant{N}"`; throwsFactory/throwsCatalog `{Op}HandlerBase` üyeleri;
    auth `["REQUIRED_ROLES","REQUIRED_SCOPES","OWNERSHIP"]`; idempotent `"IDEMPOTENCY_KEYS"`;
    boundary `"{Ext}Client.{op}(...)"`; boundaryValidation `"{Ext}{Op}Validation.validation{N}"`;
    subscription `"{Event}To{Op}Consumer.handle(event)"`; trigger `"{Op}{T}Trigger.start()"`;
    pagination `["PAGINATION_STRATEGY","DEFAULT_PAGE_SIZE"]`; consistency
    `["CONSISTENCY_RISK","CONSISTENCY_MODE"]`; resultTaxonomy 6'lı liste birebir.
  - canonicalOrder: 9 adım birebir (idempotency→authz→validation→external-input→rule→
    entity+invariant→persist→emit→return).
  - buildReportSchema birebir.
- `gapProtocol: {"compliant": true}`.
- `build`: `{"command":"mvn -q -f {targetDir}/pom.xml compile","success":"exit 0"}`.
- `conformance`: `{"adapter":"${CLAUDE_SKILL_DIR}/conformance","run":"java -jar
  ${CLAUDE_SKILL_DIR}/conformance/conformance.jar {appClasspath} {specsPath}"}`.
- `audit` bölümü .NET'ten birebir (üç lens, bağımsızlık, dispozisyon, pass tanımı).

### Step 5.2 — SKILL.md
**File:** `plugins/codegen-spring/skills/base-springboot-rest/SKILL.md`
**Action:** .NET SKILL.md'nin yapısı birebir, içerik Java'ya uyarlanmış:
- Frontmatter description: `[dsl-generator]` token'ı BAŞTA ve SONDA; "Spring Boot/Java REST
  üreticisinin (techgen-spring) LLM-doldurucu yarısı"; describe-modu cümlesi; tetik ifadeleri
  (".. seam doldur, execute gövdesini yaz, statik üretilmiş Spring projesini tamamla ..").
- §0 describe modu (birebir sözleşme: yalnız capability.json döndür, DUR).
- "Neyi neden böyle yapıyoruz" + Altın kurallar: gen/** yazma yasağı; marker-substring tespiti;
  bilinmeyen gap DUR ("Yorum ≠ STOP"); codebase-grounded tek-aday istisnası (ASSUMPTIONS.md);
  tek-tip seam (Generation Gap: abstract base + human subclass — partial YOK, extends VAR);
  access/effect tek kaynağı manifest.json 4-anahtarlı access (writes-tripwire dahil).
- Faz 0.5 (P1-P4 contract-only) + Faz 1-6 (bağlam-derleme → arketip → K1-K4 gap-gate → doldur →
  verify-loop → rapor) — mekanik detaylar references/'a atıflı (T5.2/T5.3/T5.5 etiketleri korunur).
- Arketip tablosu Java tespitiyle: Command=`*Command` record; Idempotent=`IDEMPOTENCY_KEYS` sabiti
  var; Query+pagination=`PAGINATION_STRATEGY`; Trigger=`{Op}{T}TriggerBase`; Subscription=
  `...ConsumerBase`; Boundary=`{Ext}Client`; Test-arrange=`src/test/java/**/*Arrange.java`.
- CreateInvoice 6-faz izlenebilir akış örneği (Java yüzeyiyle: `tryBegin` → REQUIRED_ROLES →
  `CreateInvoiceGuards.validation0` → `paymentGateway.charge` → `rule0` → entity+invariant →
  `invoiceRepository.save` → compensate/LIFO → `eventBus.publish` → `new Success<>(...)`).
- Referans dosya listesi (T9.2'nin üreteceği 4 dosya + capability).

### Step 5.3 — Tutarlılık denetimi (mekanik)
**Action:** küçük script/test: capability.json'daki seamPath'lerin ve marker'ın üretilmiş örnek app'te
GERÇEKTEN var olduğunu doğrula (`/tmp/tg-skill-ref` üzerinde glob+grep); canonicalOrder/resultTaxonomy
string'lerinin SKILL.md ile birebir aynı olduğunu grep'le.

## 6. Acceptance tests
### 6.1 `python3 -m json.tool plugins/codegen-spring/skills/base-springboot-rest/capability.json` → exit 0.
### 6.2 Pozitif — Step 5.3 denetimi geçti (çıktı raporda: her seamPath için eşleşen gerçek dosya).
### 6.3 Negatif — SKILL.md'de `.g.cs`/`partial class`/`dotnet build` YOK (grep boş; karşılaştırma
cümleleri hariç tutulacaksa gerekçesiyle raporla).

## 7. Out of scope (DO NOT)
- references/ + evals — T9.2
- plugin.json/marketplace/pack — T9.3
- Üreteç davranışını skill'e uydurmak için DEĞİŞTİRMEK — tersi yön (skill gerçeği belgeler);
  uyumsuzluk bulursan DUR + PM'e

## 8. Anti-patterns
- DO NOT capability alan adlarını yeniden adlandır — kesif şeması .NET descriptor'ıyla ortak
  (seamPath/emptyStubMarker/ownedTree/handlerSurfaceMap/canonicalOrder).
- DO NOT marker'ı Java'ya "çevir" — `doldurulacak` substring'i dil-bağımsız sözleşme.
- DO NOT SKILL.md'ye üreteç-implementasyon detayı göm — doldurucunun görmesi gereken YÜZEY
  (descriptor-tahrikli kalsın).
- DO NOT describe-modunu atlayıp fazlara gir — §0 kapısı fazlardan ÖNCE.

## 9. Definition of Done
- [ ] capability.json + SKILL.md mevcut, JSON valid
- [ ] SeamPath/marker gerçek-app doğrulaması yapıldı (kanıt raporda)
- [ ] [dsl-generator] token'ı description'da; describe sözleşmesi §0'da
- [ ] canonicalOrder/resultTaxonomy capability↔SKILL.md tutarlı (grep)
- [ ] `git status`: yalnız plugins/

## 10. Self-check
1. Her seamPath'i üretilmiş app'te tek tek doğruladım mı (varsayım yok)?
2. .NET capability'siyle alan-alan diff yaptım mı — eksik alan kaldı mı?
3. handlerSurfaceMap üye adları T3.x'in GERÇEK ürettiği adlarla mı eşleşiyor?
4. describe-modu metni "yalnız capability döndür + DUR" diyor mu?
5. Allowlist dışı dosya var mı?
