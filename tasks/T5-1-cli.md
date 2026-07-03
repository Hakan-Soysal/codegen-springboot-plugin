# T5.1 🔴 — GenConfig + CLI main + exit-code semantiği + shaded jar

## 1. Goal
`GenConfig` yükleyicisini (dbProvider etkileriyle), `techgen.cli.Main` uçtan-uca akışını
(load→join→emit→gate→raporlar→exit) ve maven-shade ile çalıştırılabilir `gen-cli.jar`'ı bitir.

## 2. Why
INV-7 exit sözleşmesi (`exit 1 ⟺ ≥1 silentDrop`; unsupported→exit 0) burada dışa açılır — skill'in
build/verify zinciri ve tüm E2E testler bu ikiliğe bağlıdır. dbProvider whitelist'inin parent-POM +
seam etkileri config-yanlışında sessiz kırılma üretebilir.

## 3. Inputs
- `SPEC.md` §6.6 (GenConfig), §7 (CLI)
- `docs/referans/gen-dotnet-emisyon-sozlesmesi.md` §4 (GenConfig etkileri), §6 (CLI akışı + konsol biçimi)
- **Pattern (READ-ONLY):** CoreTemplate1 `src/Gen.Cli/Program.cs` (tam), `src/Gen.Dotnet/GenConfig.cs`
- T3.2 (geçici GenConfig record'ı + parent POM provider dalları), T2.3 (gate), T3.1 (writer)

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q test    # expected: exit 0 (M3+M4 yeşil — kökten)
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — GenConfig yükleme
**File:** `gen-spring/src/main/java/techgen/spring/GenConfig.java` (T3.2 geçici record'ı genişletilir)
**Action:** `static GenConfig load(Path genConfigJson)`: dosya yoksa `new GenConfig(null, null)`;
varsa Jackson parse (camelCase, bilinmeyen-alan toleranslı). Whitelist sabiti:
`postgres|sqlserver|h2|inmemory`. Emitter etkileri (T3.2'de dallar hazır; burada bağla):
- whitelist içi → parent POM driver bağımlılığı + application.yml datasource yorumu doldurulur;
  `report.realized("dbProvider", value)` (.NET `DotnetEmitter.cs:1606` paritesi — **POLICY DEĞİL** realized;
  `dbProvider` census construct'ı olmadığından bu entry gate'i etkilemez, sadece bilgilendirir).
- null → seam yorumu (`# datasource: provider seam — doldurulacak`); **rapor entry'si YOK**
  (.NET null → yalnız seam, ne realized ne policy).
- whitelist dışı → `report.unsupported("dbProvider", value, "whitelist: postgres|sqlserver|h2|inmemory")`
  + seam davranışı (null gibi).
- `testDbProvider` default `inmemory` — T7.1 Fixture emisyonuna parametre (H2, runtime-benzersiz db adı).

### Step 5.2 — CLI Main
**File:** `gen-cli/src/main/java/techgen/cli/Main.java`
**Action:** akış birebir:
```
args: <manifest.json> <outDir>   (eksikse: usage + exit 2)
manifest = Loader.loadManifest(...)            // LoadError → stderr + exit 2
contract = Loader.loadContract(...)
gm = GmBuilder.build(manifest, contract)       // JoinError → stderr + exit 2
config = GenConfig.load(<manifestDir>/gen.config.json)
report = new BuildReport()
SpringEmitter.emit(gm, outDir, report, config)
Completeness.check(manifest, report)
report.writeTo(outDir/build-report.json)
konsol: "emit → {outDir} (clean={...}, constructs={n}, silentDrops={m})"
        her drop: "⚠ SESSİZ DROP: {construct} / {id}"
exit: drops==0 ? 0 : 1
```
(.NET default-arg davranışı taşınmaz: arg'lar zorunlu — sapma SPEC §7 ile uyumlu, raporla.)

### Step 5.3 — Shaded jar
**File:** `gen-cli/pom.xml`
**Action:** maven-shade-plugin (sürüm `docs/surumler.md` pini): `Main-Class: techgen.cli.Main`,
finalName `gen-cli`. `java -jar gen-cli/target/gen-cli.jar` çalışır (SDK'sız JRE 21 yeter).

### Step 5.4 — Testler
**File:** `gen-cli/src/test/java/techgen/cli/MainTest.java` (+ süreç-tabanlı e2e)
- In-process: `Main.run(args)` int döndüren test-edilebilir çekirdek; System.exit yalnız main'de.
- fixture → exit 0; build-report.json + provenance.json VAR; konsol satırı formata uyuyor (regex).
- Sentetik drop senaryosu: emitter'ı kısmî çağıran test-double İLE DEĞİL — mini-manifest'e emitter'ın
  bilmediği bir type-kind ekle (census `{kind}` sayar, emitter realize etmez) → exit 1 + `⚠ SESSİZ DROP`
  satırı. (Bu senaryo gate'in gerçek yolunu kullanır.)
- gen.config varyantları: h2 → parent POM'da h2 bağımlılığı; bilinmeyen `oracle` → build-report'ta
  ("dbProvider","oracle") unsupported + **exit 0** (unsupported drop değildir!).
- Bozuk manifest → exit 2 + stderr "ayrıştırılamadı".
- @Tag("e2e"): shaded jar'ı ProcessBuilder ile koş (`java -jar ...`) → exit 0.

## 6. Acceptance tests
### 6.1 `mvn -q test` kökten → exit 0.
### 6.2 Pozitif — `java -jar gen-cli/target/gen-cli.jar fixtures/manifest.json /tmp/tg-out` exit 0;
`/tmp/tg-out` içinde build-report.json/provenance.json/pom.xml görüldü (elle veya e2e testte).
### 6.3 Negatif — bilinmeyen-kind → exit 1; oracle-provider → exit 0 + unsupported (İKİSİ BİRDEN —
exit ikiliğinin iki yönü).

## 7. Out of scope (DO NOT)
- Golden snapshot — T6.1
- Skill bundle kopyalama — T9.3
- `--help`/alt-komut zenginliği — v1 iki-arg sözleşmesi

## 8. Anti-patterns
- DO NOT exit'i `clean`'e bağla — YALNIZ silentDrops (unsupported exit'i etkilemez).
- DO NOT System.exit'i iş mantığının içine göm — testlenebilir `run(args):int` + ince main.
- DO NOT LoadError/JoinError'ı exit 1 yap — bunlar kullanım/girdi hatası: exit 2 (drop-exit'i kirletme).
- DO NOT shade'de service-file merge'ünü unut — Spring YOK bu jar'da ama Jackson service dosyaları var
  (`ServicesResourceTransformer`).

## 9. Definition of Done
- [ ] GenConfig + Main + shade mevcut
- [ ] Exit üçlüsü (0 temiz / 1 drop / 2 hata) üçü de testli
- [ ] `java -jar` gerçek koşumu yapıldı (çıktı task raporunda)
- [ ] Konsol biçimi regex-testli
- [ ] `git status`: yalnız gen-cli + gen-spring(config)

## 10. Self-check
1. Exit ikiliğini (drop→1, unsupported→0) İKİ ayrı testle kanıtladım mı?
2. Sentetik drop senaryom gate'in GERÇEK yolunu mu kullanıyor (mock değil)?
3. `java -jar`'ı gerçekten koştum mu?
4. Konsol formatını .NET biçimiyle karşılaştırdım mı?
5. Allowlist dışı dosya var mı?
