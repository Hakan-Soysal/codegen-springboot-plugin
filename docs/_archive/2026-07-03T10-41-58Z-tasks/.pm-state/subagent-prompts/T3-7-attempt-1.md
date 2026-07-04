# T3.7 attempt 1 — executor prompt (2026-07-03, ANA AĞAÇ, kısa-format)

Model: sonnet · general-purpose · background · MAIN (HEAD=3d5550c)

Şablon: executor-prompt.md uyarlaması (kısa-format, spec IMPLEMENTATION-PLAN.md §M3 T3.7) + Active rules 1-8 verbatim + VERİMLİLİK NOTU (advisor yok, küçük turlar).
Kritik hatırlatmalar: note realized T3.6'da yapıldı — TEKRAR YOK (çift entry); @http ns → AYRICA policy("http-binding") (WriteAuditLog @http.endpoint canlı örnek); throws resultType→Result alt-tip eşlemesi; HandlerBase gen-owned → 'doldurulacak' marker KOYMA. DoD: üretilen app mvn compile exit 0. Commit "T3.7: HandlerBase tamamlayıcıları — Auth/Throws/Consistency/Ext".
