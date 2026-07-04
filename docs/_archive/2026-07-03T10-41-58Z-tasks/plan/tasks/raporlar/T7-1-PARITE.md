# T7.1-PARITE — 'test' census realize (.NET DotnetEmitter.cs:235 paritesi)

## Bulgu
T7.1 (commit 8b67948) test emisyonunu ekledi ama `emitOneTest`'te `report.realized("test", ...)`
çağrısını YANLIŞ parite gerekçesiyle ("test construct'ı census'ta YOK, rapor kirletilmez")
atladı. .NET `DotnetEmitter.cs:235` her all-Single test emit edildiğinde
`report.Realized("test", $"{scope}_{name}")` çağırır — id-şeması §10: `Process_*` /
`OrphanFlow_*` / `OrphanOp_*`. Kilitli "tam parite" + standing rule #4 (.NET otoriter) gereği
sapma restore edildi.

## .NET kaynağı (READ-ONLY, CoreTemplate1/src/Gen.Dotnet/DotnetEmitter.cs:235 civarı)
```csharp
void EmitOne(string scope, string name, ...)
{
    ...
    WriteAlways(...);
    report.Realized("test", $"{scope}_{name}");   // yalnız emit edilen (all-Single) testler
    ...
}
// çağrılar:
EmitOne("Process", t.ProcessId, ...);              // ProcessTests
EmitOne(t.Scope, t.Id, ...);                        // OrphanFlowTests (t.Scope="OrphanFlow")
EmitOne(t.Scope, t.Id, ...);                        // OrphanOpTests   (t.Scope="OrphanOp")
```
`Gm/TestPlan.cs`: `ScenarioTest.Scope ∈ {"OrphanFlow","OrphanOp"}` (literal, id'den türetilmez).

## Fix (gen-spring/src/main/java/techgen/spring/SpringEmitter.java)
`emitOneTest` imzasına `censusScope` parametresi eklendi ({@code "Process"} literal veya
`ScenarioTest.scope()` — Java tarafında zaten `"OrphanFlow"`/`"OrphanOp"` literal olarak
tutuluyordu, TestPlanBuilder.java:122/132). `folder` parametresi (paket-segmenti, örn.
`orphanflow_{id-lower}`) census id'sinden AYRI tutuldu — census id'si .NET birebir formatı
`{censusScope}_{testId}` kullanır (örn. `OrphanOp_CreateInvoice`, `Process_P`).

Owned iskelet+seam çifti başarıyla emit edildiğinde (yalnız all-Single prereq'li testler —
`AllSinglePrereqs` erken-return'ünden SONRA):
```java
report.realized("test", censusScope + "_" + testId);
```
Single-dışı (Unsupported test-prereq) testlerde bu satıra ULAŞILMAZ (erken `return` — .NET
`AllSinglePrereqs` guard'ıyla birebir).

## Doğrulama (gerçek koşum)
- `mvn -q -am -pl gen-spring compile` → exit 0.
- Kökten `mvn test` → **BUILD SUCCESS**, reactor toplamı **269 test, 0 fail, 0 error, 0 skip**
  (gen-core 106 + gen-spring 155 + gen-cli 7 + conformance 1) — T7.1 öncesiyle birebir aynı sayı,
  regresyon yok.
- `CharacterizationTest` 5/5 YEŞİL — golden emit-snapshot DEĞİŞMEDİ (bu fix yalnız BuildReport
  census girdisi ekliyor, emit dosyası üretmiyor; golden zaten build-report'u kapsamıyor).
- `CensusTest` 6/6 YEŞİL, `assertEquals(77, census.size())` DEĞİŞMEDİ — invoice fixture'ının
  contract'ında processes/flows yok → 0 test → census sabit 77.
- `TestEmissionTest` genişletildi (5 test, hepsi YEŞİL):
  - `fourOrphanOps_allSingle_emitsSkeletonAndArrangeSeam_forEachOp`: artık
    `report.realized("test","OrphanOp_CreateInvoice")` ve
    `report.realized("test","OrphanOp_WriteAuditLog")` entry'lerinin REALIZED olduğunu assert
    ediyor.
  - `ambiguousPrereq_process_emitsNoTestFiles_andReportsUnsupportedTestPrereq`: Single-dışı
    prereq'li process için HİÇBİR `"test"` construct entry'si OLMADIĞINI assert ediyor (negatif
    kontrol — .NET guard paritesi).
  - `studyo_processTestsGreaterThanZero_skeletonsEmitted`: studyo (gerçek veri) emisyonunda en
    az bir `report.realized("test", ...)` entry'si olduğunu assert ediyor.
- Manuel studyo smoke (StudyoScaleE2ETest içinden, aynı harness): studyo emit sonrası report'ta
  çok sayıda `"test"` REALIZED entry'si gözlendi (processTests>0, tamamı all-Single).

## Kapsam
Yalnız `gen-spring/src/main/java/techgen/spring/SpringEmitter.java` (emitOneTest + emitTests +
çağıranları, yorum güncellemesi) ve `gen-spring/src/test/java/techgen/spring/TestEmissionTest.java`
(3 mevcut testte assertion eklendi, yeni test sınıfı/dosya YOK) değişti. gen-core dokunulmadı.
Golden UPDATE_GOLDEN=1 ÇALIŞTIRILMADI (gerek yoktu — golden değişmedi, CharacterizationTest zaten
yeşil).
