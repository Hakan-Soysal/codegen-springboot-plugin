# P2/P3 dosya-ayak-izi analizi ve paralellik kararı (PM)

- **Zaman:** 2026-07-03 (M2 kapanışı sonrası; mandat: escalations/2026-07-03T16-20-parallelism-m3m4.md)
- **Analiz:** Explore agent, 8 task spec'inin Changes/Dosyalar bölümlerinden çıkarım.

## Bulgular

**P2 (T3.3, T3.4, T3.5):**
- Üçü de SpringEmitter.java'ya AYRIK YENİ metotlar ekler; mevcut metot gövdesi paylaşımı yok.
- Testler ayrı yeni sınıflar (ResultTypesEmitTest / JpaEmitTest / PredicateRenderTest+GuardsEmitTest) — test çakışması yok.
- T3.4 → T3.3 okuma bağımlılığı: entity alanları T3.3'ün emit ettiği enum/composite tipleri kullanır.
- T3.5 en bağımsız: yeni kaynak dosya JavaPredicateRenderer.java; girdileri T2.1 (ExprWalk/TypeEnv) + T3.2 (Naming); T3.3/T3.4 çıktısı OKUMAZ (Guards çağrısı T3.6'ya bırakılmış).

**P3 (T4.1-T4.4):**
- GERÇEK çakışma: T4.1 (idempotency store ctor param), T4.2 (EventBus alanı+ctor), T4.4 (boundary alanı+ctor) üçü de AYNI HandlerBase ctor/field bloğunu ve ctor-senkron Wiring @Bean çağrısını düzenler. T4.3 Wiring bean kaydına dokunur (komşu bölge). Bootstrap @Import listesi T4.2/T4.3/T4.4/T4.5 ortak.
- T4.5 zaten süpürme — T4.4 çıktısına yorum işler, P3'ten sonra tek başına.

## Karar

- **P2: [T3.3 → T3.4] ana ağaçta SIRALI ∥ T3.5 worktree'de PARALEL** (T3.2 PASS ile birlikte açılır).
  Merge sırası: T3.3, T3.4 main'e commit'lenmiş olur; T3.5 branch'i en son merge edilir; integrator çatışmada ÇÖZMEZ → DUR; o durumda T3.5 sıralı yeniden koşulur (mandat: ilk çatışmalı merge FAIL'inde pencere sıralıya döner). Merge sonrası ana ağaçta kökten tam re-verify.
- **P3 (M4): TAMAMEN SIRALI** (T4.1→T4.2→T4.3→T4.4→T4.5) — aynı ctor/field bloğu = "yoğun çakışan"; mandatın "şüphede sıralı" hükmü.

Kazanım: P2 duvar-saatinden ~1/3 (T3.5 ile T3.3+T3.4 örtüşür); P3'te paralellik kazancı yok (çakışma maliyeti > kazanç).
