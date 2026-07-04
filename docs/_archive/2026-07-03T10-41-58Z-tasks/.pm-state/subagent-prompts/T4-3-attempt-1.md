# T4.3 attempt 1 — executor prompt (2026-07-03, ANA AĞAÇ, kısa-format, M4 sıralı)

Model: sonnet · general-purpose · background · MAIN (HEAD=5177ceb)

Şablon: executor-prompt.md uyarlaması (kısa-format §M4 T4.3) + Active rules 1-10 verbatim + VERİMLİLİK NOTU.
KRİTİK rule 9 (retire-at T4.3 PASS) tam bu executor'a: @trigger.{name} realize ÇAĞIRMA (T3.7 zaten yapıyor); task:245 literal 'realized(@trigger)' ile çelişir, kural ezer. T4.3 yalnız TriggerBase + human seam + Wiring bean + policy('trigger-wiring'). rule 8 (ctor-SIRA) HandlerBase'e param eklemiyorsa dokunma. DoD: @trigger.cron TriggerBase+seam, 2. emit ezme, compile exit 0, @trigger realize TAM 1 kez (çift-entry testi). commit "T4.3: Trigger (@trigger.* → SmartLifecycle stub + seam)".
