#!/usr/bin/env bash
# Plugin'e gömülü generator (techgen) + conformance JAR'larını src'ten yeniden üretir.
# TEK doğruluk kaynağı: hem CI hem insan bunu çağırır.
# Kullanım: bash scripts/pack-plugin-bundles.sh
set -euo pipefail
cd "$(dirname "$0")/.."

SKILL="plugins/codegen-spring/skills/base-springboot-rest"
TECHGEN="$SKILL/techgen"
CONF="$SKILL/conformance"

mkdir -p "$TECHGEN" "$CONF"

echo "→ mvn -q package (gen-cli + conformance shaded jar'ları)"
mvn -q package

echo "→ refresh $TECHGEN"
rm -f "$TECHGEN"/*.jar
cp gen-cli/target/gen-cli.jar "$TECHGEN/gen-cli.jar"

echo "→ refresh $CONF"
rm -f "$CONF"/*.jar
cp conformance/target/conformance.jar "$CONF/conformance.jar"

echo "✓ bundles refreshed from src"
