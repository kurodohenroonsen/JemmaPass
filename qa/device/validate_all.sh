#!/usr/bin/env bash
set -u
OUT="${1:?usage: validate_all.sh <run dir> [suffix]}"
SUFFIX="${2:-}"
JAR="${FHIR_VALIDATOR_JAR:-/tmp/fhir-validator/validator_cli.jar}"
mkdir -p "$OUT/validator"
pids=()
for p in demo_kurodo demo_haru demo_kamekichi; do
  java -jar "$JAR" "$OUT/files/$p.fhir.json" -version 4.0.1 -ig hl7.fhir.uv.ips#1.1.0 -locale en -tx n/a \
    -output "$OUT/validator/$p$SUFFIX.txt" > "$OUT/validator/$p$SUFFIX.log" 2>&1 &
  pids+=($!)
done
for pid in "${pids[@]}"; do wait "$pid"; done
{
  echo "errors:";   grep -c '<td>Error</td>'   "$OUT"/validator/*"$SUFFIX".txt
  echo "warnings:"; grep -c '<td>Warning</td>' "$OUT"/validator/*"$SUFFIX".txt
} | tee "$OUT/validator/summary$SUFFIX.txt"
