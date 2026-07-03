# RESUME — techgen-spring PM checkpoint

_Yazılma: 2026-07-03 (resume session — M2 kapanışı)_
_Run: 2026-07-03T10-41-58Z-tasks · retry=3 · executor=Sonnet · verifier=Opus_
_Tests passing: 116 (gen-core 104, gen-spring 10, gen-cli 1, conformance 1) — kökten mvn test exit 0_

## Durum özeti

| Milestone | Durum |
|---|---|
| M0 (T0.1, T0.2) | ✅ PASS (milestone verifier dahil) |
| M1 (T1.1, T1.2, T1.3) | ✅ PASS (milestone verifier dahil) |
| M2 (T2.1-T2.4) | ✅ PASS (milestone verifier dahil; T2.3∥T2.4 worktree penceresi başarılı, merge 7c1e8ef) |
| M3-M10 | bekliyor |

Retry-FAIL sayısı: 0. Tüm task'lar ilk denemede PASS.

## Paralellik rejimi (güncel)

- Onaylı paket (12:30 escalation): ~~T2.3∥T2.4~~ (tamamlandı), T6.2∥T6.3 (worktree), T9.2∥T9.3, M8∥M6-M7 (worktree, graf sapması onaylı).
- **M3/M4 içi (16:20 escalation — kullanıcı direktifi "arttırabiliyorsak arttıralım", soru 60s yanıtsız → PM kararı):** pencere-bazlı — P2 (T3.3-T3.5) ve P3 (T4.1-T4.4) açılırken PM dosya-ayak-izi analizi yapar; ayrık dosyalu alt-kümeler worktree ile paralel, SpringEmitter.java çakışanları sıralı, şüphede sıralı. Her merge sonrası ana ağaçta tam re-verify. İlk çatışmalı merge FAIL'inde o pencere otomatik sıralıya döner.

## SIRADAKI ADIM (resume eden PM için)

1. **T3.1** (emit infra — tasks/T3-1-emit-infra.md) → **T3.2** (app iskeleti) SIRALI.
2. T3.2 PASS sonrası: P2 penceresi (T3.3/T3.4/T3.5) için dosya-ayak-izi analizi → paralel/sıralı kararı.
3. T3.6 → T3.7 → M3 milestone verifier → M4 (P3 penceresi aynı analizle) → M5 → M6/M7 ∥ M8(worktree) → M9 (T9.1 → T9.2∥T9.3) → M10.

## Active standing rules

1. [SCOPE: architectural] CoreTemplate1 (/Users/hakansoysal/Desktop/ClaudeCode Denemeler/CoreTemplate1) READ-ONLY; okunur, ASLA yazılmaz.
2. [SCOPE: process] "Derleniyor/testler geçiyor" yalnız GERÇEK mvn çıktısıyla (exit code gözlendi).
3. [SCOPE: process] Golden yalnız UPDATE_GOLDEN=1 + task raporunda diff gerekçesiyle güncellenir.
4. [SCOPE: architectural] SPEC sapması → DUR + raporla (PM müşteriye eskale eder); sessiz sapma yok.
5. [SCOPE: process] Executor insan-okur raporunu tasks/raporlar/T{X}-{Y}.md'ye yazar (plan §4 — plan klasörüne izinli TEK yazma).
6. [SCOPE: process] tasks/_uyumluluk-raporu-2026-07-03.md §7 kararları bağlayıcı: controller kaydı TAM-AÇIK @Bean (scan yok); Generation Gap / parent-POM / h2 onaylı.
7. [SCOPE: environmental] JDK 21 sistemde YOK. Temurin 21.0.11 şurada:
   `/private/tmp/claude-501/-Users-hakansoysal-Desktop-ClaudeCode-Denemeler-SpringBoot-Template/eb7a559a-6022-43dc-b5cd-8073348492aa/scratchpad/jdk21/jdk-21.0.11+10/Contents/Home`
   HER java/mvn komutu öncesi aynı Bash çağrısında JAVA_HOME+PATH export edilir.
   **DİKKAT (resume):** /private/tmp altında — reboot'ta silinmiş olabilir. Yoksa Adoptium'dan yeniden kur ve bu yolu güncelle (escalations/2026-07-03T10-45-jdk21.md).
8. [SCOPE: process] Push yalnız https://github.com/Hakan-Soysal/codegen-springboot-plugin origin main'e, PM kararıyla (milestone checkpoint'lerinde). Başka remote YOK.

## Devam eden gözlemler (bloklamayan)

- GmBuilderTest'te bayat yorumlar ("until T2.2 / daima empty") — housekeeping adayı (T2.2 verifier notu).
- **T2.4 plan-metni niti (doc-only):** M2 T2.4 DoD'un literal komutu `mvn -q -pl gen-spring test` `-am`'siz standalone FAIL eder (gen-core .m2'de kurulu değilken) — reactor artefaktı, kod kusuru değil. Gelecek gen-spring task'larında `-am` kullanılmalı.
- **Provenance alan adı `clazz`** (Java reserved-word kaçışı) vs .NET `Class` — JSON anahtarı farklı; byte-eşit cross-runtime tüketici YOK (golden = emit-snapshot.txt). C#-üretimli provenance.json byte-golden'i eklenirse yeniden değerlendir (T2.4 verifier kaydı).
- Verifier raporları bu session'dan itibaren .pm-state/verifier-reports/ altında (T2-3, T2-4, M2-milestone mevcut).

## Workspace sağlık kontrolü (resume'da koş)

- Rule-7 JAVA_HOME ile kökten `mvn test` → exit 0, ~116 test beklenir.
- `git log --oneline`: son commit'ler 7c1e8ef (merge T2.4), 5e44d5f, 1543b0c, fd1614b...
- Remote: origin = https://github.com/Hakan-Soysal/codegen-springboot-plugin (M2 checkpoint'iyle push edilecek/edildi).
