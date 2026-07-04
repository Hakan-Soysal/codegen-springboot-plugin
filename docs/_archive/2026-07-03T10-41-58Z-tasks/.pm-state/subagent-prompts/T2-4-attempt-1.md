# T2.4 attempt 1 — executor prompt (resume session, 2026-07-03)

Model: sonnet · subagent_type: general-purpose · background

Şablon: references/executor-prompt.md uyarlaması (kısa-format task), doldurulan alanlar:
- TASK ID: T2.4 · TASK SPEC: IMPLEMENTATION-PLAN.md "#### T2.4 🟢 — Provenance IO" bölümü + §0 · attempt 1/3
- ACTIVE STANDING RULES: RESUME.md Active rules 1-8 verbatim (rapor yolu T2-4.md olarak)
- WORKTREE İZOLASYONU: `git worktree add -b t2-4-provenance <session-scratchpad>/wt-t2-4 main`; TÜM iş worktree'de; mvn `-pl gen-spring -am` (fresh worktree'de -am şart); rapor worktree içindeki tasks/raporlar/T2-4.md; commit "T2.4: Provenance IO" branch'te; MERGE/PUSH/WORKTREE-SİLME YOK — entegrasyon PM'in integrator agent'ında.
- DoD: mvn -q -pl gen-spring -am test exit 0; round-trip + bozuk-dosya-null + ordinal-sıra testleri; git diff --stat yalnız gen-spring/src/** + rapor.
- Dönüş şeması: standart executor JSON + worktree/branch/commit alanları.
