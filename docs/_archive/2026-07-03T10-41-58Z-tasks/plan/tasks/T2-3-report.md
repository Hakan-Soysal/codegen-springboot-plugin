# T2.3 🔴 — BuildReport + Census + Completeness gate (INV-7)

## 1. Goal
`BuildReport` (realized/unsupported/emitConflict/silentDrop + policies + deterministik JSON),
`Completeness.census(manifest)` TAM construct sayımı ve `Completeness.check(manifest, report)`
gate'ini gen-core'a ekle.

## 2. Why
INV-7 no-silent-loss bu üçlüde yaşar. Census listesi eksik yazılırsa gate KÖR olur (drop görünmez);
IdMatches yanlış yazılırsa yanlış örtme (soundness kaybı). Exit-code sözleşmesinin (T5.1) tek kaynağı.
Silent-fail riski maksimum → kritik yol.

## 3. Inputs
- `docs/referans/gen-core-davranis-sozlesmesi.md` §7 (BuildReport/Covers/IdMatches + census TAM listesi), §9
- **Pattern (READ-ONLY):** CoreTemplate1 `src/Gen.Core/Report/BuildReport.cs`, `Report/Completeness.cs`
- T1.2 model record'ları
- `fixtures/manifest.json` (census'un canlı örneği)

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q -pl gen-core test    # expected: exit 0 (T1.2 done)
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — BuildReport
**File:** `gen-core/src/main/java/techgen/core/report/BuildReport.java`
**Action:** Add:
- `enum ConstructStatus { REALIZED, UNSUPPORTED, EMIT_CONFLICT, SILENT_DROP }`;
  `record BuildEntry(String construct, String id, ConstructStatus status, String reason)`.
- API: `realized(c,id)`, `unsupported(c,id,reason)`, `conflict(c,id,reason)`,
  `silentDrop(c,id)` → sabit reason `"manifest'te var; ne emit ne rapor (INV-7)"`,
  `policy(name,decision)` → `TreeMap<String,String>`.
- `covers(construct, owner)`: construct **case-insensitive** eşit ∧ status≠SILENT_DROP ∧ idMatches.
- `idMatches(id, owner)`: `id.equals(owner)` VEYA (`id.startsWith(owner)` ∧ `id.length()>owner.length()`
  ∧ `id.charAt(owner.length()) ∈ {'#','-',':',' '}`).
- `clean()`: tüm entry'ler REALIZED. `silentDrops()`: SILENT_DROP listesi.
- `toJson()`: entry'ler (construct, id) ordinal sıralı; status tag'leri `realized|unsupported|
  silentDrop|emitConflict`; kök `{"constructs":[{construct,id,status,reason}],"policies":{}}`;
  pretty (2-space indent), **reason null ise alan yazılmaz**; `writeTo(Path)` sonuna `\n`.

### Step 5.2 — Census
**File:** `gen-core/src/main/java/techgen/core/report/Completeness.java`
**Action:** Add — `census(ManifestJson)` → `List<Map.Entry<String,String>>` (construct, owner),
davranış sözleşmesi §7'deki TAM listeyi birebir üret (deployable/module + Ext; error; external +
boundary-op `{ext}.{op}` + validation + serving `{ext}.{op}:{proto}` + param Ext; uncharted aynısı +
entity concurrency; subscription=event.name; calls=from + compensate; event + payload Ext;
`{t.kind()}`=type id + Ext'ler; entity + Ext + concurrency(optimistic) + invariant(count>0) +
guardRef + alan başına sourceOfTruth `{en}.{f}` + field Ext; operation 16 alt-kuralı: operation/
visibility/roles/scopes/ownership/validation/rule/guardRef/permit/note/throws `{op}->{err}`/
idempotent/pagination/emits `{op}->{ev}`/consistency(mode≠null||risk==eventual)/serving `{op}:{proto}`
+ param/op Ext). Her ext → `("@"+ns+"."+name, owner)`. Sonuç **distinct** (değer-eşitliği).
Build-time-only construct'lar LİSTE DIŞI (sözleşmedeki N/A listesi).

### Step 5.3 — Gate
**Action:** `check(manifest, report)`: census'taki her çift için `!report.covers(...)` →
`report.silentDrop(construct, owner)`.

### Step 5.4 — Testler
**File:** `gen-core/src/test/java/techgen/core/report/ReportTest.java` + `CensusTest.java`
**Action:** Add:
- covers: compound id örtme — `realized("validation","CreateInvoice#Validation0")` sonrası
  `covers("validation","CreateInvoice")` true; **prefix çakışması örtmez** — `realized("operation",
  "GetInvoice")` sonrası `covers("operation","Get")` false.
- clean: unsupported varken false; JSON deterministik (iki farklı ekleme sırası → aynı string).
- silentDrop reason sabiti; reason-null alan-yok (JSON string'inde `"reason"` geçmiyor).
- census fixture: beklenen çiftlerden ≥20'si tek tek assert (ör. ("deployable","BillingService"),
  ("module","Ops"), ("@audit.module","Ops"), ("error","DuplicateInvoice"), ("boundary-op",
  "PaymentGateway.charge"), ("serving","PaymentGateway.charge:rest"), ("subscription","InvoiceCreated"),
  ("calls","CreateInvoice"), ("compensate","CreateInvoice"), ("composite","Money"), ("enum",
  "InvoiceStatus"), ("concurrency","Invoice"), ("invariant","Invoice"), ("sourceOfTruth",
  "AuditLog.invoiceRef"), ("throws","CreateInvoice->DuplicateInvoice"), ("emits",
  "CreateInvoice->InvoiceCreated"), ("serving","CreateInvoice:grpc"), ("visibility","WriteAuditLog"),
  ("permit","WriteAuditLog"), ("guardRef","WriteAuditLog"), ("idempotent","CreateInvoice"),
  ("pagination","ListInvoices"), ("consistency","WriteAuditLog"), ("@trigger.cron","WriteAuditLog")).
- gate: boş report + fixture → silentDrops.size()==census.size(); tam-kapsanmış report → 0 drop.

## 6. Acceptance tests
### 6.1 `mvn -q -pl gen-core test` → exit 0.
### 6.2 Pozitif — fixture census çift assert'leri geçiyor; toplam census sayısı task raporuna yazıldı.
### 6.3 Negatif — prefix-çakışması-örtmez ve boş-report-hepsi-drop testleri geçiyor.

## 7. Out of scope (DO NOT)
- Emitter'ın realized/unsupported ÇAĞRILARI — M3/M4 task'ları
- Exit-code — T5.1
- provenance — T2.4

## 8. Anti-patterns
- DO NOT census'u "fixture'da görünenler" ile sınırla — sözleşme listesi fixture'dan geniştir
  (type kind'ları dinamik: `t.kind()` ne ise construct adı O).
- DO NOT idMatches'i `startsWith` ile bitir — sınır-ayracı seti şart; aksi "Get"/"GetInvoice" hatası.
- DO NOT JSON'ı Jackson default field-sırasıyla yaz — entry sıralaması açıkça (construct,id) ordinal.
- DO NOT policies için HashMap — TreeMap (ordinal) şart.

## 9. Definition of Done
- [ ] BuildReport + Census + check mevcut; javadoc'ta INV-7 referansı
- [ ] ≥14 test; census fixture assert'leri ≥20 çift
- [ ] 6.1-6.3 koşuldu; census toplamı raporda
- [ ] `git status`: yalnız gen-core

## 10. Self-check
1. Census'un sözleşme listesindeki HER kuralını koda tek tek işledim mi (satır satır karşılaştırdım mı)?
2. IdMatches'in 4 ayracını da (`#`,`-`,`:`,` `) test ettim mi?
3. JSON determinizmini iki-farklı-ekleme-sırası testiyle kanıtladım mı?
4. Testleri gerçekten koştum mu?
5. Allowlist dışı dosya var mı?
