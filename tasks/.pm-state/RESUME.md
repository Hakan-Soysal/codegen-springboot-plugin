# RESUME — techgen-spring PM checkpoint

_Yazılma: 2026-07-03 (resume session — M3 ortası, kredi bitişi molası)_
_Run: 2026-07-03T10-41-58Z-tasks · retry=3 · executor=Sonnet · verifier=Opus_
_Son yeşil kök test: 116 (M2 kapanışında). M3 sürüyor — gen-spring test sayısı T3.4 sonrası 42._

## ⚠️ /clear SONRASI İLK 3 ADIM (resume eden PM buradan başla)

1. **Sağlık kontrolü:** Rule-7 JDK21 export ile kökten `mvn test` → exit 0 beklenir (gen-core 104 + gen-spring 42 + gen-cli 1 + conformance 1 = 148). git log HEAD=52310b2 (T3.4).
2. **T3.4 VERIFIER'ı KOŞ (EN ÖNCELİKLİ):** T3.4 executor DONE ama HENÜZ DOĞRULANMADI (commit 52310b2). Opus verifier spawn et — tasks/T3-4-jpa.md'ye karşı bağımsız doğrula. Özellikle yargıla: (a) @Id/@Version/@Enumerated fixture'la eşleşiyor mu; (b) sourceOfTruth'ta İLİŞKİ AÇILMADIĞI negatif assert'li mi; (c) PersistenceConfig Bootstrap'a @Import edilmiş mi; (d) entity-level ext'in typeLevelExtComment ile realize'ı spec-uyumlu mu; (e) gen-core dokunulmamış + SpringEmitter'da reformatlama yok.
3. **T3.4 PASS ise → T3.5 (JavaPredicateRenderer + Guards + Invariants) DOĞRUDAN MAIN'de** (worktree YOK — silindi). Sonra T3.6 → T3.7 → M3 milestone verifier.

## Durum özeti

| Milestone | Durum |
|---|---|
| M0, M1, M2 | ✅ PASS (milestone verifier dahil) |
| M3 | T3.1 ✅ T3.2 ✅ T3.3 ✅ · **T3.4 executor-DONE/doğrulanmadı (52310b2)** · T3.5 pending(main'de) · T3.6, T3.7 pending |
| M4-M10 | bekliyor |

Retry-FAIL sayısı: 0. T3.1'de bir reconcile (finishAndPrune imza hizalama, commit 1bc1221) — verifier PASS sonrası spec-uyum düzeltmesi, retry değil.

Commit zinciri (M3): 3e9e9c9 (T3.1) → 1bc1221 (T3.1 hizalama) → bf0fd93 (T3.2) → 5ff6a6a (T3.3) → 52310b2 (T3.4).

## Paralellik rejimi

- Onaylı paket (escalations/2026-07-03T12-30-parallelism.md): T2.3∥T2.4 ✅(bitti), T6.2∥T6.3(worktree), T9.2∥T9.3, M8∥M6-M7(worktree, graf sapması onaylı).
- M3/M4 pencere-bazlı (escalations/2026-07-03T16-20-parallelism-m3m4.md + .pm-state/p2p3-footprint-karar.md):
  - **P2 (T3.3/T3.4/T3.5):** [T3.3→T3.4] ana ağaçta sıralı ∥ T3.5 worktree PLANLANMIŞTI. GERÇEKLEŞEN: T3.3, T3.4 sıralı bitti; T3.5 worktree denemesi 2× API kesintisiyle düştü (kod yazılmadı) → worktree silindi, **T3.5 main'de sıralı** koşacak (paralel komşular bitti, worktree faydası kalmadı).
  - **P3 (M4=T4.1-T4.5): TAMAMEN SIRALI** — T4.1/T4.2/T4.4 aynı HandlerBase ctor/field bloğu + ctor-senkron Wiring çağrısı = yoğun çakışma; ayak-izi analizi (p2p3-footprint-karar.md) sıralıyı zorunlu kılıyor.

## Active standing rules

1. [SCOPE: architectural] CoreTemplate1 (/Users/hakansoysal/Desktop/ClaudeCode Denemeler/CoreTemplate1) READ-ONLY; okunur, ASLA yazılmaz.
2. [SCOPE: process] "Derleniyor/testler geçiyor" yalnız GERÇEK mvn çıktısıyla (exit code gözlendi).
3. [SCOPE: process] Golden yalnız UPDATE_GOLDEN=1 + task raporunda diff gerekçesiyle güncellenir.
4. [SCOPE: architectural] SPEC sapması → DUR + raporla (PM müşteriye eskale eder); sessiz sapma yok.
5. [SCOPE: process] Executor insan-okur raporunu tasks/raporlar/T{X}-{Y}.md'ye yazar (plan §4 — plan klasörüne izinli TEK yazma).
6. [SCOPE: process] tasks/_uyumluluk-raporu-2026-07-03.md §7 kararları bağlayıcı: controller kaydı TAM-AÇIK @Bean (scan yok); Generation Gap seam + 'doldurulacak' marker HUMAN-SEAM işaretidir (gen-owned WriteAlways dosyalara KOYULMAZ — T3.3'te bu ayrım doğrulandı); parent-POM / h2 onaylı.
7. [SCOPE: environmental] JDK 21 sistemde YOK. Temurin 21.0.11:
   `/private/tmp/claude-501/-Users-hakansoysal-Desktop-ClaudeCode-Denemeler-SpringBoot-Template/eb7a559a-6022-43dc-b5cd-8073348492aa/scratchpad/jdk21/jdk-21.0.11+10/Contents/Home`
   HER java/mvn komutu öncesi AYNI Bash çağrısında JAVA_HOME+PATH export. **DİKKAT (resume):** /private/tmp altında — reboot'ta silinmiş olabilir; yoksa `mvn`den önce `ls "$JDK/bin/java"` ile doğrula, yoksa Adoptium'dan yeniden kur ve bu yolu güncelle (escalations/2026-07-03T10-45-jdk21.md).
8. [SCOPE: process] gen-spring mvn koşumlarında `-am` ZORUNLU (gen-core .m2'de kurulu değil; -am'siz literal komut exit 1 verir — kod kusuru değil, ortam gerçeği).
9. [SCOPE: process] Push yalnız origin main'e, PM kararıyla, milestone checkpoint'lerinde. **DİKKAT:** Bu oturumda otomatik push izin sınıflandırıcısınca REDDEDİLDİ (oturum-içi açık onay yok). Push gerekiyorsa kullanıcıdan açık onay al veya kullanıcı `! git push origin main` çalıştırsın. Şu an origin, M2 checkpoint'ten (05bb4d5) sonra push EDİLMEMİŞ durumda — local commit'ler: 5ff6a6a, 52310b2 + sonrası.

## Executor spawn deseni (kanıtlanmış — kopyala)

- Model: sonnet (kullanıcı direktifi, kritik yol dahil). Verifier: opus (her zaman).
- Prompt şablonu: references/executor-prompt.md + yukarıdaki Active rules 1-9 verbatim GLOBAL CONTEXT'e.
- Her executor: mvn -am + JDK21 export; commit "T{X}.{Y}: <özet>"; .pm-state/ + .claude/settings.local.json ASLA stage; push yok; rapor tasks/raporlar/T{X}-{Y}.md.
- Executor DONE → subagent-reports/T{X}-{Y}-attempt-N.json yaz → Opus verifier spawn → verifier-reports/T{X}-{Y}-attempt-N.json → PASS ise TaskUpdate completed + status.json.
- **API kesintisinde** (stream stall / kredi): agent'ı SendMessage ile agentId'sine "kaldığın yerden devam et" mesajıyla resume et (bağlam korunur). İki kez düşerse (T3.5 gibi) fresh spawn + gerekiyorsa worktree'yi temizle.

## Devam eden gözlemler (bloklamayan)

- T2.4 plan-metni niti (doc-only): M2 T2.4 DoD literal komutu -am'siz; kural 8 bunu kapsıyor.
- Provenance alan adı `clazz` vs .NET `Class` — byte-eşit cross-runtime tüketici yok; C#-üretimli byte-golden eklenirse yeniden değerlendir.
- GmBuilderTest bayat yorumlar — housekeeping adayı.
- Verifier raporları .pm-state/verifier-reports/ altında: T2-3, T2-4, M2-milestone, T3-1, T3-2, T3-3 mevcut. subagent-reports/ + subagent-prompts/ aynı şekilde. **T3-4 verifier raporu HENÜZ YOK — koşulacak.**

## State files of record

- `.pm-state/status.json` — makine-okur run state (T3.4 = executor_done_unverified)
- `.pm-state/escalations/` — 3 dosya (jdk21, parallelism 12:30, parallelism-m3m4 16:20)
- `.pm-state/p2p3-footprint-karar.md` — P2/P3 dosya-ayak-izi analizi + paralellik kararı
- `.pm-state/verifier-reports/` — 6 verdict (T3-4 eksik)
- TaskList (harness): #1-4 completed, #5(T3.3) completed, #6(T3.4) in_progress(=executor done, verify pending), #7(T3.5) pending, #8+ pending
- Model notu: kullanıcı oturum default'unu Opus 4.8'e çevirdi; executor spawn'ları YİNE sonnet (kullanıcı önceki direktifi) — aksini istemedikçe koru.
