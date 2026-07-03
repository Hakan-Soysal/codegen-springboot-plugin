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
