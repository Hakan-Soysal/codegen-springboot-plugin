#!/usr/bin/env bash
# scripts/e2e-demo.sh — T10.1 uçtan-uca el senaryosu (SPEC.md'nin canlı kanıtı).
#
# Akış: temiz dizine generate (java -jar gen-cli.jar) -> GetInvoice seam'ini örnek impl ile
# doldur -> mvn compile (üretilen app) -> basit bir conformance spec'i konsol runner'ıyla
# (java -jar conformance.jar) koş -> PASS gör.
#
# Standalone çalışır: JDK21 export burada gömülü, dışarıdan JAVA_HOME/PATH gelmese de koşar.
# Golden dosyalara DOKUNMAZ (yalnız geçici bir çalışma dizininde generate/derleme/koşum yapar).
set -euo pipefail

export JAVA_HOME='/private/tmp/claude-501/-Users-hakansoysal-Desktop-ClaudeCode-Denemeler-SpringBoot-Template/eb7a559a-6022-43dc-b5cd-8073348492aa/scratchpad/jdk21/jdk-21.0.11+10/Contents/Home'
export PATH="$JAVA_HOME/bin:$PATH"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

WORK="$(mktemp -d)"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

IN="$WORK/in"
OUT="$WORK/out"
mkdir -p "$IN"

echo "== [1/5] mvn -q package (gen-cli.jar + conformance.jar taze) =="
mvn -q package

GEN_CLI="$ROOT/gen-cli/target/gen-cli.jar"
CONFORMANCE_JAR="$ROOT/conformance/target/conformance.jar"
[ -f "$GEN_CLI" ] || { echo "HATA: $GEN_CLI yok (mvn package başarısız olmuş olmalı)"; exit 1; }
[ -f "$CONFORMANCE_JAR" ] || { echo "HATA: $CONFORMANCE_JAR yok (mvn package başarısız olmuş olmalı)"; exit 1; }

echo "== [2/5] temiz dizine generate (java -jar gen-cli.jar) =="
cp "$ROOT/fixtures/manifest.json" "$ROOT/fixtures/operations.json" "$IN/"
cp "$ROOT/fixtures/gen.config.ornek.json" "$IN/gen.config.json"
java -jar "$GEN_CLI" "$IN/manifest.json" "$OUT"

HANDLER="$OUT/src/main/java/app/billing/getinvoice/GetInvoiceHandler.java"
[ -f "$HANDLER" ] || { echo "HATA: $HANDLER üretilmedi (emitter davranışı değişmiş olabilir)"; exit 1; }

echo "== [3/5] GetInvoice seam'ini örnek impl ile doldur =="
# Bu dosya generate anında writeIfAbsent ile üretilir ve "iş mantığı doldurulacak" marker'ını
# taşıyan bir UnsupportedOperationException gövdesiyle gelir (Generation Gap human seam).
# Burada gerçek bir örnek iş mantığıyla ELLE dolduruyoruz.
cat > "$HANDLER" <<'JAVA'
package app.billing.getinvoice;

import app.Result;
import app.billing.InvoiceRepository;
import app.billing.Invoice;
import app.NotProcessable;
import app.Success;

/**
 * E2E demo seam impl (scripts/e2e-demo.sh). InvoiceRepository'den id ile arar; bulunamazsa
 * NotProcessable("NotFound", ...) döner. GetInvoice.throws==[] (fixtures/manifest.json) —
 * bu kod SPEC'in dayattığı bir sözleşme DEĞİL, yalnız demo amaçlı örnek iş mantığıdır.
 */
public class GetInvoiceHandler extends GetInvoiceHandlerBase {

    public GetInvoiceHandler(InvoiceRepository invoiceRepository) {
        super(invoiceRepository);
    }

    @Override
    public Result<Invoice> execute(GetInvoiceQuery request) {
        return invoiceRepository.findById(request.id())
                .<Result<Invoice>>map(Success::new)
                .orElseGet(() -> new NotProcessable<>("NotFound", "fatura bulunamadi: " + request.id()));
    }
}
JAVA

echo "== [4/5] mvn -q compile (üretilen app) =="
mvn -q -f "$OUT/pom.xml" compile

echo "== [5/5] basit conformance spec'i koş (java -jar conformance.jar) =="
SPEC="$WORK/get-invoice.spec.json"
cat > "$SPEC" <<'JSON'
{
  "construct": "smoke",
  "opId": "GetInvoice",
  "arrange": {},
  "act": { "call": "GetInvoice", "with": { "id": "inv-does-not-exist", "includeVoid": false } },
  "assert": {
    "resultType": "NotProcessable",
    "code": "NotFound",
    "source": "e2e-demo.sh: boş repository + GetInvoice(id=inv-does-not-exist) -> seam impl NotProcessable/NotFound döner"
  }
}
JSON

mvn -q -f "$OUT/pom.xml" dependency:build-classpath -Dmdep.outputFile="$WORK/cp.txt"
APP_CP="$OUT/target/classes:$(cat "$WORK/cp.txt")"

java -jar "$CONFORMANCE_JAR" "$APP_CP" "$SPEC"

echo "== e2e-demo: TAMAM (conformance PASS görüldü) =="
