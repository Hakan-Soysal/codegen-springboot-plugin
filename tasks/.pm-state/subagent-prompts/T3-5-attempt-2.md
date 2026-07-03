# T3.5 attempt 2 — executor prompt (2026-07-03, ANA AĞAÇ)

Model: sonnet · general-purpose · background · MAIN (worktree YOK — attempt 1 worktree'si 2× API kesintisi sonrası boş silindi)

Şablon: executor-prompt.md + Active rules 1-8 verbatim. Task: tasks/T3-5-predicate.md.
Değişiklik: worktree yerine doğrudan main (paralel komşu kalmadı — T3.3/T3.4 bitti). git worktree yasak. JDK21 yolu ilk-iş ls kontrolü eklendi (reboot riski). Commit "T3.5: JavaPredicateRenderer + Guards + Invariants"; .pm-state + settings.local.json stage yasak; push yok.
Çıktı dosyaları: yeni JavaPredicateRenderer.java + PredicateRenderTest + GuardsEmitTest + SpringEmitter'a Guards/Invariants metotları.
