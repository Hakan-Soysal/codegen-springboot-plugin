# Escalation — T6.3 studyo E2E testi 3 gerçek SpringEmitter bug'ı buldu

- **Zaman:** 2026-07-04 (resume session)
- **Case:** (d) — implementasyon planın kapsamadığı bir şey gerektiriyor (tamamlanmış M3 kodunda latent defektler).
- **Bağlam:** T6.3 (E2E compile + studyo smoke) worktree'de (branch t6-3-e2e, commit 25a2edc, RED test — main'e MERGE EDİLMEDİ). Executor testi doğru yazdı; SpringEmitter'a DOKUNMADI (kapsam dışı, doğru davranış); kararı PM'e bıraktı.

## Bulgu

Studyo fixture (fixtures/studyo.manifest.json) emit → 239 dosya, 148ms, **silentDrop=0** (INV-7 temiz), AMA gerçek `mvn compile` **exit 1** — 3 kök-neden SpringEmitter defekti:

1. **Cross-package `import app.Unit` eksik** — Result<Unit> dönen op'larda Unit tipi import edilmiyor. (Kaynak: T3.3 Result/Types veya T3.6 slice import mantığı.)
2. **Cross-package shared-enum import eksik** — app.shared'daki paylaşılan enum'lar (SessionStatus/AppointmentStatus) başka paket entity'lerinde import edilmeden kullanılıyor. (Kaynak: T3.3 Types veya T3.4 entity import.)
3. **PackageInvariants LocalDate için geçersiz `>=`** — 'endsAt >= startsAt' üretiliyor; Java'da LocalDate için `>=` derlenmez (compareTo/isBefore/isAfter gerekir). (Kaynak: T3.5 JavaPredicateRenderer — Decimal→compareTo yapıyor ama temporal/LocalDate→compareTo yapmıyor; T3.5'te bir tip-boşluğu.)

## Neden M3/M4 kaçırdı

Invoice fixture bu üç yolu hiç egzersiz etmedi: Result<Unit> dönen cross-package op yok, cross-package shared enum yok, LocalDate invariant yok. M4'ün 0-silentDrop DoD'u CENSUS kapsamıdır, DERLENEBİLİRLİK değil — bu yüzden 0 silentDrop bu bug'ları yakalamadı. T6.3 studyo E2E'si tam da bu genişletilmiş kapsamı test ediyor.

## No-defer prensibi

Kullanıcı CLAUDE.md: "Yarım iş = yapılmamış iş"; "Validator hata veriyorsa döngüden çıkma — fix et, 0 error olana kadar". Bu defektler üreteç'in geçerli bir manifest için derlenmeyen kod üretmesi demek — parite hedefiyle çelişir, fix edilmeli.

## Karar (kullanıcıya soruldu — 60s yanıt yok → PM no-defer default'u)

Kullanıcı AskUserQuestion'a 60s içinde yanıt vermedi. Kullanıcının CLAUDE.md no-defer prensibi ("Yarım iş = yapılmamış iş"; "fix et, 0 error olana kadar devam") ile önerilen seçenek birebir uyumlu → **Seçenek A otomatik seçildi**:

1. Odaklı 🔴 bug-fix task (T6.3-FIX) aç: 3 defekti SpringEmitter'da düzelt.
2. Doğrula: studyo emit → mvn compile exit 0; invoice golden ETKİLENMEDİ (invoice bu 3 yolu egzersiz etmiyor — bug'ların latent kalma nedeni; golden değişmemeli) VEYA değiştiyse UPDATE_GOLDEN=1 + gerekçe (rule 3); tüm mevcut testler yeşil.
3. Opus verifier onaylar.
4. Sonra T6.3 regresyon testini (branch t6-3-e2e commit 25a2edc) main'e merge et → artık yeşil → T6.3 verifier → M6 milestone verifier.

**SIRALAMA:** Bug-fix SpringEmitter'a dokunuyor; T6.2 ŞU AN ana ağaçta (yeni test dosyaları). İki agent'ın main index'inde yarışmaması için bug-fix, T6.2 land + verify OLDUKTAN SONRA main'de spawn edilecek. T6.2 emit davranışını değiştirmiyor (test portu); bug-fix invoice emit çıktısını değiştirmemeli (studyo-özel yollar) → ikisi disjoint, çakışmasız.

Kullanıcı dönerse ve farklı isterse (kendi incelemesi / erteleme) plan revize edilir; şimdilik T6.3 worktree korunuyor (RED test main'e girmedi).
