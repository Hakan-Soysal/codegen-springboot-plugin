# T3.6 🔴 — Operation slice: request record + HandlerBase + human seam + Endpoint

## 1. Goal
Op başına feature-slice emisyonu: `{Op}Command|Query` record, Generation Gap `{Op}HandlerBase`
(abstract), human seam `{Op}Handler` (WriteIfAbsent + marker), ince `{Op}Endpoint` `@RestController`
ve Wiring bean kayıtları. Bu task sonunda üretilen app **`mvn compile` exit 0**.

## 2. Why
SPEC §6.2'nin gerçeklemesi — paketin varlık sebebi olan seam deseni burada vücut bulur. Bean
kayıt zinciri (Wiring→Bootstrap), abstract/human imza eşleşmesi ve endpoint bind kuralları yanlışsa
ya derlenmez ya da context boot etmez. Tüm M4 + doldurucu skill bu yüzeye bağlanır.

## 3. Inputs
- `SPEC.md` §6.2 (seam deseni + marker şablonları), §6.3 ({Op}.g.cs/Endpoint satırları), §6.4
- `docs/referans/gen-dotnet-emisyon-sozlesmesi.md` §1 ({Op}.g.cs/Endpoint/Logic), §2 (marker birebir),
  §3 (HttpVerb/BindsBody)
- **Pattern (READ-ONLY):** CoreTemplate1 `DotnetEmitter.cs` 1466-1513 (OperationFile/LogicFile),
  1663-1685 (EndpointFile)
- T3.3 Result; T3.4 Repository'ler; T3.5 Guards; T3.2 Wiring/Naming

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q -pl gen-spring test    # expected: exit 0 (T3.3, T3.4, T3.5 yeşil)
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — Request record
**File emit:** `gen/java/app/{module}/{op}/{Op}Command.java` (isCommand) | `{Op}Query.java`
**Action:** `public record {Op}Command({params — Naming.javaType})`; visibility yorumu; note →
record üstü Javadoc + `realized("note", opId)`. → `realized("operation", opId)` (HandlerBase ile
birlikte bir kez), `realized("visibility", opId)`.

### Step 5.2 — HandlerBase (Generation Gap, gen-owned)
**File emit:** `gen/java/app/{module}/{op}/{Op}HandlerBase.java`
**Action:** şablon:
```java
public abstract class {Op}HandlerBase {
    protected final {Entity}Repository {entity}Repository;   // op access'inin dokunduğu her entity için
    // (+ sonraki task'ların alanları: EventBus, IdempotencyStore, I{Ext} — kendi task'larında eklenir)
    protected {Op}HandlerBase({ctor param'ları}) { ... }
    public abstract Result<{Ret}> execute({Op}Command request);
}
```
Repository alanları: `access.reads∪creates∪updates∪deletes` entity'leri, ordinal-distinct.
`{Ret}` = Naming.javaType(signature.returns) (pagination T4.1'de `Page<...>`e çevirir).
Guards çağrı-kılavuzu Javadoc'a: kanonik sıra yorumu (idempotency→authz→validation→...).

### Step 5.3 — Human seam
**File emit (writeIfAbsent):** `src/main/java/app/{module}/{op}/{Op}Handler.java`
**Action:** şablon birebir:
```java
public class {Op}Handler extends {Op}HandlerBase {
    public {Op}Handler({ctor param'ları}) { super(...); }
    @Override public Result<{Ret}> execute({Op}Command request) {
        throw new UnsupportedOperationException("{opId}: iş mantığı doldurulacak");
    }
}
```
Öncesinde `migrateSeamIfFlat` (düz eski yol → slice) çağrılır.
**DİKKAT:** ctor imzası HandlerBase ile senkron — sonraki task'lar HandlerBase ctor'una alan
ekledikçe MEVCUT human seam'ler EZİLMEZ (WriteIfAbsent) → bayat-imza build kırığı techgen-sync
davranışıdır (KASITLI, yüksek sesle). Test fixture'ları her emit'te temiz dizine üretir.

### Step 5.4 — Endpoint
**File emit:** `gen/java/app/{module}/{op}/{Op}Endpoint.java`
**Action:** serving'de `protocol=="rest"` olan her arg için: `@RestController` sınıf;
`{httpVerbAnnotation}("{route}")`; POST/PUT/PATCH → `@RequestBody {Op}Command`; GET/DELETE →
route `{param}`'ları `@PathVariable`, kalan record bileşenleri `@RequestParam`; gövde:
`return ResultHttp.toHttp(handler.execute(request));` (GET'te request'i paramlardan kur).
`visibility=="internal"` VEYA rest-serving yok → **controller dosyası emit edilmez** (policy
`visibility` T4.5'te). → `realized("serving", "{op}:rest")`. Non-rest protokoller T4.5.

### Step 5.5 — Wiring bean kayıtları (SPEC §12/4: TAM-AÇIK KAYIT — kullanıcı kararı 2026-07-03)
**File:** T3.2'deki `{Module}Wiring` şablonuna op bölümü ekle:
- `@Bean {Op}Handler {op}Handler({deps}) { return new {Op}Handler({deps}); }` — human sınıfı gen-owned
  config'ten kaydedilir (SPEC §6.2).
- `@Bean {Op}Endpoint {op}Endpoint({Op}Handler h) { return new {Op}Endpoint(h); }` — **controller'lar
  DA açık @Bean ile kaydedilir**. Endpoint sınıfı `@RestController` anotasyonunu TAŞIR (Spring MVC
  mapping keşfi bean'in anotasyonuna bakar, kayıt yöntemine değil) ama scan'le BULUNMAZ: Application
  component-scan yapmaz (T3.2 — `@EnableAutoConfiguration + @Import(GeneratedBootstrap)`). Çift-kayıt
  riski yoktur çünkü scan kapalıdır; tek kayıt kaynağı = Wiring.

### Step 5.6 — E2E derleme testi
**File:** `gen-spring/src/test/java/techgen/spring/GeneratedAppCompileTest.java`
**Action:** `@Tag("e2e")`: fixture emit → `mvn -q -f <tmp>/pom.xml compile` → **exit 0** (gerçek koşum,
ProcessBuilder; çıktı hataysa test mesajına eklenir). + hızlı testler: 4 op için slice dosyaları var;
seam marker'ı `doldurulacak` içeriyor; ikinci emit seam'i ezmiyor; internal op (WriteAuditLog)
controller ÜRETMİYOR.

## 6. Acceptance tests
### 6.1 `mvn -q -pl gen-spring test` → exit 0 (e2e dahil: `mvn -q -pl gen-spring test -Dgroups=e2e` de koşulur).
### 6.2 Pozitif — üretilen app `mvn -q compile` exit 0 (ÇIKTI OKUNDU; ilk gerçek derlenen app).
### 6.3 Negatif — WriteAuditLog (internal) için Endpoint dosyası YOK; CreateInvoice POST'unda
`@RequestBody` var, GetInvoice GET'inde `@PathVariable String id` var (bind asimetrisi).

## 7. Out of scope (DO NOT)
- Idempotency/pagination/trigger/subscription/boundary alanları — T4.x (HandlerBase'e kendi
  task'larında bölüm eklerler)
- Auth/Throws/Consistency sabitleri — T3.7
- Handler GÖVDESİ doldurma — asla (LLM-doldurucu alanı)

## 8. Anti-patterns
- DO NOT human seam'e `@Component` koy — kayıt gen-owned Wiring'de (`@Bean`).
- DO NOT HandlerBase'i interface yap — Generation Gap abstract-class kararı (SPEC §12/1; DI alanları taşır).
- DO NOT endpoint'te iş mantığı — tek satır delegate + toHttp.
- DO NOT seam marker metnini değiştir — `"{opId}: iş mantığı doldurulacak"` birebir (skill tespiti
  substring `doldurulacak`).
- DO NOT compile testini `javac` ile taklit et — GERÇEK `mvn compile` (bağımlılık zinciri kanıtı).

## 9. Definition of Done
- [ ] 4 emisyon + Wiring kayıtları mevcut; kanonik-sıra Javadoc'u HandlerBase'te
- [ ] E2E compile exit 0 — çıktı task raporunda
- [ ] Seam ezilmeme + internal-op-controller-yok + bind asimetrisi testli
- [ ] Controller kayıt kararı (scan istisnası) task raporunda
- [ ] `git status`: yalnız gen-spring

## 10. Self-check
1. `mvn compile`'ı üretilen app'te GERÇEKTEN koştum mu, exit 0'ı gözümle gördüm mü?
2. Seam'in ikinci emit'te ezilmediğini İÇERİK DEĞİŞTİREREK test ettim mi?
3. Marker metni birebir mi (fazla boşluk/nokta yok)?
4. GET bind'ında record'u paramlardan kurma yolunu test ettim mi?
5. Allowlist dışı dosya var mı?
