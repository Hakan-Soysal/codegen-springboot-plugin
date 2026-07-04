# T7.1 attempt 1 — executor prompt (2026-07-04, ANA AĞAÇ, 🔴 tam-format, M7 ∥ M8 worktree)

Model: sonnet · general-purpose · background · MAIN (M6 kapanışı)

Şablon: executor-prompt.md + Active rules 1-8 verbatim (rule 3 GOLDEN-BÜYÜTME vurgulu) + VERİMLİLİK NOTU.
Görev: TestPlan→JUnit emisyonu (gen/test-java iskeletleri + Fixture harness + ARRANGE seam WriteIfAbsent+marker; Single-dışı prereq→Unsupported("test-prereq")). GOLDEN'I BÜYÜT: UPDATE_GOLDEN=1 ile yeniden üret (T6.1 golden test iskeleti içermiyordu, T6.1 verifier T7.1'in büyüteceğini onayladı); yeni dosyaları raporda gerekçelendir. Koordinasyon: M8 (T8.1) eşzamanlı worktree'de (conformance modülü), disjoint; SpringEmitter'ı sen düzenliyorsun. commit "T7.1: TestPlan→JUnit emisyonu + fixture harness + ARRANGE seam".
