# T6.3-FIX attempt 1 — bug-fix executor prompt (2026-07-04, ANA AĞAÇ, plan-dışı)

Model: sonnet · general-purpose · background · MAIN (HEAD=d608549)

Kaynak: escalations/2026-07-04T-t6-3-studyo-bugs.md (T6.3 studyo E2E'sinin bulduğu 3 gerçek SpringEmitter defekti; kullanıcı 60s yanıt vermedi → no-defer default seçenek A).
3 defekt: (1) cross-package app.Unit import, (2) cross-package shared-enum import, (3) JavaPredicateRenderer temporal (LocalDate/LocalDateTime/Instant) → compareTo (Decimal deseninin aynısı; Java'da temporal için >= derlenmez).
Kritik golden protokolü (rule 3): fix sonrası CharacterizationTest — yeşilse invoice değişmedi (beklenen, bug'lar studyo-özel), dokunma; kırmızıysa diff incele + doğruysa UPDATE_GOLDEN=1 + satır-satır gerekçe (sessiz değişiklik YASAK).
DoD: studyo compile exit 0 + invoice compile exit 0 + kökten 263 test yeşil. commit "T6.3-FIX: studyo derleme bug'ları — cross-package import + LocalDate compareTo".
