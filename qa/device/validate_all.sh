#!/usr/bin/env bash
set -u
OUT_ARG="${1:?usage: validate_all.sh <run dir or subfolder> [suffix]}"
SUFFIX="${2:-}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAR="${FHIR_VALIDATOR_JAR:-/tmp/fhir-validator/validator_cli.jar}"

latest_out() { ls -dt "$ROOT"/qa/device/out/*/ 2>/dev/null | head -1 | sed 's:/$::'; }
OUT="${OUT:-$(latest_out)}"

if [ -d "$OUT_ARG/files" ]; then
  OUT="$OUT_ARG"
  FILES_DIR="$OUT/files"
elif [ -d "$OUT/$OUT_ARG" ]; then
  FILES_DIR="$OUT/$OUT_ARG"
elif [ -d "$OUT_ARG" ]; then
  FILES_DIR="$OUT_ARG"
  OUT="$(cd "$FILES_DIR/.." && pwd)"
else
  FILES_DIR="$OUT/files"
fi

mkdir -p "$OUT/validator"
pids=()
for p in demo_kurodo demo_haru demo_kamekichi; do
  java -jar "$JAR" "$FILES_DIR/$p.fhir.json" -version 4.0.1 -ig hl7.fhir.uv.ips#1.1.0 -locale en -tx n/a \
    -output "$OUT/validator/$p$SUFFIX.txt" > "$OUT/validator/$p$SUFFIX.log" 2>&1 &
  pids+=($!)
done
for pid in "${pids[@]}"; do wait "$pid"; done
{
  echo "errors:";   grep -c '<td>Error</td>'   "$OUT"/validator/*"$SUFFIX".txt
  echo "warnings:"; grep -c '<td>Warning</td>' "$OUT"/validator/*"$SUFFIX".txt
} | tee "$OUT/validator/summary$SUFFIX.txt"

