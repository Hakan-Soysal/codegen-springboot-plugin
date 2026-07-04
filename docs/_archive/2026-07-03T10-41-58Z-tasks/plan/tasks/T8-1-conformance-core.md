# T8.1 🔴 — Conformance: Spec DTO + SpecRunner + GeneratedApp bootstrap

## 1. Goal
`conformance` modülünün çekirdeğini yaz: SPEC JSON DTO'ları, `GeneratedApp` (izole classloader +
Spring context bootstrap + reflection invoke + Result inspect) ve `SpecRunner` (arrange/act/assert).

## 2. Why
A3 değişmezinin taşıyıcısı: assertion SPEC'te kalmalı, runner'a tek literal gömülmemeli. Classloader/
context bootstrap Java'nın en kırılgan alanı — yanlış kurulursa "çalışmıyor" değil "yanlış sınıfı test
ediyor" üretir (silent-fail). Skill'in Kapı-2'si buna bağlanır.

## 3. Inputs
- `SPEC.md` §8
- `docs/referans/conformance-testler-skill-sozlesmesi.md` §A.1-A.3 (şema + runner akışı + yükleme)
- **Pattern (READ-ONLY):** CoreTemplate1 `conformance-adapter/Spec.cs`, `SpecRunner.cs`,
  `GeneratedApp.cs` (tam)
- Üretilen app yüzeyi: T3.3 Result adları + `code()`; T3.6 `{Op}Handler`/`execute`

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q test    # expected: exit 0 (M3-M5 yeşil)
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — Modül bağımlılıkları
**File:** `conformance/pom.xml`
**Action:** spring-context + spring-beans + Jackson (**spring-boot starter DEĞİL** — hafif çekirdek);
JPA sınıfları app classpath'inden gelir. Sürümler parent dependencyManagement + `docs/surumler.md`.

### Step 5.2 — Spec DTO
**File:** `conformance/src/main/java/techgen/conformance/Spec.java` (+ komşular)
**Action:** `record Spec(String construct, String opId, JsonNode arrange, SpecAct act, SpecAssert assert_)`;
`record SpecAct(String call, JsonNode with)`; `record SpecAssert(String resultType, String code,
String violated, boolean stub, String expected, String source, String field, String op, BigDecimal bound)`.
Parse: case-insensitive, yorum+trailing-comma toleranslı (Jackson JsonReadFeature'ları).
**Her dosya = TAM BİR Spec** (array parser yok).

### Step 5.3 — GeneratedApp
**File:** `conformance/src/main/java/techgen/conformance/GeneratedApp.java`
**Action:**
- `load(String appClasspath)`: `:`-ayrık path listesi → `URLClassLoader` (parent = runner'ın CL'i —
  Spring/Jackson paylaşılır; app sınıfları child'dan). `app.GeneratedBootstrap` sınıfını yükle →
  `AnnotationConfigApplicationContext` (web YOK): H2 datasource property'leri programatik
  (`jdbc:h2:mem:` + benzersiz ad), sonra bootstrap config register + refresh.
- `resolveHandler(String opId)`: context'ten adı `{opId}Handler` olan VE `execute` metodu olan bean.
- `buildRequest(handler, JsonNode with)`: `execute`'un ilk parametre tipi (record) → canonical ctor;
  JSON key'leri (case-insensitive) record bileşen adlarına eşle; dönüşümler: String/BigDecimal/int/
  long/double/boolean/UUID-as-String + fallback Jackson `treeToValue`.
- `act(handler, request)`: `execute(request)` invoke → `Result` döner (senkron — .NET Task'ının aksine;
  sapma SPEC §8'de kayıtlı).
- `inspect(Object result)`: `ResultShape(getClass().getSimpleName(), code varsa code())` — Success/
  NotValid vb. basit adlar.
- `tryGetSuccessFieldDecimal(result, field)`: Success.value() → record accessor/getter `field`
  (case-insensitive) → BigDecimal'e çevir.

### Step 5.4 — SpecRunner
**File:** `conformance/src/main/java/techgen/conformance/SpecRunner.java`
**Action:** akış birebir: stub→SKIPPED; handler resolve; construct=="invariant"→property dalı
(T8.2'de gövde — bu task'ta metot iskeleti + SKIPPED); arrange.kind=="duplicate"→seed çağrısı;
act; `assertAgainstSpec`: resultType null→FAIL("koşulamaz"); simpleName!=resultType→FAIL;
code beklenen ve observed!=code→FAIL; geçti→PASS. Her exception (UnsupportedOperationException
dahil)→FAIL(detay). `SpecResult(status, construct, opId, detail)`.

### Step 5.5 — Testler
**File:** `conformance/src/test/java/techgen/conformance/SpecRunnerTest.java`
- DTO parse: örnek spec JSON'ları (throws/validation/invariant/stub) — alan eşlemesi + tolerans.
- `assertAgainstSpec` birim: eşleşme→PASS; tip-farkı→FAIL(detayda iki ad); code-farkı→FAIL;
  resultType-null→FAIL.
- buildRequest birim: sahte record + JSON → doğru ctor çağrısı (BigDecimal/int/bool dönüşümleri).
- inspect birim: `Success<String>` örneği → ("Success", null); `NotProcessable` → ("NotProcessable",
  code).
(Gerçek app'e karşı acceptance — T8.2.)

## 6. Acceptance tests
### 6.1 `mvn -q -pl conformance test` → exit 0.
### 6.2 Pozitif — DTO/assert/buildRequest/inspect birim testleri yeşil.
### 6.3 Negatif — beklenen-değer-uydurma denetimi: kaynakta grep `"NotProcessable"|"Success"` yalnız
test dosyalarında geçiyor, main kaynakta GEÇMİYOR (A3 — runner'a literal gömülmedi).

## 7. Out of scope (DO NOT)
- Invariant property üreteci + acceptance + CLI — T8.2
- SPEC dosyalarını ÜRETMEK — aile alanı; runner yalnız koşar
- Üretilen app'te değişiklik — gerekiyorsa DUR + PM'e (yüzey sözleşmesi T3.3/T3.6'nındır)

## 8. Anti-patterns
- DO NOT child-first classloader yaz — Spring tipleri iki CL'de yüklenirse `instanceof` kırılır;
  parent-delegation + app-sınıfları-child yeter.
- DO NOT `SpringApplication.run` kullan — hafif `AnnotationConfigApplicationContext` (web'siz, hızlı).
- DO NOT assert'e default beklenti koy (resultType null ise "Success varsay" GİBİ) — null→FAIL sözleşme.
- DO NOT NotImplemented/UnsupportedOperation'ı SKIP say — FAIL (boş seam = geçmez).

## 9. Definition of Done
- [ ] DTO + GeneratedApp + SpecRunner mevcut
- [ ] ≥10 birim test; A3 grep denetimi yapıldı (raporda)
- [ ] 6.1-6.3 koşuldu
- [ ] `git status`: conformance/** + (§5.1 gereği) root `pom.xml` (spring-boot-dependencies BOM
      import / dependencyManagement) + `docs/surumler.md` (sürüm kaydı)

## 10. Self-check
1. A3 grep denetimini gerçekten yaptım mı (main'de beklenen-değer literal'i yok)?
2. Classloader delegasyon yönünü test/koşumla doğruladım mı?
3. Exception→FAIL yolunu UnsupportedOperationException ile test ettim mi?
4. Testleri gerçekten koştum mu?
5. Allowlist dışı dosya var mı?
