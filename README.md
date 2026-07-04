# techgen-spring — Tech DSL → Spring Boot kod üreteci + LLM-doldurucu (Java-native)

`manifest.json` (+ opsiyonel `operations.json`) girdisini alan, **derlenen, iş-mantığı gövdeleri
boş (marker'lı) bir Spring Boot uygulaması** üreten deterministik Java-native araç ve onun
**LLM-doldurucu skill'i** (`base-springboot-rest`). techgen-dotnet'in (CoreTemplate1) kardeşi:
aynı girdi sözleşmesi, aynı değişmezler, tam construct paritesi — bkz. `SPEC.md`.

## Kurulum (yerel geliştirme)

| Adım | Komut |
|---|---|
| Gereksinimler | JDK **21**, Maven **3.9+** (pinlenmiş sürümler: `docs/surumler.md`) |
| Kökten derle + test | `mvn -q test` |
| Yalnız derle (jar'ları üret) | `mvn -q package` → `gen-cli/target/gen-cli.jar`, `conformance/target/conformance.jar` (shaded/fat jar, `java -jar` ile SDK'sız koşar) |
| Plugin bundle'larını tazele | `bash scripts/pack-plugin-bundles.sh` (jar'ları `plugins/codegen-spring/skills/base-springboot-rest/{techgen,conformance}/` altına kopyalar) |
| Uçtan-uca demo | `bash scripts/e2e-demo.sh` (aşağıda) |

> JDK 21 sistemde kurulu değilse `JAVA_HOME`'u JDK 21 dağıtımına işaret ettirmeniz gerekir
> (bkz. `docs/surumler.md` — "JDK kurulum durumu"). `scripts/e2e-demo.sh` bu export'u kendi
> içinde taşır, standalone çalışır.

## Kurulum (Claude Code plugin — LLM-doldurucu skill)

| Adım | Komut |
|---|---|
| Marketplace'i ekle | `/plugin marketplace add Hakan-Soysal/codegen-springboot-plugin` |
| Plugin'i kur | `/plugin install codegen-spring@codegen-spring-tools` |
| Güncelle | `/plugin marketplace update codegen-spring-tools` |

Kurulduktan sonra `base-springboot-rest` skill'i kurulu-skill listesinde `[dsl-generator]`
işaretiyle görünür; CommandDSL ailesinin **`kesif`** skill'i onu `describe` modunda çağırıp
`capability.json` descriptor'ını okur ve seam-doldurmayı bu skill'e devreder. (Marketplace adı
**`codegen-spring-tools`**, plugin adı **`codegen-spring`**, repo **`codegen-springboot-plugin`**
— üçü farklı, normaldir.)

## CLI kullanımı (gen-cli)

```bash
java -jar gen-cli/target/gen-cli.jar <manifest.json> <outDir>
```

- Akış: `load → join/GM → config → emit → Completeness.check → build-report.json/provenance.json yaz`.
- `outDir` yanında `<manifest.json>` ile aynı dizinde opsiyonel `gen.config.json` (dile-özel;
  `{"dbProvider":"h2"}` gibi — whitelist: `postgres`/`sqlserver`/`h2`/`inmemory`) okunur.
- Exit sözleşmesi: `0` = temiz (silentDrop yok), `1` = ≥1 sessiz-drop, `2` = kullanım/girdi hatası.
  Konsol çıktısı: `emit → {outDir} (clean=..., constructs=..., silentDrops=...)`.

Üretilen app'i derlemek: `mvn -q -f <outDir>/pom.xml compile`.

### Conformance runner

```bash
java -jar conformance/target/conformance.jar <appClasspath> <specsPath>
```

- `appClasspath` — üretilen app'in derlenmiş sınıfları + bağımlılıkları (`target/classes` +
  `mvn dependency:build-classpath` çıktısı, `:`-ayrık).
- `specsPath` — dil-nötr bir `*.json` spec dosyası veya spec'lerle dolu bir dizin (recursive).
- Çıktı: her spec için `[PASS|FAIL|SKIP] construct/opId: detail` satırı + özet
  (`conformance: N pass, N fail, N skip`). Exit `0` (tüm PASS/SKIP) / `1` (≥1 FAIL) / `2` (usage hatası).

## Uçtan-uca demo (`scripts/e2e-demo.sh`)

Tek script, elle koşulabilecek E2E senaryonun tamamını otomatikleştirir:

1. `mvn -q package` — `gen-cli.jar` + `conformance.jar`'ı taze derler.
2. Temiz bir geçici dizine `fixtures/manifest.json` ile `java -jar gen-cli.jar` çalıştırır.
3. Üretilen `GetInvoice` seam'ini (`GetInvoiceHandler.java`) örnek bir iş mantığıyla doldurur
   (repository'den id ile arar; bulunamazsa `NotProcessable("NotFound", ...)` döner).
4. Üretilen app'i `mvn -q compile` ile gerçekten derler.
5. Basit bir conformance spec'i (`java -jar conformance.jar`) koşar ve `PASS` satırını gösterir.

```bash
bash scripts/e2e-demo.sh
```

Golden dosyalara dokunmaz (yalnız geçici bir çalışma dizininde çalışır); `UPDATE_GOLDEN=1` bu
script için anlamsızdır.

## İçerik

| Yol | Ne |
|---|---|
| `gen-core/` | Dil-nötr pipeline: model (record'lar) + loader + join + GenerationModel + TestPlan + build-report |
| `gen-spring/` | Spring Boot emitter (Generation Gap seam mekaniği + Java predicate renderer) |
| `gen-cli/` | CLI (shaded executable jar): `java -jar gen-cli.jar <manifest.json> <outDir>` |
| `conformance/` | Java conformance runner (shaded executable jar): dil-nötr SPEC'leri üretilmiş app'e karşı koşar |
| `fixtures/` | `manifest.json`/`operations.json` + `studyo.*` (CoreTemplate1'den kopya) + `gen.config` örnekleri |
| `plugins/codegen-spring/skills/base-springboot-rest/` | LLM-doldurucu skill: `SKILL.md` + `capability.json` (descriptor) + `references/` (gap-protocol, archetype-playbooks, gap-registry, verify-loop) + `evals/` + bundle edilen jar'lar |
| `.claude-plugin/marketplace.json` | Plugin marketplace tanımı |
| `scripts/pack-plugin-bundles.sh` | `mvn package` çıktısındaki jar'ları skill bundle'ına kopyalar |
| `scripts/e2e-demo.sh` | Uçtan-uca el senaryosu (yukarıda) |
| `docs/referans/` | CoreTemplate1 davranış sözleşmelerinin (pipeline/emisyon/conformance) çıkarımı — bu SPEC'in ekleri |
| `docs/surumler.md` | Pinlenmiş araç/kütüphane sürümleri + doğrulama kaynakları |
| `SPEC.md` | Tasarım spesifikasyonu (amaç, sabit kararlar, değişmezler, emisyon sözleşmesi) |
| `tasks/` | Implementasyon planı + task dosyaları + task raporları |

## Pair mimarisi (üreteç + doldurucu)

- **Üreteç** (statik, deterministik): `manifest.json`'dan `gen/**`'i byte-deterministik üretir +
  boş insan-seam'leri (`{Op}Handler.java` vb.) `WriteIfAbsent` ile bir kez yazar (marker
  substring'i: `doldurulacak`). Java'da `partial class` olmadığından seam mekaniği **Generation
  Gap**'tir: `gen/java/.../{Op}HandlerBase.java` (abstract, gen-owned, her run ezilir) + insan-sahibi
  `src/main/java/.../{Op}Handler.java` (`extends {Op}HandlerBase`, bir kez üretilir, asla ezilmez).
- **Doldurucu** (LLM, `base-springboot-rest` skill'i): seam'leri arketip-temelli doldurur
  (`gen/**`'e asla yazmaz), `mvn compile` + conformance runner ile doğrular, bilinmeyen gap'te DUR der.
- **Bean kaydı TAM-AÇIK**: tüm üretilen bean'ler (controller'lar dahil) gen-owned
  `GeneratedBootstrap`/`{Module}Wiring` `@Configuration` sınıflarında `@Bean` ile kaydedilir;
  component-scan'e güvenilmez (determinizm + izlenebilirlik — SPEC §12/4).
- **Aile** (`command-dsl`/`kesif`): seçer → devreder → kapıda doğrular (manifest-türevli
  completeness + conformance). Bu paket: üretir → doldurur → verify eder.

## Doğrulama / test stratejisi

- `mvn -q test` — golden snapshot (`gen-spring/src/test/resources/golden/emit-snapshot.txt`,
  `UPDATE_GOLDEN=1` ile bilinçli değişiklikte yenilenir), characterization (write-only-if-changed,
  prune-keeps-human), emit davranışları (gerçek `mvn compile` + 0 silentDrop), ölçek smoke
  (`studyo.manifest.json`, 43 op).
- `mvn compile` içeren testler `@Tag("e2e")` ile işaretlidir (CI'da ayrı aşama).
- Detaylı sözleşmeler: `SPEC.md` (§3 değişmezler, §5-§9 pipeline/emisyon/CLI/conformance/test
  stratejisi).
