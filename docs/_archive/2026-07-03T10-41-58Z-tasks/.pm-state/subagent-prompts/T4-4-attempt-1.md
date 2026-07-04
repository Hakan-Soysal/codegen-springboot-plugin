# T4.4 attempt 1 — executor prompt (2026-07-03, ANA AĞAÇ, 🔴 tam-format, M4 sıralı)

Model: sonnet · general-purpose · background · MAIN (HEAD=dd22844)

Şablon: executor-prompt.md + Active rules 1-9 verbatim (rule 9 RETIRED — taşınmadı) + VERİMLİLİK NOTU.
KRİTİK rule 8 (ctor-SIRA): boundary({Ext} client) EN SONA (events'ten sonra); yeni sıra [repos,idempotent,events,boundary]; 3 senkron nokta; cross-site ctor-SIRA assert GÜNCELLE/GENİŞLET (T4.2'nin testini boundary ile). Boundary externals + client seam (marker) + boundary validation (JavaPredicateRenderer T3.5) + Uncharted. commit "T4.4: Boundary externals + client seam + boundary validation + Uncharted".
