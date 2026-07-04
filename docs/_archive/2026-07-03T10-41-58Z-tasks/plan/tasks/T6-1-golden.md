# T6.1 🔴 — Golden snapshot + characterization + byte-determinizm

## 1. Goal
Fixture emit ağacının golden snapshot'ını (`relpath\tsha256`) üret/commit'le ve dört characterization
testini kur: golden-eşleşme, write-only-if-changed, prune-keeps-human, HumanShell-hayatta-kalır +
iki-dizin byte-determinizm.

## 2. Why
Bundan sonraki HER emitter değişikliğinin güvenlik ağı. Golden mekanizması yanlış kurulursa (ör.
build-report'u da içerirse) her run kırmızı ya da hep yeşil olur — iki uçta da işlevsiz. Determinizm
INV-D'nin tek falsifiye edilebilir kanıtıdır.

## 3. Inputs
- `SPEC.md` §3 INV-D, §9
- `docs/referans/gen-dotnet-emisyon-sozlesmesi.md` §8 (determinizm + golden mekaniği)
- `docs/referans/conformance-testler-skill-sozlesmesi.md` §B (CharacterizationTests davranışları)
- **Pattern (READ-ONLY):** CoreTemplate1 `tests/Gen.Tests/CharacterizationTests.cs` +
  `golden/emit-snapshot.txt` (biçim örneği)
- T5.1 CLI (tam zincir)

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q test    # expected: exit 0 (T5.1 dahil)
java -jar gen-cli/target/gen-cli.jar fixtures/manifest.json /tmp/tg-golden-pre && echo OK
# expected: OK (exit 0)
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — Snapshot üretici
**File:** `gen-spring/src/test/java/techgen/spring/GoldenSupport.java`
**Action:** emit ağacını gez (**build-report.json ve provenance.json HARİÇ**), her dosya için
`relPath + "\t" + sha256hex + "\n"`, relPath'ler `/` ayraçlı ordinal-sıralı. `UPDATE_GOLDEN=1`
env'i varsa `gen-spring/src/test/resources/golden/emit-snapshot.txt`'e yaz, yoksa karşılaştır.

### Step 5.2 — Dört characterization + determinizm testi
**File:** `gen-spring/src/test/java/techgen/spring/CharacterizationTest.java`
1. `emit_tree_matches_golden_snapshot`: fixture emit → snapshot == golden (diff'i mesajda göster).
2. `unchanged_input_does_not_rewrite_generated_file`: ikinci emit → örnek bir `.java`'nın mtime'ı
   değişmedi (mtime'ı elle geriye çekip doğrula).
3. `removed_operation_prunes_generated_but_keeps_human_logic`: manifest'ten GetInvoice'u çıkar
   (in-memory mutasyon) → ikinci emit → `gen/java/app/billing/getinvoice/` YOK; human
   `src/.../GetInvoiceHandler.java` (ilk emit'te elle işaretlenmiş içerikle) DURUYOR.
4. `human_shell_survives_regeneration`: pom.xml/Application.java/application.yml'i elle boz →
   ikinci emit → bozulmuş halleri DURUYOR; `gen/parent/pom.xml` ise yeniden yazıldı.
5. `generated_tree_is_byte_deterministic`: iki FARKLI temp dizine emit → snapshot string'leri EŞİT.

### Step 5.3 — Golden'ı üret ve commit'le
**Action:** `UPDATE_GOLDEN=1 mvn -q -pl gen-spring test -Dtest=CharacterizationTest#emit_tree_matches_golden_snapshot`
→ üretilen golden'ı gözden geçir (İÇİNDE build-report/provenance YOK; `pom.xml`, `gen/parent/pom.xml`,
`src/main/java/app/Application.java`, 4 op slice'ı, boundary, uncharted, test iskeleti dosyaları VAR —
satır sayısını raporla) → commit.

## 6. Acceptance tests
### 6.1 `mvn -q test` kökten → exit 0 (golden artık aktif).
### 6.2 Pozitif — golden dosyası commit'te; içerik denetimi raporlandı.
### 6.3 Negatif — bir şablona kasıtlı boşluk ekle → golden testi KIRMIZI (diff mesajında dosya adı
görünüyor) → değişikliği geri al → yeşil. (Bu döngü task raporunda kanıtlanır.)

## 7. Out of scope (DO NOT)
- EmitTests/LatentConstruct portu — T6.2
- studyo — T6.3
- Golden'a build-report/provenance ekleme — asla (run-metadata'dır)

## 8. Anti-patterns
- DO NOT golden'ı test çıktısından elle yaz — YALNIZ UPDATE_GOLDEN mekanizmasıyla üret.
- DO NOT mtime testinde `Thread.sleep` ile bekle — mtime'ı `Files.setLastModifiedTime` ile geriye çek
  (flake yasağı).
- DO NOT snapshot'ta `\\` ayraç — `/` normalize.
- DO NOT prune testinde dosya-yokluğunu tek assert yap — human dosyasının İÇERİĞİNİN korunduğunu da assert'le.

## 9. Definition of Done
- [ ] GoldenSupport + 5 test + commit'li golden mevcut
- [ ] Kasıtlı-kırma/geri-alma döngüsü yapıldı ve raporlandı
- [ ] 6.1 koşuldu; golden satır sayısı raporda
- [ ] `git status`: gen-spring test kaynakları + golden

## 10. Self-check
1. Golden'ın build-report/provenance İÇERMEDİĞİNİ dosyayı açıp doğruladım mı?
2. Kasıtlı-kırma testini gerçekten yaptım mı (kırmızıyı gözümle gördüm mü)?
3. Byte-determinizm testi iki AYRI dizin mi kullanıyor (aynı dizine iki emit değil)?
4. mtime testinde flake riski bıraktım mı?
5. Allowlist dışı dosya var mı?
