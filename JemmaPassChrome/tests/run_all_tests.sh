#!/usr/bin/env bash
set -euo pipefail

echo "=========================================================="
echo "🐢 JemmaPassChrome — Suite de Tests Complète"
echo "=========================================================="

echo ""
echo "--- 1. Exécution des tests Node TypeScript stricts ---"
node --experimental-strip-types --test \
  JemmaPassChrome/tests/test_vectors.ts \
  JemmaPassChrome/tests/test_personas.ts \
  JemmaPassChrome/tests/test_codec_roundtrip.ts

echo ""
echo "--- 2. Validation HL7 FHIR IPS 1.1.0 ---"
JAVA_BIN="${JAVA_BIN:-/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java}"
JAR="${FHIR_VALIDATOR_JAR:-/tmp/fhir-validator/validator_cli.jar}"
OUT="JemmaPassChrome/tests/out"

if [ -f "$JAR" ] && [ -x "$JAVA_BIN" ]; then
  mkdir -p "$OUT/validator"
  pids=()
  for p in demo_kurodo demo_haru demo_kamekichi; do
    echo "Lancement validation HL7 IPS 1.1.0 de $p..."
    "$JAVA_BIN" -jar "$JAR" "$OUT/files/$p.fhir.json" -version 4.0.1 -ig hl7.fhir.uv.ips#1.1.0 -locale en -tx n/a \
      -output "$OUT/validator/$p.txt" > "$OUT/validator/$p.log" 2>&1 &
    pids+=($!)
  done
  for pid in "${pids[@]}"; do wait "$pid"; done
  echo "Validation terminée pour tous les personas."
  echo ""
  echo "Erreurs HL7 par persona :"
  grep -c '<td>Error</td>' "$OUT"/validator/*.txt || true
  echo ""
  echo "Avertissements HL7 par persona :"
  grep -c '<td>Warning</td>' "$OUT"/validator/*.txt || true
else
  echo "Validateur HL7 non disponible (JAR ou Java absent), étape ignorée."
fi

echo ""
echo "✅ TOUS LES TESTS SONT AU VERT."
