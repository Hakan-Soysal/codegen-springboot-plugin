# T2.3 attempt 1 — executor prompt (resume session, 2026-07-03)

Model: sonnet · subagent_type: general-purpose · background

Şablon: references/executor-prompt.md, doldurulan alanlar:
- TASK ID: T2.3 · TASK FILE: tasks/T2-3-report.md · attempt 1/3
- ACTIVE STANDING RULES: RESUME.md Active rules 1-8 verbatim (CoreTemplate1 read-only; gerçek mvn çıktısı; golden UPDATE_GOLDEN=1; SPEC sapması→DUR; rapor tasks/raporlar/T2-3.md; uyumluluk-raporu §7 bağlayıcı; JDK21 JAVA_HOME export zorunlu; push yok)
- COORDINATION: T2.4 paralel ayrı worktree'de (branch t2-4-provenance) — T2.3 yalnız ana ağaç + gen-core; mvn modül-scope'lu; bitişte yalnız kapsam dosyaları + rapor commit "T2.3: BuildReport + Census + Completeness gate"; .claude/settings.local.json stage'lenmez; push yok.
- Dönüş şeması: standart executor JSON + "commit" alanı.
