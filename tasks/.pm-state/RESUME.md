# RESUME — techgen-spring PM checkpoint

_Yazılma: 2026-07-03 (resume session — M3 KAPANDI, M4 girişi)_
_Run: 2026-07-03T10-41-58Z-tasks · retry=3 · executor=Sonnet · verifier=Opus_
_Son yeşil kök test: 178 (gen-core 104 + gen-spring 72 + gen-cli 1 + conformance 1). Üretilen app 45 dosya compile exit 0._

## ⚠️ /clear SONRASI İLK ADIMLAR

1. Sağlık: Rule-7 JDK21 export ile kökten `mvn test` → exit 0 (~178 test). git HEAD son PM checkpoint commit'i (M3).
2. SIRADAKI: M4 = T4.1→T4.2→T4.3→T4.4→T4.5 TAMAMEN SIRALI (ayak-izi: ortak HandlerBase ctor/field bloğu). T4.1 (Idempotency+Pagination) executor'ı koşuyor/koşulacak.
3. **T4.3 için AKTİF scaffolding rule** (aşağıda rule 9) — T4.3 executor+verifier prompt'una MUTLAKA verbatim taşı.

## Durum özeti

| Milestone | Durum |
|---|---|
| M0, M1, M2, M3 | ✅ PASS (milestone verifier dahil) |
| M4 | T4.1 in_progress · T4.2-T4.5 pending (SIRALI) |
| M5-M10 | bekliyor |

Retry-FAIL: 0. T3.5 executor 4× API stream-stall yaşadı (mantık değil altyapı) — SendMessage resume + küçük-adım stratejisiyle aşıldı, PASS.

M3 commit zinciri: 3e9e9c9→1bc1221(T3.1)→bf0fd93(T3.2)→5ff6a6a(T3.3)→52310b2(T3.4)→fec500b(T3.5)→3d5550c(T3.6)→9d836ca(T3.7).

## Paralellik rejimi

- Onaylı paket (escalations/2026-07-03T12-30): T2.3∥T2.4 ✅. Kalan: T6.2∥T6.3(worktree), T9.2∥T9.3, M8∥M6-M7(worktree, graf sapması onaylı).
- **M4 (T4.1-T4.5): TAMAMEN SIRALI** — p2p3-footprint-karar.md: T4.1/T4.2/T4.4 aynı HandlerBase ctor/field bloğu + ctor-senkron Wiring çağrısı = yoğun çakışma; worktree faydasız. T4.5 zaten süpürme (en son).
- Worktree deseni ileride yalnız GERÇEKTEN ayrık-dosyalı işlerde (M6.2/6.3, M8) kullanılacak.

## Active standing rules

1. [SCOPE: architectural] CoreTemplate1 READ-ONLY; okunur, ASLA yazılmaz.
2. [SCOPE: process] "Derleniyor/testler geçiyor" yalnız GERÇEK mvn çıktısıyla (exit code gözlendi).
3. [SCOPE: process] Golden yalnız UPDATE_GOLDEN=1 + task raporunda diff gerekçesiyle güncellenir.
4. [SCOPE: architectural] SPEC sapması → DUR + raporla; sessiz sapma yok. İç-tutarsızlıkta verifier MEŞRU-mu-ESCALATE-mi yargılar; kaynak (SPEC/.NET parite) otoriter.
5. [SCOPE: process] Executor insan-okur raporunu tasks/raporlar/T{X}-{Y}.md'ye yazar (plan §4 — izinli TEK plan-klasörü yazımı).
6. [SCOPE: process] tasks/_uyumluluk-raporu-2026-07-03.md §7 bağlayıcı: TAM-AÇIK @Bean (component-scan YOK; JPA @EnableJpaRepositories/@EntityScan istisna=idiomatik, iş-bean scan DEĞİL); Generation Gap seam + 'doldurulacak' HUMAN-SEAM marker (WriteIfAbsent human dosyalarda; gen-owned WriteAlways'te ASLA); h2.
7. [SCOPE: environmental] JDK 21 sistemde YOK. Temurin 21.0.11:
   `/private/tmp/claude-501/-Users-hakansoysal-Desktop-ClaudeCode-Denemeler-SpringBoot-Template/eb7a559a-6022-43dc-b5cd-8073348492aa/scratchpad/jdk21/jdk-21.0.11+10/Contents/Home`
   HER java/mvn öncesi AYNI Bash çağrısında export; ilk-iş `ls "$JDK/bin/java"`. /private/tmp — reboot'ta silinebilir, yoksa Adoptium'dan kur + yolu güncelle (escalations/2026-07-03T10-45-jdk21.md).
8. [SCOPE: process] gen-spring mvn koşumlarında `-am` ZORUNLU (gen-core .m2'de garanti değil; T3.7 executor'ı bir kez `mvn -pl gen-core -am install` koştu ama buna GÜVENME, -am kullan). Push yalnız origin main'e, PM kararıyla, milestone checkpoint'lerinde. **DİKKAT:** otomatik push izin sınıflandırıcısınca REDDEDİLDİ; kullanıcı açık onayı veya `! git push origin main` gerekir. Origin M2 checkpoint'ten (05bb4d5) sonra push EDİLMEDİ.
9. [SCOPE: scaffolding; retire-at: T4.3 PASS] **T4.3 (@trigger) executor'ı @trigger.{name} için report.realized(...) ÇAĞIRMAMALI.** T3.7 (9d836ca) tüm ext ns'lerini @trigger dahil ExtPartial paritesiyle (DotnetEmitter:1145) zaten realize ediyor; .NET TriggerPartial (:1105-1123) yalnız Policy('trigger-wiring') üretir, Realized çağırmaz. T4.3 yalnız TriggerBase + human seam + wiring bean + policy('trigger-wiring') üretmeli. UYARI: T4.3 task metni ~satır 245 literal 'realized(@trigger.{name})' diyor — .NET paritesiyle ÇELİŞİR, bu kuralla EZİLİR (note deseninin aynısı). Kaynak-teyit: verifier-reports/T3-7-attempt-1.json.
10. [SCOPE: process] Executor verimlilik: uzun analiz turları API stream-stall'a yakalanıyor (T3.5'te 4×). Executor prompt'una ekle: advisor çağırma; okumadan sonra doğrudan yaz; işi küçük turlara böl (kod→test→mvn→commit). Kesintide SendMessage ile agentId'ye "kaldığın yerden devam et" resume; kod kaybı yoksa fresh spawn.

## Known follow-up notes (doc-only, non-blocking)

- **tasks/T3-5-predicate.md §5.1** 'o da yoksa Double varsay' → 'Decimal/BigDecimal varsay' düzeltilmeli (kendi parantezi + §5.4 + CoreTemplate1 son-çare decimal ile çelişiyor; kod doğru, belge yanıltıcı). verifier-reports/T3-5-attempt-2.json.
- **tasks/IMPLEMENTATION-PLAN.md T4.3 ~satır 245** 'realized(@trigger.{name})' — .NET paritesiyle çelişir (rule 9); belge düzeltilmeli.
- Provenance alan adı `clazz` vs .NET `Class` — byte-eşit cross-runtime tüketici yok; C#-golden eklenirse yeniden değerlendir.
- {ns}-realization policy açıklaması 'annotation/interceptor' vs .NET anchor 'interceptor/attribute' — pinlenmemiş, benign (T3-7 verifier).

## Executor/Verifier spawn deseni

- Executor model sonnet, verifier opus (her zaman). Prompt: executor-prompt.md/verifier-prompt.md + Active rules 1-10 verbatim GLOBAL CONTEXT'e (T4.3 spawn'ında rule 9 dahil).
- Akış: executor DONE → subagent-reports/*.json → opus verifier → verifier-reports/*.json → PASS ise TaskUpdate completed + status.json. Milestone bitince milestone verifier + RESUME güncelle + PM checkpoint commit.

## State files of record

- .pm-state/status.json — M0-M3 completed; M4 T4.1 in_progress. open_observations: rule 1-10 (rule 9 scaffolding retire-at T4.3, rule 10 process).
- .pm-state/verifier-reports/ — T2-3,T2-4,M2,T3-1..T3-7,M3 (13 verdict).
- .pm-state/p2p3-footprint-karar.md — M4 sıralı gerekçesi.
- .pm-state/escalations/ — 3 (jdk21, parallelism 12:30, parallelism-m3m4 16:20).
- TaskList: #1-9 completed, #10(T4.1) in_progress, #11-25 pending.
- Model: kullanıcı oturum default'u Opus 4.8; executor spawn'ları YİNE sonnet (kullanıcı direktifi) — aksi istenmedikçe koru.
