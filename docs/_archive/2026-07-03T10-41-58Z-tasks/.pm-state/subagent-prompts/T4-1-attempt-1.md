# T4.1 attempt 1 — executor prompt (2026-07-03, ANA AĞAÇ, kısa-format, M4 ilk)

Model: sonnet · general-purpose · background · MAIN (M3 kapandı)

Şablon: executor-prompt.md uyarlaması (kısa-format §M4 T4.1) + Active rules 1-8 verbatim + VERİMLİLİK NOTU.
M4 SIRALI: T4.1 HandlerBase ctor'a IdempotencyStore param + IDEMPOTENCY_KEYS; ctor-senkron Wiring @Bean çağrısı güncellenmeli; sonraki T4.2/T4.4 aynı ctor bloğuna ekleyecek → bölüm lokal/temiz. Pagination: Result<Page<Ret>> + cursor/size + policies. IdempotencyStore bean EXPLICIT @Bean. commit "T4.1: Idempotency + Pagination".
(rule 9 T4.3-özel; T4.1'e taşınmadı.)
