# T5.1 attempt 1 — executor prompt (2026-07-03, ANA AĞAÇ, 🔴 tam-format, M5)

Model: sonnet · general-purpose · background · MAIN (M4 kapandı)

Şablon: executor-prompt.md + Active rules 1-9 verbatim (rule 8/9 RETIRED — taşınmadı) + VERİMLİLİK NOTU.
Görev: GenConfig (dbProvider whitelist) + techgen.cli.Main (arg parse + emit pipeline sürücü + INV-7 exit-code) + shaded jar (maven-shade, sürüm docs/surumler.md'den). M5 DoD: GERÇEK `java -jar gen-cli/target/<jar> fixtures/manifest.json <out>` exit 0 + build-report/provenance yazılmış + üretilen app compile. Shaded jar'ı build edip java -jar ile koşmadan "çalışıyor" deme. commit "T5.1: GenConfig + CLI main + exit-code + shaded jar".
