# T4.2 attempt 1 — executor prompt (2026-07-03, ANA AĞAÇ, 🔴 tam-format, M4 sıralı)

Model: sonnet · general-purpose · background · MAIN (HEAD=f0b798d)

Şablon: executor-prompt.md + Active rules 1-8 verbatim + VERİMLİLİK NOTU.
Kritik: M4 CTOR-THREADING kuralı verbatim taşındı (events EventBus, idempotent'ten SONRA; 3 senkron nokta; CROSS-SITE ctor-SIRA assert şart — yanlış sıra derlenir ama arg takas eder; retType yeniden türetme). EventBus/Event T3.3'te üretilmiş; T4.2 emits HandlerBase alanı + Subscription consumer human seam (marker doğru yere). Test sayısını GERÇEK reactor'dan yaz (T4.1 yanlış raporladı). commit "T4.2: EventBus + emits + Subscription consumer + seam".
