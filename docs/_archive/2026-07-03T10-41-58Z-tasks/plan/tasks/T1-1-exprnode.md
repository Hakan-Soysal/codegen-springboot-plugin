# T1.1 🔴 — ExprNode AST + polymorphic (de)serializer + hata tipleri

## 1. Goal
`techgen.core.model.ExprNode` sealed AST ailesini, Jackson custom (de)serializer'ını ve dört çekirdek
exception tipini gen-core modülüne ekle.

## 2. Why
SPEC §5 + davranış sözleşmesi §3: tüm validation/rule/invariant/permit ifadeleri bu AST'den geçer.
Deserializer'ın ayrımcı sırası ve toleranslılık asimetrisi (bilinmeyen NodeKind toleranslı,
bilinmeyen literal-kind fatal) yanlış yazılırsa parse SESSİZCE farklı davranır — tüm predicate
emisyonu bozulur ama compile geçer (silent-fail riski yüksek). Hata tipleri (LoadError/JoinError/
ModelError/UnsupportedConstruct) sonraki her task'ın sözleşmesidir.

## 3. Inputs (must read fully before editing)
- `SPEC.md` §5 (ExprNode maddesi), §3 INV-4
- `docs/referans/gen-core-davranis-sozlesmesi.md` §3 (AST + deserializer sırası), §8 (hata tipleri)
- **Pattern (READ-ONLY):** CoreTemplate1 `src/Gen.Core/Model/ExprNode.cs` (tam) ve `Errors.cs`
- `fixtures/manifest.json` içindeki `ast` alanları (gerçek örnekler: cmp/path/number)

## 4. Pre-conditions (verify before starting; all must succeed)
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q -pl gen-core test        # expected: exit 0 (T0.1 iskeleti yeşil)
test -f fixtures/manifest.json  # expected: exit 0 (T0.2 done)
```
If any check fails, STOP and report — do not proceed.

## 5. Changes (atomic numbered steps)

### Step 5.1 — Exception tipleri
**File:** `gen-core/src/main/java/techgen/core/errors/` altında 4 dosya
**Action:** Add
```java
public final class LoadError extends RuntimeException { public LoadError(String m){super(m);} }
public final class JoinError extends RuntimeException { public JoinError(String m){super(m);} }
public final class ModelError extends RuntimeException { public ModelError(String m){super(m);} } // rezerve
public final class UnsupportedConstruct extends RuntimeException { public UnsupportedConstruct(String m){super(m);} }
```

### Step 5.2 — AST tipleri
**File:** `gen-core/src/main/java/techgen/core/model/ExprNode.java`
**Action:** Add — sealed interface + record'lar:
```java
public sealed interface ExprNode permits BinaryNode, AggNode, CallNode, PathNode, LiteralNode, DurationNode {}
record BinaryNode(String nodeKind, String op, ExprNode left, ExprNode right) implements ExprNode {}   // op nullable
record AggNode(String fn, List<String> path) implements ExprNode {}
record CallNode(String name, List<ExprNode> args) implements ExprNode {}
record PathNode(List<String> path) implements ExprNode {}
record LiteralNode(String litKind, Object value) implements ExprNode {}    // value: String|Double|Boolean
record DurationNode(double value, String unit, String text) implements ExprNode {}
```
(Her record ayrı public dosya olabilir; paket `techgen.core.model`.)

### Step 5.3 — Custom deserializer
**File:** `gen-core/src/main/java/techgen/core/model/ExprNodeDeserializer.java`
**Action:** Add — `JsonDeserializer<ExprNode>`; ayrımcı sıra AYNEN:
1. `node` alanı varsa: `"agg"`→AggNode(fn,path); `"call"`→CallNode(name, args özyineli);
   **default** → BinaryNode(node, op?, left, right özyineli) — bilinmeyen nodeKind PATLAMAZ.
2. yoksa `path` alanı varsa → PathNode.
3. yoksa `kind` alanı varsa: `"duration"`→DurationNode(value,unit,text); `"string"`→getText;
   `"number"`→getDouble; `"boolean"`→getBoolean; **başka kind → `JsonMappingException` fırlat**.
4. hiçbiri → **`JsonMappingException("unknown ExprNode shape")`**.

### Step 5.4 — Serializer (round-trip)
**File:** `gen-core/src/main/java/techgen/core/model/ExprNodeSerializer.java`
**Action:** Add — her node tipini deserializer'ın okuduğu şekle geri yazar (Binary→`{node,op?,left,right}`,
Agg→`{node:"agg",fn,path}`, Call→`{node:"call",name,args}`, Path→`{path}`, Literal→`{kind,value}`
(değer tipine göre), Duration→`{kind:"duration",value,unit,text}`). `op` null ise alan yazılmaz.

### Step 5.5 — Unit testler
**File:** `gen-core/src/test/java/techgen/core/model/ExprNodeTest.java`
**Action:** Add — en az şunlar:
- cmp/path/number parse (fixture'daki `amount > 0` AST'si birebir string'den)
- and/or nested parse; agg/call/duration parse
- **round-trip kayıpsız**: parse→serialize→parse → eşit (record equality)
- bilinmeyen literal kind (`{"kind":"weird","value":1}`) → exception
- bilinmeyen şekil (`{"foo":1}`) → exception
- **bilinmeyen nodeKind toleransı**: `{"node":"xor","left":...,"right":...}` → BinaryNode("xor",...)

## 6. Acceptance tests (run after; ALL must pass)
### 6.1
```bash
mvn -q -pl gen-core test    # expected: exit 0; ExprNodeTest'in TÜM testleri koştu
```
### 6.2 Pozitif — fixture AST'leri parse oluyor
Test içinde `fixtures/manifest.json`'dan `operations[0].validation[0].ast` düğümünü Jackson tree ile
alıp ExprNode'a deserialize et → `BinaryNode("cmp", ">", PathNode([amount]), LiteralNode("number", 0.0))`.
### 6.3 Negatif — bilinmeyen literal kind
`{"kind":"weird","value":1}` → deserialize exception fırlatıyor (test assert'ü `assertThrows`).

## 7. Out of scope (DO NOT)
- Manifest/Contract POJO'ları — T1.2
- ExprNode→Java predicate render — T2.1 (nötr yürüyüş) + T3.5 (Java render)
- ObjectMapper global yapılandırması — T1.2 (`techgen.core.Json`); bu task'ta test-lokal mapper kullan

## 8. Anti-patterns
- DO NOT `@JsonTypeInfo`/`@JsonSubTypes` kullan — ayrım alan-VARLIĞINA göredir (node/path/kind),
  standart polimorfizm anotasyonlarıyla ifade EDİLEMEZ; custom deserializer şart.
- DO NOT bilinmeyen nodeKind'de exception fırlat — toleranslı BinaryNode sözleşmesi (asimetri kasıtlı).
- DO NOT LiteralNode.value için `BigDecimal` kullan — sözleşme `Double` (number→getDouble); BigDecimal
  dönüşümü render katmanının işi (T3.5).
- DO NOT alan adlarını değiştir (`litKind` JSON'da `kind`tir; `nodeKind` JSON'da `node`) — serializer
  ile deserializer alan adlarında simetrik olmalı.

## 9. Definition of Done
- [ ] Step 5.1: 4 exception sınıfı mevcut
- [ ] Step 5.2: 6 node tipi + sealed interface mevcut
- [ ] Step 5.3: deserializer 4-adımlı ayrımcı sırayla yazıldı
- [ ] Step 5.4: serializer round-trip simetrik
- [ ] Step 5.5: ≥8 unit test
- [ ] 6.1-6.3 koşuldu, çıktılar okundu
- [ ] `git status`: yalnız gen-core altında yeni/değişen dosyalar

## 10. Self-check before reporting done
1. Acceptance komutlarını GERÇEKTEN koştum mu, çıktıyı gözümle okudum mu?
2. Bilinmeyen-nodeKind toleransını POZİTİF testle kanıtladım mı (yalnız fatal yolları değil)?
3. Round-trip testi record-equality ile mi karşılaştırıyor (string karşılaştırması değil)?
4. Out-of-scope dosyalara dokundum mu? (`git status` kontrolü)
5. Spec dışı bir karar verdiysem task raporuna yazdım mı?
