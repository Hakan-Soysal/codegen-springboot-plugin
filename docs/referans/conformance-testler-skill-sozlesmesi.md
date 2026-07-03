# Conformance Runner + Test Süiti + Skill Paketi — Davranışsal Sözleşme Referansı

> CoreTemplate1'in `conformance-adapter/`, `tests/Gen.Tests/` ve
> `plugins/codegen/skills/base-dotnet-rest/references/` tam okumasından çıkarılmıştır.
> Java kardeşinin conformance runner'ı, test süiti ve `base-springboot-rest` skill paketi
> bu sözleşmeleri birebir taşır. Anchor'lar CoreTemplate1 dosyalarına işaret eder (READ-ONLY).

---

## A) Conformance runner sözleşmesi

### A.1 SPEC JSON şeması (`conformance-adapter/Spec.cs`)
Şekil `{construct, opId, arrange, act, assert}` (`Spec.cs:9-14`). Case-insensitive, yorum + trailing-comma toleranslı (`:36-41`). **Her JSON dosyası TAM BİR Spec'tir; array parser YOK** (`:43-48`).
- `construct` (string) — davranış sınıfı. Runner'ın özel kolları: `"invariant"` (property-test dalı). Verify-loop eşlemesi: throws/validation/rule/invariant/idempotent/saga/pagination/roles/ownership/scopes.
- `opId` (string) — handler resolve anahtarı; tip adı `{opId}Handler`.
- `arrange` (serbest JSON) — yalnız `kind` yorumlanır: `"duplicate"` (act öncesi bir seed çağrısı → dedup state), `"property"` (invariant), `{}`.
- `act` = `{call, with}` — `with` camelCase request payload.
- `assert` — hepsi nullable/contract-türevli: `resultType` (beklenen Result alt-tip adı), `code` (NotProcessable.Code), `violated` (not; assert edilmez), `stub` (true→skip), `expected`, `source` (provenance), invariant üçlüsü `field/op/bound(decimal)`.
- **A3 değişmezi:** beklenen değerler yalnız SPEC'ten; adapter'a literal gömülmez.

### A.2 SpecRunner mekaniği (`SpecRunner.cs`)
Her spec sırayla (`RunOneAsync`):
1. `stub==true` → Skipped.
2. Scope + `ResolveHandler(opId)`.
3. `construct=="invariant"` → property dalı.
4. arrange `kind=="duplicate"` → BuildRequest+ActAsync ile seed çağrısı (`:42-47`).
5. act: `BuildRequest(handler, act.with)` → `ActAsync`.
6. assert: `Inspect(result)` → `AssertAgainstSpec` (`:66-86`): resultType null→Fail; observed≠expected (Ordinal)→Fail; code spec'te varsa Ordinal eşleşmeli.
7. Herhangi exception (NotImplementedException dahil) → Fail.

**Invariant property koşumu** (`:91-121`): field/op/bound eksik→Skipped; **50 tur**; `RandomDecimals(50, 0, 1000, seed=20260625)` deterministik; her tur act.with'in ilk numeric alanı rastgele değerle değiştirilir, op çağrılır, Success.Value'daki field decimal okunur; predicate (`>=,>,<=,<,==,=`) ihlalde ilk karşı-örnekle Fail; Success/field üretmeyen tur skip.

`SpecResult`: Pass|Fail|Skipped + Detail.

### A.3 GeneratedApp yükleme (`GeneratedApp.cs`)
- İzole AssemblyLoadContext + deps.json resolver (`:160-171`).
- Load: dll → `{rootNs}.GeneratedBootstrap` (default rootNs="App") → statik `AddGenerated(IServiceCollection)` reflection invoke → ServiceProvider (`:26-43`).
- ResolveHandler: adı `{opId}Handler` olan VE `ExecuteAsync` metodu olan tip → DI'dan al (`:49-62`).
- BuildRequest: ExecuteAsync ilk parametresi = request tipi; en-çok-parametreli ctor; JSON camelCase → ctor param (OrdinalIgnoreCase); Convert: string/decimal/int/long/double/bool/Guid/nullable + fallback deserialize (`:66-104`).
- ActAsync: `ExecuteAsync(request, CancellationToken.None)` → Task await → `.Result` oku (`:107-114`).
- Inspect: alt-tip adı (generic tick soyulur) + varsa `Code` → `ResultShape(ResultType, Code)` (`:118-131`).
- TryGetSuccessFieldDecimal: Success.Value.{field} → decimal (`:135-155`).

**Java eşlemesi:** ALC → izole classloader (URLClassLoader, app classpath); AddGenerated+DI → Spring `ApplicationContext` bootstrap (web-server'sız); reflection invoke + sealed-record `getClass().getSimpleName()`; decimal → BigDecimal.

### A.4 Exit code'lar (`Program.cs`)
Kullanım: `Conformance <appPath> <specsPath>`; specsPath tek dosya→1 spec; dizin→recursive `*.json` ordinal-sıralı.
- **0** — tüm non-skip PASS; **1** — ≥1 FAIL; **2** — usage/parse/load hatası.
- Skip fail sayılmaz. Çıktı satırı: `[PASS|FAIL|SKIP] <construct>/<opId>: <detail>` + özet `conformance: N pass, N fail, N skip`.

### A.5 Adapter acceptance testleri (Tests/)
Üç test (gerçek execution, A3 kanıtı): doğru seam→tüm spec PASS; yanlış seam (ServerError döndüren)→throws spec FAIL + validation spec hâlâ PASS (seçicilik); invariant property üreteci gerçekten N tur koşar. `AppFixture`: fixture manifest'ten emit + seam'i throwaway impl ile doldur + build → App.dll. Runner scaffold'a bağımlı DEĞİL.

---

## B) Test süiti sözleşmeleri (tests/Gen.Tests)

Java kardeşinde birebir port edilecek test davranışları:

- **CharacterizationTests** — golden ağaç snapshot (`relpath\tSHA256`, `golden/emit-snapshot.txt`, `UPDATE_GOLDEN=1` ile yenilenir); write-only-if-changed (mtime korunur); op silinince `.g.cs` prune + human Logic korunur; HumanShell (Program/csproj) regen'de ezilmez.
- **EmitTests** (13) — üretilen app derlenir + 0 SilentDrop; db-provider config yolları (sqlite derlenir, null→seam, bilinmeyen→Unsupported+seam); request record + partial handler; EF entity + RowVersion + DbContext; event/bus/auth; boundary + Idempotency + policies; error kataloğu + throws fabrikası; ResultHttp Override hook; pagination Page+cursor/size; Logic regen'de korunur; **iki farklı dizinde emit byte-özdeş**.
- **CompletenessTests** — SilentDrop seti `KnownDebt` allowlist'ini (boş) aşamaz (ratchet).
- **LatentConstructTests** (~17) — fixture in-memory mutasyonla genişletilip her keyword'ün emit+census kapsaması: consistency/outbox, deployable host, @http/@trigger, tüm ext site'ları, subscription, @internal route bastırma, boundary serving+validation, **grpc/queue→explicit Unsupported**, guardRef, GET param binding, pagination offset, decimal suffix, uncharted, sourceOfTruth, note.
- **ExprEmitTests** (6) — predicate emit: typed input; collision-safe `input.ResourceCreditLimit`; and/or/eq; distinct path; agg/call/duration→UnsupportedConstruct; PropName join.
- **PocoTests** — ExprNode polymorphic parse + bilinmeyen şekil throw + round-trip; manifest/contract POCO sayıları.
- **JoinTests** (6) — contract manifest-göreli çözüm; linked-join + command/query türetme; id-sıralı determinizm; standalone skip; çözülemeyen realizes/eksik contract → JoinError.
- **ContractParseTests** — effect.target string VE path-array kabul (regression).
- **GenConfigTests** — parse/absent-null.
- **ReportTests** (4) — Clean semantiği; Covers compound-id örter ama prefix-çakışmasını örtmez; JSON deterministik; **`silentDrops` diye ayrı JSON alanı yok — status alanı + exit≠0**.
- **SkillSyncTests** (3) — bayat seam build'i yüksek sesle kırar; brownfield düz-layout seam slice'a TAŞINIR (kopyalanmaz); op silinince orphan Logic tespit edilebilir (auto-silinmez).
- **FixtureSmokeTest** — fixture'lar geçerli JSON + beklenen kökler.

**Fixture'lar:** `manifest.json` (238 satır; full-keyword "PoC Billing": 4 op, 2 entity, 4 type, 1 error, 1 event, 1 subscription, 1 external, 1 uncharted, 2 callEdge) + `operations.json` (schemaVersion 2) = birincil. `studyo.manifest.json` (3832 satır; 43 op, 9 entity, 4 process, 14 flow) = ölçek fixture'ı (otomatik testte kullanılmıyor). Golden snapshot 56 satır.

---

## C) Skill paketi mekanikleri (base-dotnet-rest → base-springboot-rest)

### C.1 gap-protocol.md (T5.2)
- **§0.5 ön-kapı** (contract-only, fill-öncesi; gen/** + build-report OKUNMAZ): P1 manifest sağlık (hasErrors=false ∧ coverage boş); P2 referential integrity (**id-keyed çözüm, name değil**; returns/params → types∪entities∪skaler-küme; skaler-küme `ref:"scalar"` ground-truth); P3 K2-contract (failable→named-error); P4 K1-contract yalnız kaynak 1-3 (kaynak-4 policy post-gen'e ertelenir). Defekt → DUR + `back-to-teknik-analiz`.
- **K1-K4 gate** (fill-öncesi, deterministik): K1 predicate-input dört kaynaktan birine (request-param/entity-field/boundary-dönüş/build-report.policy); K2 failable→throws→errors; K3 dependency DI'da veya boundary; K4 unsupported-değme. **Dual-layer (INV-B):** paket K1-K4 erken-DUR; aile yalnız K1/K2 bağımsız yeniden koşar.
- **Çözüm kademesi rung 1-4** (ilk eşleşen): 1=üreteç-policy (otomatik+rapor); 2=kayıtlı çözüm (registry signature); 3=unsupported-bilinen; 3b=codebase-grounded KESİN inference; 4=DUR+sor.
- **§2b iki-bant:** tek-aday deterministik (tam bir FK/nav/tip) → `ASSUMPTIONS.md`'ye (app kökü; format: `## {Op}` altında `NE:/NEDEN: <dosya:sembol>/GÜVEN:`) yaz + DEVAM; 0 veya ≥2 aday → DUR+sor.
- **§3 DUR/sor/kaydet:** gap'i kesin sun; kullanıcı eylemi: use-generator-policy:X / inject-human-interface / map-to-error:Y / back-to-teknik-analiz; tekrar-edilebilirse registry kaydı öner.

### C.2 verify-loop.md (T5.5)
- **Kapı 0 — access-coverage** (post-fill, build-öncesi): `entities_persisted(seam) ⊇ manifest.access.{creates,updates,deletes}`; kaynak YALNIZ manifest (operations.json tripwire: `writes` anahtarı görürsen yanlış kaynak).
- **Kapı 1 — build** exit 0 (gerekli ama yetersiz).
- **Kapı 2 — conformance**: SPEC'leri adapter'la koş, hepsi PASS; deterministik oracle, LLM-judge ASLA.
- **Kapı 3 — bağımsız adversarial denetim**: seam'i yazmayan subagent; dosya × lens (integrity/security/performance); default-kusurlu; mitigation kod-kanıtı; etki persisted-state'ten. Dispozisyon: seam-fixable→düzelt+loop tekrar; yapısal→GAP→route (retry tüketmez).
- **Retry:** seam başına ≤3 build-fix; fresh-start 1 kez; sonra gap→DUR.
- **Halüsinasyon kapısı:** yalnız contract/gen'de var olan tip/paket; build + paket-allowlist.

### C.3 archetype-playbooks.md (T5.3)
**A2 tek-tip seam (global):** gen-owned bildirim (hep ezilir) + human gövde (WriteIfAbsent, donar, `doldurulacak` marker). 8 arketip ve kanonik sıraları:
1. **Command**: validate → rule → entity+invariant → persist → emit → Result.
2. **Query**: yetki → sorgu → projeksiyon → Result (read-only; mutasyon/emit yok).
3. **Saga**: Command + dış-çağrı + hata→ters-sıra compensate (LIFO).
4. **Idempotency**: başta TryBegin (key=IdempotencyKeys); replay→aynı sonuç yan-etkisiz.
5. **Pagination**: Query + strategy (cursor/offset) + Page<T> zarfı.
6. **Trigger-inbound**: StartAsync → oku → request kur → handler'ı çağır → ack (yalnız wiring).
7. **Subscription-consumer**: HandleAsync → event→request eşle → handler (yalnız wiring).
8. **Boundary-client**: üye başına request kur → dış-çağrı → yanıt eşle → tipli dönüş.
Kapanış kuralları: yalnız contract/gen tipleri; kanonik sıra ihlal edilmez; gen üyeleri bağlanır (icat edilmez); marker değişir imza değişmez; gen/**'e asla yazılmaz.

### C.4 gap-registry.md (T5.4)
Disk: `<proje>/.dsl/gap-policies/<pkg>@<ver>/*.json`; her dosya = bir gap-signature; eşleşme içerik-signature'a göre; git'e commit. Kayıt 4 alan: gap-signature{archetype,kural,tetik} / resolution{method,params,template} / scope{package} / provenance{taught-by,date,repeatable}. Merge: paket-seed ⊕ proje-öğretili, çakışmada proje kazanır. Sürüm-drift: scope.package sürümü ≠ aktif → oto-uygulanmaz, yeniden-doğrula.

### C.5 evals.json — 9 senaryo
1. command-saga-idem birleşik fill → build0+conformance PASS.
2. bilinmeyen gap (creditLimit bağlanamaz) → K1 GAP → DUR, gövde yazılmaz.
3. bilinen gap (dedup-store policy) → rung-1 otomatik + rapor.
4. codebase-grounded tek-aday → ASSUMPTIONS.md + devam.
5. çoklu-aday → DUR, ledger'a yazılmaz.
6. Faz 0.5 sağlam manifest → PASS → üretime geç.
7. Faz 0.5 defektli manifest (havada returns + adsız rule) → DUR + back-to-teknik-analiz.
8. Kapı 0 access-divergence (Package eksik) → FAIL → düzelt → PASS.
9. test-arrange ARRANGE-only; ASSERT owned dokunulmaz (anti-circularity).

### C.6 techgen-sync skill özeti
Hedef app'i manifest'e göre delta-sync ile yeniden üretir + derlenebilir tutar: üreteci çağırır, gen/ günceller, orphan .g.cs prune eder; HumanShell/HumanSeam'e dokunmaz. İki muhakeme noktası: (1) imza değişince build kasıtlı kırılır → skill insan gövdesini mantığı koruyarak yeni imzaya uyarlar; (2) op silinince orphan Logic tespit edilir → kullanıcıya sorulur (onaysız silme yok). Exit 0 ⟺ SilentDrop yok.
