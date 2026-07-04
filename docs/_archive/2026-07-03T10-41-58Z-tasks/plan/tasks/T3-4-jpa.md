# T3.4 🔴 — Entity→JPA + repository + PersistenceConfig

## 1. Goal
Entity'leri `@Entity` sınıflarına, entity başına `JpaRepository`'ye ve `PersistenceConfig`
(`@EnableJpaRepositories`/`@EntityScan`) emisyonuna bağla; optimistic concurrency → `@Version`.

## 2. Why
AppDbContext'in Java karşılığı. `@EntityScan`/`@EnableJpaRepositories` paket listeleri yanlışsa
context boot ETMEZ ama compile geçer (silent-fail, conformance'a kadar görünmez). sourceOfTruth
alanının FK-yorum davranışı (navigasyon AÇILMAZ) parite kuralıdır.

## 3. Inputs
- `SPEC.md` §6.3 (Entity/Repository/PersistenceConfig satırları), §6.4 (tip eşleme)
- `docs/referans/gen-dotnet-emisyon-sozlesmesi.md` §1 (Entities.g.cs/AppDbContext), §7 (entity/
  concurrency/sourceOfTruth satırları)
- **Pattern (READ-ONLY):** CoreTemplate1 `DotnetEmitter.cs` 755-787 (EntitiesFile), 1189-1212 (DbContextFile)
- T3.2 `SpringEmitter`/`Naming`; T3.3 (types — entity alanları enum/composite tip kullanabilir)

## 4. Pre-conditions
```bash
cd "/Users/hakansoysal/Desktop/ClaudeCode Denemeler/SpringBoot Template"
mvn -q -pl gen-spring test    # expected: exit 0 (T3.3 yeşil)
```
FAIL → STOP + rapor.

## 5. Changes

### Step 5.1 — Entity emisyonu
**File emit:** `gen/java/app/{module}/{EntityId}.java` (writeAlways)
**Action:** şablon: `@Entity @Table(name="{entityId}")` sınıf; alanlar Naming.javaType; `id` alanı →
`@Id`; enum-ref alan → `@Enumerated(EnumType.STRING)`; concurrency=="optimistic" →
`@Version private long version;` + `realized("concurrency", id)`; sourceOfTruth'lu alan → alan üstü
yorum `// sourceOfTruth: {module}.{entity} — cross-module FK; navigasyon AÇILMAZ` +
`realized("sourceOfTruth", "{en}.{f}")`; entity Ext → prelude yorum + `realized("@{ns}.{name}", id)`;
getter/setter'lar (JPA için mutable sınıf — record DEĞİL). → `realized("entity", id)`.
Invariants burada DEĞİL (T3.5).

### Step 5.2 — Repository + PersistenceConfig
**File emit:** `gen/java/app/{module}/{EntityId}Repository.java` — `public interface extends
JpaRepository<{EntityId}, String> {}`. Kök `gen/java/app/PersistenceConfig.java` (yalnız entities>0):
`@Configuration @EnableJpaRepositories(basePackages="app") @EntityScan(basePackages="app")` +
Bootstrap `@Import`'una eklenir.

### Step 5.3 — Testler
**File:** `gen-spring/src/test/java/techgen/spring/JpaEmitTest.java`
- fixture: `Invoice.java` — @Entity, @Id id, @Version, `BigDecimal amount`, `InvoiceStatus status`
  @Enumerated(STRING), `Instant createdAt`.
- `AuditLog.java` — invoiceRef üstünde sourceOfTruth yorumu; navigasyon alanı YOK (grep: `Invoice `
  tipi alan yok).
- `InvoiceRepository` + `AuditLogRepository` + `PersistenceConfig` var; Bootstrap import ediyor.
- build-report: ("entity","Invoice"), ("concurrency","Invoice"), ("concurrency","AuditLog"),
  ("sourceOfTruth","AuditLog.invoiceRef") realized.

## 6. Acceptance tests
### 6.1 `mvn -q -pl gen-spring test` → exit 0.
### 6.2 Pozitif — yukarıdaki içerik assert'leri geçiyor.
### 6.3 Negatif — sourceOfTruth alanında JPA `@ManyToOne`/ilişki anotasyonu OLMADIĞI grep-assert'i
(navigasyon açılmaz kuralı).

## 7. Out of scope (DO NOT)
- Invariants emisyonu — T3.5
- Datasource/provider konfigürasyonu — T3.2 (parent POM) + T5.1 (config)
- Persist ÇAĞRILARI (handler gövdesi) — LLM-doldurucu alanı, üreteç değil

## 8. Anti-patterns
- DO NOT entity'yi record yap — JPA mutable sınıf ister; `@Version` alanı setter'lı olmalı.
- DO NOT `RowVersion`/`byte[]` taklidi — Java'da `@Version long` idiyomu (parite: kavram, temsil değil;
  bu sapma SPEC §6.3'te kayıtlı).
- DO NOT composite type'ları `@Embeddable`'a çevir — SPEC düz record der (T3.3'te üretildiler);
  entity alanı composite tip kullanıyorsa şimdilik String-ID gibi davranma → tip passthrough +
  derleme testine bırak; çözülemiyorsa DUR + PM'e sor.
- DO NOT tablo/kolon adlarını çevirme stratejisi icat et — `@Table(name=entityId)` + default kolon adları.

## 9. Definition of Done
- [ ] Entity + Repository + PersistenceConfig emisyonu ve realized çağrıları mevcut
- [ ] ≥6 test
- [ ] 6.1-6.3 koşuldu
- [ ] `git status`: yalnız gen-spring

## 10. Self-check
1. @Version/@Enumerated/@Id üçlüsünü fixture içeriğiyle mi doğruladım?
2. sourceOfTruth'ta İLİŞKİ AÇMADIĞIMI negatif assert ile kanıtladım mı?
3. PersistenceConfig'in Bootstrap'a import edildiğini test ettim mi?
4. Testleri gerçekten koştum mu?
5. Allowlist dışı dosya var mı?
