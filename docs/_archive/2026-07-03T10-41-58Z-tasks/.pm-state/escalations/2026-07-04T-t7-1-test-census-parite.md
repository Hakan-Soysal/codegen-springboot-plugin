# Escalation — T7.1 #2: 'test' census parite sapması (task metni yanlış premise)

- **Zaman:** 2026-07-04 (resume session)
- **Case:** (b) task spec iç-tutarsız / (c) parite — verifier INCONSISTENT (non-blocking).
- **Bağlam:** T7.1 (test emisyonu, commit 8b67948, main). Fonksiyonel PASS (269 test, golden büyümesi meşru, test-compile exit 0). Tek escalate item: #2.

## Bulgu (verifier kaynak-okumasıyla kanıtladı)

- .NET `DotnetEmitter.cs:235` FİİLEN `report.Realized("test", "{scope}_{name}")` çağırıyor (her all-Single test için).
- T7.1 task §5.2 bunu ÇAĞIRMAMAYI söylüyor ve YANLIŞ parite gerekçesi veriyor: ".NET de test'i census'a saymaz" — bu FAKTÜEL YANLIŞ.
- Executor task metnini izledi (rule 4: task metni bağlayıcı sandı) → Java build-report'ta 0 'test' census entry → .NET'ten sapma.
- Standing rule #4: kaynak (.NET parite) OTORİTER. Proje kilitli kararı: TAM PARİTE.

## Non-blocking gerekçe

Ne Java Gen-Core/conformance ne .NET Gen.Core Completeness 'test' construct'ını TÜKETİYOR — hiçbir gate/exit/conformance buna bağlı değil. Yalnız build-report census içeriği farkı (Java build-report'unu .NET'inkiyle diff'lersen 'test' entry'leri eksik).

## Karar seçenekleri

- **A (parite-restore, önerilen):** emitOneTest'te iskelet+seam yazımından sonra `report.realized("test", folder+"_"+testId)` ekle → build-report census .NET DotnetEmitter.cs:235 ile hizalanır. Küçük değişiklik; golden'ı ETKİLEMEZ (build-report golden'dan metadata filtresiyle dışlı); invoice census 77 aynı kalır (invoice contract processes/flows yok → 0 test). Kilitli "tam parite" + rule #4 ile uyumlu.
- **B (omission'ı onayla):** census'ta 'test' YOK kararını bilinçli kabul et + task §5.2'deki yanlış parite gerekçesini düzelt (belge).

## Yan bulgu (blocking değil)

Executor self-check #3 iddiası ('Unsupported biçimi .NET birebir uyumlu') faktüel yanlış — #5'te id-formatı .NET'ten sapıyor ({scope}_{name} agrege vs per-offender). #5 yine de legitimate (her offender kaydediliyor). Rapor düzeltilebilir (opsiyonel).

## PM kararı (kullanıcıya soruldu — 60s yanıt yok → PM default)

Kullanıcı AskUserQuestion'a 60s içinde yanıt vermedi. Kilitli "tam parite" (proje memory kararı) + rule #4 (.NET parite otoriter) + verifier'ın .NET DotnetEmitter.cs:235 kaynak kanıtı buradaki yönü KESİN belirliyor → **Seçenek A (parite-restore) otomatik seçildi**:

1. Küçük parite-fix executor (T7.1-PARITE): emitOneTest'e report.realized("test", <.NET DotnetEmitter.cs:235'teki {scope}_{name} formatı>) ekle → build-report census .NET ile hizalanır.
2. Doğrula: golden ETKİLENMEZ (build-report golden'dan dışlı), invoice census 77 aynı (invoice 0 test), 269 test yeşil, studyo build-report'ta 'test' entry'leri belirir.
3. Opus verifier onaylar → T7.1 PASS.
4. Follow-up (doc-only, PM plan dosyası editlemez): task §5.2'deki yanlış parite gerekçesi + T7-1.md self-check #3 yanlış iddiası düzeltilmeli.

Kullanıcı dönerse ve B'yi (omission onayla) isterse fix geri alınır; şimdilik parite tam.
