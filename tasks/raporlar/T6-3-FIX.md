# T6.3-FIX Raporu — studyo E2E'nin bulduğu 3 SpringEmitter defekti

**Durum:** DONE · **Tarih:** 2026-07-04 · **Attempt:** 1/3

## Bağlam

T6.3 (E2E compile + studyo smoke, worktree `t6-3-e2e`, main'e MERGE EDİLMEDİ) studyo fixture'ında
(`fixtures/studyo.manifest.json`) 3 gerçek, tekrar-üretilebilir SpringEmitter defekti buldu:
`mvn compile` **exit 1** (silentDrops=0 olsa da derlenmiyor). Kaynak:
`tasks/.pm-state/escalations/2026-07-04T-t6-3-studyo-bugs.md`. Bu task o 3 defekti düzeltir.

## Pre-condition doğrulaması (repro)

- `ls $JAVA_HOME/bin/java` OK.
- Kökten `mvn -q test` (fix ÖNCESİ) → **exit 0**, 263 test (mevcut yeşil).
- Studyo emit → `mvn compile` (fix ÖNCESİ) → **exit 1**, gerçek hatalar gözlendi:
  - `cannot find symbol: class Unit` — **59 kez** (HandlerBase/HumanHandler slice dosyaları,
    ör. `AtaEgitmenHandlerBase.java`) + boundary tarafında ayrıca `PushService.java`/
    `PushServiceClient.java` (2 ayrı hata, aynı kök-neden — aşağıda not).
  - `cannot find symbol: class {SessionStatus,AppointmentStatus,MemberStatus,PackageStatus,
    ClassTypeStatus,RequestStatus,RequestType}` — 3'er kez, `gen/java/app/studio/{Appointment,
    MemberProfile,Session}.java` (entity field tipleri; enum'lar `app.shared` paketinde tanımlı).
  - `bad operand types for binary operator '>=': LocalDate, LocalDate` — `PackageInvariants.java`
    (`endsAt >= startsAt`).

Tüm 3 defekt birebir teyit edildi.

## Fix 1 — Cross-package `import app.Unit` eksik

**Kök neden:** `SpringEmitter.customTypeImport(manifestType, gm)` yalnız `gm.entities()`/
`gm.types()`/`gm.events()` id-eşleşmesine bakıyordu; `Unit` manifest'te bir entity/type/event
DEĞİL, built-in sentinel (`unitRecordJava()`, paket `app`). Op-slice paketi (`app.{module}.{op}`)
her zaman kök paketten (`app`) farklı olduğundan (`Result` gibi) `Unit` de HER ZAMAN cross-package
import gerektiriyordu ama hiç eklenmiyordu.

**Fix:** `customTypeImport` başına özel-durum: `"Unit".equals(manifestType)` →
`"import app.Unit;\n"`. Bu, hem `handlerBaseJava` hem `humanHandlerJava`'daki `retImport` hesabını
(ikisi de `customTypeImport(o.signature().returns(), gm)` çağırıyor) TEK NOKTADAN düzeltti.

**Ek bulgu (aynı kök-neden, farklı emisyon yolu):** `boundaryFile`/`boundaryClientJava`
(`app.boundary` paketi) da boundary-op dönüş tipi `Unit` olduğunda import eksikti
(`PushService.java`/`PushServiceClient.java`, studyo'daki `PushService.notify` external op'u).
`boundaryOpImports` da aynı mantıkla genişletildi: dönüş tipi `Unit` ise `import app.Unit;\n`
eklenir. Bu, escalation metninde "HandlerBase/slice" olarak sınırlanan tarifin gerçekte kapsadığı
DAHA GENİŞ bir emisyon-noktası kümesiydi — studyo `mvn compile` exit 0 hedefine ulaşmak için
gerekliydi, aksi halde 2 hata kalırdı (bkz. doğrulama).

## Fix 2 — Cross-package shared-enum import eksik (entity field)

**Kök neden:** `entityFieldImports(fields)` yalnız `Naming.javaType` sonucu `BigDecimal`/`Instant`/
`LocalDate`/`Duration`/`List` olan alanlar için `java.*` import ekliyordu. Bir alan `app.shared`
gibi FARKLI bir modülde tanımlı bir enum/type'a (ör. `SessionStatus`) referans veriyorsa hiç
kontrol edilmiyordu — `customTypeImport` zaten vardı ama entity alanları için ÇAĞRILMIYORDU
(yalnız op param/return tiplerinde kullanılıyordu).

**Fix:**
- `crossModuleTypeImport(manifestType, ownerModule, gm)` eklendi — `customTypeImport` ile aynı
  entity/type/event taraması, EK olarak eşleşen tanımın modülü `ownerModule`'dan FARKLIYSA import
  döner (aynı modül → aynı Java paketi, import gerekmez/gereksiz import eklenmez).
- `entityFieldImports` üç-parametreli overload'a genişletildi
  (`fields, ownerModule, gm`) — mevcut java.* built-in taramasına ek olarak her alan için
  `crossModuleTypeImport` çağrılır, sonuçlar `LinkedHashSet` ile (alan görülme sırası,
  distinct) eklenir.
- `entityJava` artık `gm` parametresi alıyor (çağıran döngüde zaten scope'ta); tek çağıran site
  güncellendi.
- **Geriye-uyumluluk:** `unchartedEntityJava` (`app.uncharted` paketi, GM modül kavramı yok) için
  eski tek-parametreli `entityFieldImports(fields)` overload'u KORUNDU (cross-module tarama
  yapmaz) — bu emisyon yolu bug kapsamında değildi (studyo hatalarında uncharted entity yoktu) ve
  davranışı kasıtlı değiştirilmedi.

## Fix 3 — PackageInvariants LocalDate/DateTime için geçersiz `>=`

**Kök neden:** `JavaPredicateRenderer.binary()` yalnız `Decimal` tipini `compareTo` formuna
alıyordu (`BigDecimal.compareTo`); `Date`(→`LocalDate`)/`DateTime`(→`Instant`) nötr tipleri için
doğal operatör (`>=`/`<=`/`>`/`<`) üretiliyordu — Java'da bu tipler `Comparable` ama operatör
overload'u YOK, derlenmiyor. (.NET'te `DateTime` operatör-aşırıyüklemesiyle `>=` doğal çalışır;
`CoreTemplate1 ExprBuild.cs` bu yüzden özel-durum İÇERMİYOR — bu Java'ya özgü bir tip-boşluğuydu,
davranışsal parite `compareTo` formuyla sağlandı, kod-birebir parite değil.)

**Fix:** `TEMPORAL = Set.of("Date", "DateTime")` sabiti eklendi; `binary()`'de cmp dalında
`decimal` kontrolünün hemen ardından `temporal` kontrolü: `TEMPORAL.contains(left.type()) ||
TEMPORAL.contains(right.type())` → `Decimal` ile AYNI `compareTo` render formu
(`(a.compareTo(b) OP 0)`, `=`→`==` eşlemesi dahil).

## Doğrulama (gerçek koşumlar)

1. **Studyo emit → `mvn compile`:** `constructs=320, silentDrops=0` (değişmedi) →
   **`BUILD SUCCESS`, exit 0** (fix ÖNCESİ exit 1, 3 kök-neden / 164 satır hata idi).
   - `PackageInvariants.java`: `return (endsAt.compareTo(startsAt) >= 0);` — derleniyor.
   - `Session.java`: `import app.shared.SessionStatus;` eklendi — derleniyor (Appointment/
     MemberProfile de aynı şekilde).
   - `AtaEgitmenHandlerBase.java` (+ diğer 20 op slice'ı): `import app.Unit;` eklendi —
     derleniyor.
2. **Invoice emit (`fixtures/manifest.json`) → `mvn compile`:** `constructs=77, silentDrops=0`
   (değişmedi, invoice bu 3 yolu hiç egzersiz etmiyor — Unit-return op yok, cross-module enum
   field yok, LocalDate invariant yok) → **exit 0** (bozulmadı).
3. **Kökten `mvn test`:** **BUILD SUCCESS**, gerçek surefire-reports toplamı: gen-core **106**,
   gen-spring **149** (`CharacterizationTest` dahil **5/5**, `PredicateRenderTest` **9/9**), gen-cli
   **7**, conformance **1** → **toplam 263 test, 0 failure, 0 error, 0 skipped** — regresyon YOK,
   test sayısı T6.2 sonrası tabanla (263) BİREBİR (bu task yeni test eklemedi, yalnız emisyon
   kodu düzeltti).
4. **Golden durumu:** `CharacterizationTest` **5/5 YEŞİL**, `UPDATE_GOLDEN` HİÇ kullanılmadı,
   `emit-snapshot.txt` bu task'ta DOKUNULMADI. Beklenen sonuç gerçekleşti (rule 3(a)): invoice
   fixture 3 buggy yolu hiç tetiklemediği için fix'in invoice emit çıktısına etkisi YOK
   (constructs=77 aynı, golden aynı) — bug'ların M3/M4'te latent kalma nedeni tam olarak buydu.

## Self-check

1. 3 defekt de fix edildi mi + gerçek `mvn compile` ile mi doğrulandı? **Evet** — studyo exit 1 →
   exit 0, hata mesajları (Unit/enum/LocalDate `>=`) tek tek kayboldu (yukarıdaki komut çıktıları).
2. Invoice fixture bozuldu mu? **Hayır** — `mvn compile` exit 0, `constructs=77` değişmedi.
3. Golden sessizce mi değişti? **Hayır** — `UPDATE_GOLDEN` kullanılmadı, `CharacterizationTest`
   fix SONRASI da 5/5 yeşil (golden dosyasına dokunulmadı, diff yok).
4. Kökten `mvn test` regresyon var mı? **Hayır** — 263/263, 0 failure/error/skipped (fix ÖNCESİ de
   263/263 idi — test SAYISI aynı, hepsi geçiyor).
5. gen-core'a dokunuldu mu? **Hayır** — yalnız `gen-spring/src/main/java/techgen/spring/{SpringEmitter,
   JavaPredicateRenderer}.java`.
6. 3 defekt dışında başka construct'a dokunuldu mu? **Kısmen genişletildi, gerekçeli:** Fix 1
   escalation'da "HandlerBase/slice" diye tarif edilmişti; gerçek repro `boundaryFile`/
   `boundaryClientJava`'da (`PushService`/`PushServiceClient`, external op Unit dönüşü) 2 EK hata
   gösterdi — aynı kök-neden (Unit cross-package import), farklı emisyon noktası. Bu genişletme
   yapılmadan studyo `mvn compile` exit 0 hedefine ULAŞILAMAZDI (DoD zorunlu tutuyor); fix minimal
   ve aynı desen (`customTypeImport`'a paralel bir `if` bloğu). Fix 2'de `unchartedEntityJava`
   çağrı-site'i (aynı fonksiyon adı, farklı imza) BOZULMAMASI için eski davranış overload olarak
   korundu — bu da minimal-değişiklik ilkesine uygun.
7. .NET paritesine uyuyor mu? **Fix 1/2 birebir** (aynı `customTypeImport` desenine paralel).
   **Fix 3 davranışsal parite** (kod-birebir DEĞİL) — C#'ın `DateTime` operatör-aşırıyüklemesi
   Java'da yok; `compareTo` formu `Decimal` dalıyla AYNI mekanizma, aynı karşılaştırma semantiği
   (gerekçe yukarıda, kod içi Javadoc'ta da not edildi).

## Notlar (verifier için)

- **3 bug fix özeti:** (1) `SpringEmitter.customTypeImport` + `boundaryOpImports` → `Unit` özel-
  durumu (`import app.Unit;`); (2) `SpringEmitter.entityFieldImports` (3-param overload) +
  yeni `crossModuleTypeImport` helper → entity alanları için modül-duyarlı cross-package import;
  (3) `JavaPredicateRenderer.binary` → `TEMPORAL` set (`Date`/`DateTime`) `compareTo` formu.
- **Studyo compile:** fix ÖNCESİ exit 1 (164 satır hata / 3 kök-neden) → fix SONRASI **exit 0**.
- **Invoice compile:** fix ÖNCESİ/SONRASI **exit 0**, `constructs=77` DEĞİŞMEDİ — bozulmadı.
- **Golden:** DEĞİŞMEDİ (`golden_changed=false`) — invoice bu 3 yolu egzersiz etmediği için
  beklenen sonuç (rule 3(a)); `UPDATE_GOLDEN` kullanılmadı, gerekçe gerekmiyor.
- **Gerçek test sayısı:** kökten `mvn test` → **263 test, 0 failure, 0 error, 0 skipped**
  (gen-core 106 + gen-spring 149 + gen-cli 7 + conformance 1) — T6.2 sonrası taban ile BİREBİR
  (bu task test eklemedi, yalnız emisyon-kodu düzeltti).
- **Kapsam-dışı gözlem:** `git status --porcelain` çalışma ağacında `tasks/.pm-state/{RESUME.md,
  status.json,escalations/,subagent-prompts/,subagent-reports/,verifier-reports/}` ve
  `gen-cli/dependency-reduced-pom.xml` görünüyor — bunların HİÇBİRİ bu executor'ın tool-call'larıyla
  oluşturulmadı/değiştirilmedi (paralel PM süreci / build-byproduct); commit'e STAGE EDİLMEDİ.
  `.claude/settings.local.json` da stage edilmedi (standing rule).
