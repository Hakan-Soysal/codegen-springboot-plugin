# Pinlenmiş sürümler

Bu dosya, üreteç repo'sunun kendi build zincirinde kullanılan araç/kütüphane sürümlerini ve
doğrulama kaynaklarını kayıt altına alır. Her task kendi eklediği sürümü ilgili bölüme ekler.

## T0.1 — Maven multi-module iskeleti + git init

| Bileşen | Sürüm | Doğrulama kaynağı |
|---|---|---|
| JDK (derleme/test hedefi) | **21** (`maven.compiler.release=21`) | SPEC §2, §4 |
| JDK — gerçek çalıştırma ortamı | Eclipse Temurin **21.0.11+10** (LTS) | `java -version` çıktısı (aşağıda) |
| Maven | Apache Maven 3.9.10 | `mvn -version` çıktısı (aşağıda) |
| Jackson BOM | 2.19.0 | Maven Central `solrsearch` sorgusu (`com.fasterxml.jackson:jackson-bom`), tüm sürümler çekilip (rows=50) semantik-versiyon sırasına göre (timestamp değil) azalan sıralandı; en güncel stabil (milestone/RC hariç) |
| JUnit 5 BOM | 5.12.2 | Maven Central `solrsearch` sorgusu (`org.junit:junit-bom`), tüm sürümler çekilip (rows=50) incelendi; 5.13.0 yalnız M1-M3 (milestone) olarak yayınlanmış, stabil değil — en güncel stabil budur |
| maven-surefire-plugin | 3.5.6 | Maven Central `solrsearch` sorgusu (`org.apache.maven.plugins:maven-surefire-plugin`), en güncel stabil (milestone/RC hariç) |

### ÖNEMLİ — JDK kurulum durumu (dürüst kayıt)

Bu makinede sistem genelinde kurulu JDK'lar arasında **21.x YOKTUR** (kurulu olanlar: 8, 11, 17,
19.0.2, 22.0.1, 23 — bkz. `/usr/libexec/java_home -V`). Bu task'ın (ve bu repodaki tüm sonraki
`mvn`/`java` komutlarının) JDK 21 gereksinimini karşılaması için, PM tarafından **scratchpad'e**
lokal bir Eclipse Temurin 21.0.11+10 dağıtımı indirilip açılmıştır. Bu JDK, projeye veya sisteme
kalıcı kurulum olarak **eklenmemiştir** — yalnızca oturuma özel scratchpad dizininde durur ve
kalıcı değildir (oturum kapanınca kaybolabilir).

Bu depodaki (`techgen-parent`) herhangi bir `mvn`/`java` komutu koşulmadan önce **mutlaka** aşağıdaki
gibi `JAVA_HOME` bu scratchpad JDK'sına işaret ettirilmelidir; aksi halde varsayılan `java`/`mvn`
sistemde kurulu başka bir JDK'ya (örn. 23 veya Homebrew 24) düşer ve `maven.compiler.release=21`
ile derleme/test hâlâ çalışabilir ama "JDK 21 ile doğrulandı" iddiası gerçek dışı olur:

```bash
export JAVA_HOME='<scratchpad>/jdk21/jdk-21.0.11+10/Contents/Home'
export PATH="$JAVA_HOME/bin:$PATH"
```

Kalıcı bir JDK 21 kurulumu (örn. `brew install --cask temurin@21` veya resmi Adoptium paketi)
henüz bu makineye YAPILMAMIŞTIR; bu, PM/kullanıcı tarafında ayrıca ele alınması gereken bir
altyapı notudur.

### Doğrulama çıktıları (bu JAVA_HOME altında koşuldu)

```
$ java -version
openjdk version "21.0.11" 2026-04-21 LTS
OpenJDK Runtime Environment Temurin-21.0.11+10 (build 21.0.11+10-LTS)
OpenJDK 64-Bit Server VM Temurin-21.0.11+10 (build 21.0.11+10-LTS, mixed mode, sharing)

$ mvn -version
Apache Maven 3.9.10 (5f519b97e944483d878815739f519b2eade0a91d)
Maven home: /opt/homebrew/Cellar/maven/3.9.10/libexec
Java version: 21.0.11, vendor: Eclipse Adoptium, runtime: <scratchpad>/jdk21/jdk-21.0.11+10/Contents/Home
Default locale: en_TR, platform encoding: UTF-8
OS name: "mac os x", version: "26.3", arch: "aarch64", family: "mac"
```

## T0.2 — Fixture'lar + sürüm pinleme

**Context7 erişimi:** Context7 MCP bu oturumda erişilebilirdi (`resolve-library-id` çağrıldı).
Ancak Spring Boot için döndürdüğü sürüm listesi (`v3.5.3`, `v3.5.9`, ...) Maven Central'daki
gerçek yayın kümesiyle karşılaştırıldığında EKSİK çıktı (Maven Central'da 3.5.16'ya kadar yayın
var — bkz. aşağı). Context7'nin doküman-odaklı indexi patch-seviyesi tam sürüm listesi için
güvenilir bulunmadığından, plan metnindeki fallback maddesi uyarınca (executor Context7'ye
erişemezse Maven Central kullanır) burada da nihai doğrulama kaynağı olarak **Maven Central
`maven-metadata.xml`** (repo1.maven.org, groupId/artifactId bazlı, otoritatif yayın listesi)
kullanılmıştır — Context7 yalnız ön-tarama için kullanıldı.

| Bileşen | Sürüm | Doğrulama kaynağı |
|---|---|---|
| Spring Boot (3.5.x en güncel patch) | **3.5.16** | `https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml` — `<versions>` listesinde en yüksek `3.5.*` girdisi (liste 3.5.0..3.5.16, ardından 4.0.0-M1... ile devam ediyor); `lastUpdated=20260625105826`. Context7 `resolve-library-id("Spring Boot")` ön-taramada `v3.5.3`/`v3.5.9` gösterdi ama bu liste eksikti, bu yüzden Maven Central esas alındı. |
| Jackson BOM | **2.22.0** | `https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/maven-metadata.xml` — `<latest>`/`<release>` = 2.22.0 (T0.1'de 2.19.0 pinlenmişti; bu, T0.1'in solrsearch sorgusunun o an eksik/stale döndüğünü gösteriyor olabilir — bkz. not aşağıda). |
| JUnit 5 BOM | **5.14.4** | `https://repo1.maven.org/maven2/org/junit/junit-bom/maven-metadata.xml` — bu artefaktın kök `<latest>`/`<release>` etiketi artık **6.1.1** (JUnit 6 yayınlanmış); SPEC açıkça **JUnit 5** istediğinden (§9), `<versions>` listesi `5.` önekiyle filtrelendi ve en yüksek stabil (milestone/RC hariç) sürüm alındı: 5.14.4 (T0.1'de 5.12.2 pinlenmişti). |
| build-helper-maven-plugin | **3.6.1** | `https://repo1.maven.org/maven2/org/codehaus/mojo/build-helper-maven-plugin/maven-metadata.xml` — `<latest>`/`<release>` = 3.6.1. |
| maven-shade-plugin | **3.6.2** | `https://repo1.maven.org/maven2/org/apache/maven/plugins/maven-shade-plugin/maven-metadata.xml` — `<latest>`/`<release>` = 3.6.2. |

**Not (bilgi amaçlı, bu task'ın kapsamı DIŞINDA — düzeltme yapılmadı):** Yukarıdaki Maven Central
`maven-metadata.xml` sorguları, T0.1'de pinlenen Jackson BOM (2.19.0) ve JUnit 5 BOM (5.12.2)
sürümlerinin şu an güncel olmadığını gösteriyor (gerçek en güncel: Jackson BOM 2.22.0, JUnit 5 BOM
5.14.4). T0.2'nin "Dosyalar" listesi yalnız `fixtures/*.json` ve `docs/surumler.md`'yi kapsıyor;
kök/gen-core `pom.xml` içindeki fiili bağımlılık sürümlerini değiştirmek bu task'ın kapsamında
değil. Bu, PM/kullanıcıya bilgi amaçlı bir bulgu olarak raporlanır — gerekirse ayrı bir task ile
güncellenmelidir.

## T8.1 — Conformance: Spec DTO + SpecRunner + GeneratedApp bootstrap

| Bileşen | Sürüm | Doğrulama kaynağı |
|---|---|---|
| spring-context / spring-beans (conformance modülü) | **Spring Boot 3.5.16 BOM'undan çözülür** (literal spring-context sürümü yok) | Kök `pom.xml`'e eklenen `spring-boot-dependencies:3.5.16` (scope=import) — YENİ sorgu YAPILMADI; bu, T0.2'de zaten Maven Central `maven-metadata.xml` ile doğrulanmış `Versions.SPRING_BOOT` (gen-spring) pinidir; conformance'ın Spring sürümü, üretilen app'in `spring-boot-starter-parent` sürümüyle KASITLI OLARAK aynı BOM'dan gelir (uyumluluk garantisi — instanceof/classloader paylaşımı için gerekli). |

**Not:** `conformance/pom.xml` yalnız `spring-context`+`spring-beans` (hafif çekirdek; task §5.1 —
`spring-boot-starter` DEĞİL) + mevcut `jackson-databind` (kök jackson-bom 2.22.0'dan) ekler.
Kök `pom.xml`'de jackson-bom/junit-bom import'ları `spring-boot-dependencies` import'undan ÖNCE
sıralanmıştır (Maven'de aynı GA için ilk `dependencyManagement` girdisi kazanır) — bu, T0.2'nin
pinlediği Jackson/JUnit sürümlerinin `spring-boot-dependencies`'in kendi yönettiği (muhtemelen
daha eski) Jackson/JUnit sürümleriyle EZİLMEDİĞİNİ garanti eder; `mvn test` (tüm reactor, 282 test)
gerçek çalıştırmayla doğrulandı, sürüm çakışması gözlenmedi.
