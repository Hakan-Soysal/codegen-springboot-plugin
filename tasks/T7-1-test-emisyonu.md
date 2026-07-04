# T7.1 🔴 — TestPlan→JUnit emisyonu: Fixture harness + skeleton + ARRANGE seam

## 1. Goal
TestPlan'dan üretilen-app test iskeletini emit et: `gen/test-java/app/Fixture.java` harness,
test başına `gen/test-java/app/{scope}/{Name}Test.java` owned iskelet + `src/test/java/app/{scope}/
{Name}Arrange.java` WriteIfAbsent ARRANGE seam; Single-dışı prereq'li testlere iskelet YOK →
`Unsupported("test-prereq", ...)`.

## 2. Why
Anti-circularity kuralının (ASSERT owned, ARRANGE human) emisyon tarafı. Ambiguous/Missing prereq'te
iskelet EMİT ETMEME kararı sessiz atlanırsa doldurucu skill'in test-arrange akışı (eval #9) bozulur.
3-faz iskeletin assert'i WriteSet'ten türetilir — contract-türevlilik (A3'ün test-yüzü).

## 3. Inputs
- `SPEC.md` §6.3 (tests satırları), §6.6 (testDbProvider)
- `docs/referans/gen-dotnet-emisyon-sozlesmesi.md` §1 (tests/ bölümü: Fixture/TestSkeleton/
  TestArrangeSeam + Unsupported("test-prereq") koşulu)
- `docs/referans/gen-core-davranis-sozlesmesi.md` §6 (TestPlan IR)
- **Pattern (READ-ONLY):** CoreTemplate1 `DotnetEmitter.cs` 201-250 (EmitTests), 276-345
  (TestSkeleton/TestArrangeSeam), 377-499 (TestProvider/Fixture)
- T2.2 TestPlan; T3.6 slice; T5.1 testDbProvider

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q test    # expected: exit 0 (T5.1 yeşil)
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — Fixture harness
**File emit:** `gen/test-java/app/Fixture.java` (writeAlways)
**Action:** Spring context bootstrap'ı (GeneratedBootstrap + PersistenceConfig import; web YOK) +
H2 in-memory override (db adı **runtime'da** benzersiz — emit metni deterministik:
`"jdbc:h2:mem:" + java.util.UUID.randomUUID()`); yardımcılar: `<T> Result<T> run(Class<? extends
{handler-üst-tipi}> h, Object request)` (reflection execute), `<E> E get(Class<E> entity, String id)`
(repository/EntityManager), `reset()`. testDbProvider!=inmemory ise env `TEST_DB` bağlantısı
(postgres/sqlserver dalları — .NET paritesi).

### Step 5.2 — Test iskeleti (owned)
**File emit:** `gen/test-java/app/{scope}/{Name}Test.java` — scope: `process` → Name=processId;
orphan'lar `orphanflow_{id}`/`orphanop_{id}` (paket adları lowercase; sınıf adı Pascal).
JUnit 5; 3-faz:
```java
// ARRANGE (human): {Name}Arrange.arrange{Op}() çağrıları — prereq sırasıyla (SINGLE creator'lar)
// ACT (owned): runSequence sırasıyla fixture.run({Op}Handler.class, arrange.request{i}())
// ASSERT (owned): her run sonucu Success; WriteSet'teki her entity için fixture.get(...) != null
```
Abstract bağ: iskelet `{Name}Arrange` sınıfının metotlarını çağırır (tip referansı derleme bağı kurar).
→ all-Single olarak emit edilen her test `report.realized("test", "{censusScope}_{testId}")` çağırır —
`test` construct'ı census'a SAYILIR (parite: .NET DotnetEmitter.cs:235 de `report.Realized("test",
"{scope}_{name}")` ile test'i census'a sayar). Single-dışı prereq'li testte iskelet EMİT EDİLMEZ →
census'a girmez, yalnız `Unsupported("test-prereq", ...)` yazılır (§5.4).

### Step 5.3 — ARRANGE seam (human)
**File emit (writeIfAbsent):** `src/test/java/app/{scope}/{Name}Arrange.java` — her SINGLE prereq +
her runSequence adımı için `public {Op}Command request{i}() { return null; /* Arrange {op}:
doldurulacak — tutarlı başlangıç payload'u kur */ }` biçimli metotlar (marker substring korunur).

### Step 5.4 — Single-dışı prereq → iskelet YOK
**Action:** testin prerequisites'inde AMBIGUOUS ya da MISSING varsa: test dosyaları EMİT EDİLMEZ;
`report.unsupported("test-prereq", "{name}: {entity} creator={ambiguous|missing}", ...)`.

### Step 5.5 — Testler
**File:** `gen-spring/src/test/java/techgen/spring/TestEmissionTest.java`
- fixture (process/flow YOK) → orphan-op testleri: 4 op için `orphanop_*` iskeletleri + Arrange seam'leri.
- `mvn -q -f <tmp>/pom.xml test-compile` exit 0 (@Tag e2e) — iskelet+seam derleniyor (gövde null-dönen
  stub'larla derleme yeterli; koşum doldurma-sonrası iş).
- Arrange seam ikinci emit'te ezilmiyor.
- Sentetik contract (iki-creator'lı entity içeren process) → o test için dosya YOK +
  ("test-prereq", ...) unsupported entry.
- studyo çifti → processTests>0 iskeletleri üretiliyor (sayı raporlanır); test-compile studyo için
  KOŞULMAZ (süre) — task raporunda not.

## 6. Acceptance tests
### 6.1 `mvn -q test` → exit 0.
### 6.2 Pozitif — üretilen app'te `mvn -q test-compile` exit 0 (çıktı okundu).
### 6.3 Negatif — Ambiguous senaryosunda dosya-yok + unsupported-entry assert'leri.

## 7. Out of scope (DO NOT)
- ARRANGE gövdelerinin DOLDURULMASI — skill alanı (eval #9)
- Assert zenginleştirme (alan-düzeyi beklentiler) — v1 varlık-assert'i (WriteSet ⊆ persisted)
- Conformance SPEC üretimi — aile/conformance alanı, üreteç değil

## 8. Anti-patterns
- DO NOT ASSERT'i Arrange sınıfına taşı — anti-circularity: ASSERT owned iskelette kalır.
- DO NOT Ambiguous'ta "ilk creator'la üret" — iskelet YOK + rapor (T2.2 kuralının emisyon yüzü).
- DO NOT UUID'yi emit-time üret — golden bozulur; runtime-ifade olarak yaz.
- DO NOT `@SpringBootTest` kullan — Fixture kendi hafif context bootstrap'ını kurar (hız + web'siz).

## 9. Definition of Done
- [ ] Fixture + iskelet + seam + Unsupported yolu mevcut
- [ ] test-compile E2E yeşil (çıktı raporda)
- [ ] Ezilmeme + Ambiguous-yok kanıtları testli
- [ ] Golden güncellendi (yeni dosyalar) — diff gerekçesi raporda
- [ ] `git status`: yalnız gen-spring (+golden)

## 10. Self-check
1. ASSERT'in owned dosyada, ARRANGE'ın human dosyada olduğunu içerik-assert'iyle kanıtladım mı?
2. test-compile'ı gerçekten koştum mu?
3. Unsupported("test-prereq") entry biçimi .NET'inkiyle uyumlu mu?
4. Golden diff'ini bilinçli mi güncelledim (gerekçe yazdım mı)?
5. Allowlist dışı dosya var mı?
