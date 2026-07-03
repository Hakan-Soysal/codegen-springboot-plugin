# RESUME — techgen-spring PM checkpoint

_Yazılma: 2026-07-03 ~13:05Z (kullanıcı molası — network kesintisi öncesi checkpoint)_
_Run: 2026-07-03T10-41-58Z-tasks · retry=3 · executor=Sonnet · verifier=Opus · milestone-by-milestone + onaylı paralel paket_

## Durum özeti

| Milestone | Durum |
|---|---|
| M0 (T0.1, T0.2) | ✅ PASS (milestone verifier dahil) |
| M1 (T1.1, T1.2, T1.3) | ✅ PASS (milestone verifier dahil) |
| M2 | T2.1 ✅, T2.2 ✅ — **T2.3 + T2.4 bekliyor**, sonra M2 milestone verifier |
| M3-M10 | bekliyor |

Retry-FAIL sayısı: 0. Tüm task'lar ilk denemede PASS (T0.1 hariç: attempt1 ortam engeli JDK21, attempt3 pin hizalama — ikisi de çözüldü).

## SIRADAKI ADIM (resume eden PM için)

1. **T2.3 (ana ağaç) ∥ T2.4 (git-worktree izolasyonu)** executor'larını TEK mesajda paralel spawn et
   (kullanıcı onaylı paralel paket — bkz. escalations/2026-07-03T12-30-parallelism.md).
   T2.4 worktree'de biter → küçük bir integrator subagent merge eder → verifier'lar SIRALI koşar
   (aynı anda iki mvn ana ağaçta yarışmasın).
2. M2 milestone verifier → M3 (T3.1→T3.7 SIRALI — ortak SpringEmitter.java) → M4 (T4.1→T4.5 SIRALI)
   → M5 (T5.1) → **M6/M7 sırasında M8'i (T8.1→T8.2) worktree'de paralel koş** (onaylı graf sapması)
   → M9 (T9.1 → T9.2∥T9.3) → M10.

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
   **DİKKAT (resume):** Bu yol /private/tmp altında — reboot/temizlikte silinmiş olabilir. Yoksa yeniden kur:
   `curl -L -o /tmp/t21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/mac/aarch64/jdk/hotspot/normal/eclipse"` → yeni scratchpad'e aç → bu kuralın yolunu güncelle. (Kalıcı çözüm istenirse kullanıcıya `brew install --cask temurin@21` öner — escalations/2026-07-03T10-45-jdk21.md.)
8. [SCOPE: process] Kullanıcı push'u onayladı (yalnız https://github.com/Hakan-Soysal/codegen-springboot-plugin origin'ine, main). Başka remote'a push YOK.

## Devam eden gözlemler (bloklamayan)

- GmBuilderTest'te bayat yorumlar ("until T2.2 / daima empty") — işlevsiz dokümantasyon kalıntısı;
  T2.2 verifier'ın önerisi: ileride bir housekeeping dokunuşu + GmBuilder-seviyesi studyo non-empty testi (opsiyonel).
- T1.1 verifier notu (uygulandı, kapandı): Json mapper ExprNode tip-anotasyonlarıyla uyumlu.
- Verifier raporlarının tamamı .pm-state/ altında DEĞİL — bu run'da verdict'ler PM konuşma bağlamında
  toplandı; özet bilgiler status.json + bu dosyadadır. (İyileştirme: sonraki oturum verifier JSON'larını
  verifier-reports/ altına da yazdırabilir.)

## Workspace sağlık kontrolü (resume'da koş)

- Rule-7 JAVA_HOME ile kökten `mvn -q test` → exit 0 beklenir (mola anında: gen-core 80 test dahil tüm reactor yeşil).
- `git log --oneline` ~6+ commit (T0.1×2, T0.2, T1.1×2, T1.2, T1.3, T2.1, T2.2 + checkpoint).
- Remote: origin = https://github.com/Hakan-Soysal/codegen-springboot-plugin (mola commit'iyle push edildi).
