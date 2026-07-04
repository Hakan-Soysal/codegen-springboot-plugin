# T3.2 🔴 — App iskeleti: parent POM + HumanShell'ler + Bootstrap/Wiring + Naming

## 1. Goal
`SpringEmitter.emit(gm, outDir, report, config)` orkestrasyon iskeletini kur ve üretilen app'in
çatısını emit et: üreteç-sahibi `gen/parent/pom.xml`, HumanShell `pom.xml` + `Application.java` +
`application.yml`, `GeneratedBootstrap` + modül `Wiring` config'leri, `Naming` yardımcıları.

## 2. Why
SPEC §6.1-6.2: parent-POM deseni Generated.props'un Maven karşılığı — yanlış kurulursa üretilen app
hiç derlenmez ya da insan pom'u her run ezilir. İki source-root (gen/java + src/main/java) aynı
paketleri paylaşmalı (Generation Gap ön-şartı). Downstream'deki TÜM emisyon task'ları bu iskeletin
üstüne ekler.

## 3. Inputs
- `SPEC.md` §6.1, §6.2, §6.4
- `docs/referans/gen-dotnet-emisyon-sozlesmesi.md` §1 (kök seviye + Bootstrap/Wiring), §3 (Naming), §6 (CLI akışı)
- **Pattern (READ-ONLY):** CoreTemplate1 `src/Gen.Dotnet/DotnetEmitter.cs` 1516-1581 (ProgramShell/
  Csproj/GeneratedProps), 1610-1659 (Bootstrap/Wiring), `Naming.cs` (tam)
- `docs/surumler.md` (T0.2 pinleri: Spring Boot 3.5.x patch, build-helper sürümü)
- T3.1 `EmitWriter`

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q -pl gen-spring test                                     # expected: exit 0 (T3.1 yeşil)
grep -i "spring boot" docs/surumler.md                         # expected: pinli sürüm satırı var
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — Naming
**File:** `gen-spring/src/main/java/techgen/spring/Naming.java`
**Action:** Add — SPEC §6.4 tablosu:
- `javaType(String manifestType, boolean collection)`: ID/String/`*Id`→"String"; Decimal→"BigDecimal";
  Int→"int" (nullable bağlam parametresiyle "Integer"); Bool/Boolean→"boolean"; DateTime→"Instant";
  Date→"LocalDate"; Duration→"Duration"; diğer→passthrough; collection→"List<...>".
- `pascal(s)`, `camel(s)`, `packageOf(module)` (lowercase), `slicePackage(module, opId)` =
  `"app."+lower(module)+"."+lower(opId)`, `constant(s)` (UPPER_SNAKE).
- `httpVerbAnnotation(method)`: POST→"@PostMapping", PUT→"@PutMapping", PATCH→"@PatchMapping",
  DELETE→"@DeleteMapping", default→"@GetMapping". `bindsBody(method)`: POST/PUT/PATCH→true.

### Step 5.2 — SpringEmitter iskeleti
**File:** `gen-spring/src/main/java/techgen/spring/SpringEmitter.java`
**Action:** Add — `emit(GenerationModel gm, Path outDir, BuildReport report, GenConfig config)`:
EmitWriter kur → sıra: kök dosyalar → global gen dosyaları → modül döngüsü (sonraki task'lar bölüm
ekleyecek) → `writer.finishAndPrune()`. Bu task'ta yalnız 5.3-5.6 emisyonları bağlanır.
(GenConfig T5.1'de gelecek — bu task geçici `record GenConfig(String dbProvider, String testDbProvider)`
tipini `techgen.spring`'e ekler; T5.1 yükleyiciyi ekleyecek.)

### Step 5.3 — Üreteç-sahibi parent POM
**File emit:** `gen/parent/pom.xml` (writeAlways)
**Action:** şablon: `<parent>` = `org.springframework.boot:spring-boot-starter-parent:{pin}`;
packaging=pom; `<properties>` java.release=21; `<dependencies>`: spring-boot-starter-web,
spring-boot-starter-data-jpa; provider bağımlılığı config.dbProvider'a göre (h2/inmemory→com.h2database:h2
runtime; postgres→org.postgresql:postgresql; sqlserver→com.microsoft.sqlserver:mssql-jdbc; null→yok +
yorum; whitelist-dışı→yok + `report.unsupported("dbProvider", value, ...)` — T5.1'de policy detayı).
`<build>`: build-helper-maven-plugin `add-source`=`${project.basedir}/../../gen/java` DEĞİL —
**dikkat**: parent `gen/parent/` altında, app kökü iki üst değil; source-root'lar app pom'undan
göreli: build-helper konfigürasyonu parent'ın `<pluginManagement>`'ında tanımlanır, path'ler
`${project.basedir}/gen/java` ve `${project.basedir}/gen/test-java` (child=app kökü çözer);
maven-compiler release=21. Test bağımlılıkları: spring-boot-starter-test (T7.1 kullanacak).

### Step 5.4 — HumanShell'ler (writeIfAbsent)
- `pom.xml`: `<parent><groupId>app.gen</groupId><artifactId>generated-parent</artifactId>
  <version>0.1.0</version><relativePath>gen/parent/pom.xml</relativePath></parent>`;
  artefakt `app:app:0.1.0`; build-helper plugin'i etkinleştirir (parent pluginManagement'tan);
  insan-sahipli yorum başlığı ("bu dosya bir kez üretilir").
- `src/main/java/app/Application.java`: `@EnableAutoConfiguration` + `@Import(GeneratedBootstrap.class)`
  + main. **`@SpringBootApplication` KULLANILMAZ** (SPEC §12/4 tam-açık kayıt: component-scan bilinçli
  kapalı — tüm bean'ler gen-owned Wiring/Bootstrap `@Bean` kayıtlarından gelir). Dosya başına iki
  satırlık açıklayıcı yorum: "component-scan kapalıdır; kendi bean'lerinizi burada @Bean/@Import ile
  kaydedin ya da bilinçli olarak @ComponentScan ekleyin". İnsan-sahipli yorum.
- `src/main/resources/application.yml`: minimal (`spring.application.name: app` + provider'a göre
  datasource yorum bloğu).

### Step 5.5 — GeneratedBootstrap + Wiring
**File emit:** `gen/java/app/GeneratedBootstrap.java` (writeAlways) — `@Configuration` +
`@Import({Billing modül Wiring'leri...})` (modüller name-ordinal). Modül başına
`gen/java/app/{module}/{Module}Wiring.java` — `@Configuration`; op bean metotları sonraki task'larda
eklenecek; bu task'ta boş config + `// op kayıtları: T3.6+` yorumu.
`report.realized("module", name)` her modül için; deployable'lar T4.5'te.

### Step 5.6 — Emit smoke testi
**File:** `gen-spring/src/test/java/techgen/spring/AppSkeletonTest.java`
**Action:** fixture GM → emit → assert: 6 dosya var (parent pom, pom, Application, application.yml,
GeneratedBootstrap, 2 Wiring); pom.xml ikinci emit'te (içeriği elle bozulduktan sonra) EZİLMİYOR;
parent pom EZİLİYOR (writeAlways). `mvn -q -f <tmp>/pom.xml compile` **bu task'ta beklenmez**
(handler'lar yok — derleme T3.6 sonunda anlamlı) ama `mvn -q -f <tmp>/pom.xml validate` exit 0
(POM zinciri geçerli).

## 6. Acceptance tests
### 6.1 `mvn -q -pl gen-spring test` → exit 0.
### 6.2 Pozitif — üretilen `<tmp>/pom.xml` için `mvn -q -f <tmp>/pom.xml validate` exit 0 (gerçek koşum).
### 6.3 Negatif — insan pom'u elle değiştir → ikinci emit → değişiklik DURUYOR (assert).

## 7. Out of scope (DO NOT)
- Entity/type/op/endpoint emisyonu — T3.3-T3.6
- GenConfig yükleme + policy raporlaması — T5.1
- deployable/Host — T4.5

## 8. Anti-patterns
- DO NOT component-scan'e güven — bean kayıtları açık `@Configuration/@Bean/@Import` (SPEC §6.2).
- DO NOT app pom'una bağımlılık listesi koy — bağımlılıklar üreteç-sahibi parent'ta (Generated.props paritesi).
- DO NOT Spring Boot sürümünü şablona hardcode et — `docs/surumler.md` pininden tek sabitle
  (`Versions.SPRING_BOOT`), tek yerde.
- DO NOT `gen/parent/pom.xml`'e insan-düzenleme yorumu koyMAMAK — writeAlways uyarı başlığı şart.

## 9. Definition of Done
- [ ] Naming + SpringEmitter iskeleti + 4 emisyon (parent/shell'ler/bootstrap/wiring) mevcut
- [ ] `mvn validate` üretilen app'te exit 0 (çıktı okundu)
- [ ] Ezmeme/ezme asimetrisi testli
- [ ] 6.1-6.3 koşuldu
- [ ] `git status`: yalnız gen-spring

## 10. Self-check
1. POM parent zincirini gerçek `mvn validate` ile mi doğruladım, XML'e bakarak mı?
2. build-helper path'lerinin app köküne göre çözüldüğünü kanıtladım mı?
3. HumanShell üçlüsünün ÜÇÜNÜN de ezilmediğini test ettim mi?
4. Sürümleri tek sabitten mi okuyorum?
5. Allowlist dışı dosya var mı?
