# RESUME — techgen-spring PM checkpoint

_Yazılma: 2026-07-03 (resume session — M4 KAPANDI, M5 girişi)_
_Run: 2026-07-03T10-41-58Z-tasks · retry=3 · executor=Sonnet · verifier=Opus_
_Son yeşil kök test: 225 (gen-core 104 + gen-spring 119 + gen-cli 1 + conformance 1). Üretilen app 60 dosya compile exit 0, fixture 0 silentDrop._

## ⚠️ /clear SONRASI İLK ADIMLAR

1. Sağlık: Rule-7 JDK21 export ile kökten `mvn test` → exit 0 (~225 test). git HEAD son PM checkpoint (M4).
2. SIRADAKI: M5 = T5.1 (GenConfig + CLI main + exit-code + shaded jar) — tek task, tam-format tasks/T5-1-cli.md. Uçtan uca CLI zinciri + INV-7 exit sözleşmesi + shaded jar.
3. Sonra: M6 (T6.1 golden → T6.2∥T6.3 worktree) → M7 (T7.1) ∥ M8 (T8.1→T8.2 worktree, onaylı graf sapması) → M9 (T9.1 → T9.2∥T9.3) → M10 (T10.1).

## Durum özeti

| Milestone | Durum |
|---|---|
| M0, M1, M2, M3, M4 | ✅ PASS (milestone verifier dahil) |
| M5 | T5.1 in_progress |
| M6-M10 | bekliyor |

Retry-FAIL: 0 (tüm task ilk denemede PASS). T4.1'de reconcile YOK — T3.1'de bir reconcile (finishAndPrune imza) olmuştu.
**Kritik başarı:** M4 sonunda fixture TAMAMEN 0 silentDrop — 43 farklı construct tipi realize, tam .NET paritesi. Üretilen Spring app 60 dosyayla derleniyor.

M4 commit zinciri: f0b798d(T4.1)→5177ceb(T4.2)→dd22844(T4.3)→4d8efeab(T4.4)→ffefe39(T4.5).

## Paralellik rejimi

- Onaylı paket (escalations/2026-07-03T12-30): T2.3∥T2.4 ✅. Kalan: **T6.2∥T6.3 (worktree)**, **T9.2∥T9.3 (aynı ağaç)**, **M8(T8.1-T8.2)∥M6-M7 (worktree, graf sapması onaylı)**.
- M3/M4 pencere-bazlı deneyimi (p2p3-footprint-karar.md): M3'te T3.5 worktree denendi (API stall'lar nedeniyle sonunda main'de sıralı bitti); M4 tamamen sıralıydı (ortak HandlerBase ctor bloğu).
- **M5 tek task** — paralellik yok. M6+ için worktree deseni: yalnız gerçekten ayrık-dosyalı işlerde; her merge sonrası ana ağaçta tam re-verify; integrator çatışmada DUR (çözmez).

## Active standing rules

1. [SCOPE: architectural] CoreTemplate1 READ-ONLY; okunur, ASLA yazılmaz.
2. [SCOPE: process] "Derleniyor/testler geçiyor" yalnız GERÇEK mvn çıktısıyla (exit code gözlendi).
3. [SCOPE: process] Golden yalnız UPDATE_GOLDEN=1 + task raporunda diff gerekçesiyle güncellenir. **(M6 golden task'ında kritik olacak.)**
4. [SCOPE: architectural] SPEC sapması → DUR + raporla; sessiz sapma yok. İç-tutarsızlıkta verifier MEŞRU-mu-ESCALATE-mi yargılar; kaynak (SPEC/.NET parite) otoriter.
5. [SCOPE: process] Executor insan-okur raporunu tasks/raporlar/T{X}-{Y}.md'ye yazar (plan §4 — izinli TEK plan-klasörü yazımı). Test sayılarını GERÇEK reactor'dan doğru yaz (T4.1'de yanlış sayı raporlandı, sonraki task'larda düzeltildi).
6. [SCOPE: process] tasks/_uyumluluk-raporu-2026-07-03.md §7 bağlayıcı: TAM-AÇIK @Bean (component-scan YOK; JPA @EnableJpaRepositories/@EntityScan istisna=idiomatik); Generation Gap seam + 'doldurulacak' HUMAN-SEAM marker (WriteIfAbsent human dosyalarda; gen-owned WriteAlways'te ASLA); h2.
7. [SCOPE: environmental] JDK 21 sistemde YOK. Temurin 21.0.11:
   `/private/tmp/claude-501/-Users-hakansoysal-Desktop-ClaudeCode-Denemeler-SpringBoot-Template/eb7a559a-6022-43dc-b5cd-8073348492aa/scratchpad/jdk21/jdk-21.0.11+10/Contents/Home`
   HER java/mvn öncesi AYNI Bash çağrısında export; ilk-iş `ls "$JDK/bin/java"`. /private/tmp — reboot'ta silinebilir; yoksa Adoptium'dan kur + yolu güncelle (escalations/2026-07-03T10-45-jdk21.md).
8. [SCOPE: process] gen-spring mvn koşumlarında `-am` ZORUNLU. Push yalnız origin main'e, PM kararıyla, milestone checkpoint'lerinde. **DİKKAT:** otomatik push izin sınıflandırıcısınca REDDEDİLDİ; kullanıcı açık onayı veya `! git push origin main` gerekir. Origin M2 checkpoint'ten (05bb4d5) sonra push EDİLMEDİ — local'de M3+M4 tüm commit'leri bekliyor.
9. [SCOPE: process] Executor verimlilik: uzun analiz turları API stream-stall'a yakalanıyor (T3.5'te 4×). Executor prompt'una ekle: advisor çağırma; okumadan sonra doğrudan yaz; işi küçük turlara böl (kod→test→mvn→commit); JDK21 ilk-iş ls. Kesintide SendMessage ile agentId'ye "kaldığın yerden devam et".

## Retired rules (arşiv — spawn'lara TAŞINMAZ)

- ~~[scaffolding rule 9] T4.3 @trigger realize tekrarlamama~~ — RETIRED after T4.3 PASS (dd22844; by-construction 1 entry doğrulandı).
- ~~[scaffolding rule 8] M4 ctor-threading repos→idempotent→events→boundary + cross-site ctor-SIRA assert~~ — RETIRED after M4 close. Final ctor sırası [invoiceRepository, idempotencyStore, eventBus, paymentGateway]. Yeni HandlerBase dep'i planlanmıyor.

## Known follow-up notes (doc-only, non-blocking)

- **tasks/T3-5-predicate.md §5.1** 'Double varsay' → 'Decimal/BigDecimal varsay' (kod doğru, belge yanıltıcı; verifier-reports/T3-5-attempt-2.json).
- **tasks/IMPLEMENTATION-PLAN.md T4.3 ~satır 245** 'realized(@trigger.{name})' — .NET paritesiyle çelişir (retired rule 9 ile çözüldü); belge düzeltilmeli.
- Provenance `clazz` vs .NET `Class` — byte-eşit cross-runtime tüketici yok.
- {ns}-realization policy açıklaması 'annotation/interceptor' vs .NET 'interceptor/attribute' — pinlenmemiş, benign.

## Executor/Verifier spawn deseni

- Executor sonnet, verifier opus (her zaman). Prompt: executor-prompt.md/verifier-prompt.md + Active rules 1-9 verbatim GLOBAL CONTEXT'e (retired kurallar TAŞINMAZ).
- Akış: executor DONE → subagent-reports/*.json → opus verifier → verifier-reports/*.json → PASS ise TaskUpdate completed + status.json. Milestone bitince milestone verifier (bağımsız harness ile DoD üret) + RESUME güncelle + PM checkpoint commit.
- Verifier deseni (kanıtlanmış değerli): milestone DoD'unu ZeroDropTest gibi task-testlerine GÜVENMEDEN, kendi harness'ıyla bağımsız üret (T4.5/M4 verifier VerifyDrop.java yazdı).

## State files of record

- .pm-state/status.json — M0-M4 completed; M5 T5.1 in_progress. open_observations: Active 1-9 + 2 RETIRED (rule 8, rule 9).
- .pm-state/verifier-reports/ — T2-3,T2-4,M2,T3-1..T3-7,M3,T4-1..T4-5,M4 (21 verdict).
- .pm-state/p2p3-footprint-karar.md — M4 sıralı gerekçesi.
- .pm-state/escalations/ — 3 (jdk21, parallelism 12:30, parallelism-m3m4 16:20).
- TaskList: #1-14 completed, #15(T5.1) → sıradaki, #16-25 pending.
- Model: kullanıcı oturum default'u Opus 4.8; executor spawn'ları YİNE sonnet (kullanıcı direktifi) — aksi istenmedikçe koru.
