#!/usr/bin/env bash
set -u
SRC="$(cd "$(dirname "$0")/.." && pwd)"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
export HOME="$T/home"; mkdir -p "$HOME"
export JP_DIR="$T/jp" JP_REPORTS="$T/dr" JP_MAILBOX="$T/mb"; mkdir -p "$JP_DIR" "$JP_REPORTS" "$JP_MAILBOX"
export OUT="$T/out"; mkdir -p "$OUT/qr" "$OUT/json" "$OUT/validator" "$OUT/logs" "$OUT/screenshots"
pass=0; fail=0
ok() { pass=$((pass+1)); echo "PASS  $1"; }
ko() { fail=$((fail+1)); echo "FAIL  $1"; }

printf '🏥 === JEMMA CLINICAL SUMMARY (FR) ===\n\n💊 [ MÉDICAMENTS ]\n  ▪️ Furosémide 1tab Furosemide 20mg\n' > "$OUT/qr/haru-text-fr.txt"
printf '🏥 === JEMMA 臨床サマリー (JA) ===\n  ▪️ フロセミド' > "$OUT/qr/haru-text-ja.txt"
printf '{\n  "resourceType": "Medication",\n  "code": { "text": "Tisane maison" }\n}\n' > "$OUT/json/haru-freetext.json"
printf 'errors:\ndemo_haru.txt:0\nwarnings:\ndemo_haru.txt:50\n' > "$OUT/validator/summary.txt"
printf 'errors:\ndemo_haru.txt:0\n' > "$OUT/validator/summaryfreetext.txt"
printf '<html>huge validator page</html>\n' > "$OUT/validator/demo_haru.txt"
printf 'SECRET-LOG-LINE\n' > "$OUT/logs/logcat-ui.txt"
printf 'png' > "$OUT/screenshots/001.png"

printf '%s\n' 'report-raw pieces.md' > "$JP_DIR/task.txt"
bash "$SRC/jp.sh" > /dev/null 2>&1; rc=$?
P="$OUT/pieces.md"
[ "$rc" -eq 0 ] && [ -s "$P" ] && ok "RAW-01 report-raw writes the file in the run folder" || ko "RAW-01 report-raw writes the file in the run folder (rc=$rc)"

all=1
for f in qr/haru-text-fr.txt qr/haru-text-ja.txt json/haru-freetext.json validator/summary.txt validator/summaryfreetext.txt; do
  grep -qF "$f" "$P" 2>/dev/null || all=0
done
[ "$all" -eq 1 ] && ok "RAW-02 every qr, json and validator summary file is named" || ko "RAW-02 every qr, json and validator summary file is named"

extract() { python3 - "$P" "$1" <<'PY'
import sys
lines = open(sys.argv[1], encoding="utf-8").read().split("\n")
name = sys.argv[2]
for i, l in enumerate(lines):
    if name in l and l.startswith("#"):
        j = i + 1
        while j < len(lines) and not lines[j].startswith("```"): j += 1
        k = j + 1
        while k < len(lines) and not lines[k].startswith("```"): k += 1
        sys.stdout.write("\n".join(lines[j + 1:k]))
        break
PY
}
same=1
for f in qr/haru-text-fr.txt qr/haru-text-ja.txt json/haru-freetext.json validator/summary.txt; do
  a="$(extract "$f")"; b="$(cat "$OUT/$f")"
  [ "$a" = "$b" ] || { same=0; echo "      differs: $f"; }
done
[ "$same" -eq 1 ] && ok "RAW-03 each block is the file content, character for character (accents, emoji, Japanese)" || ko "RAW-03 each block is the file content, character for character (accents, emoji, Japanese)"

grep -q "huge validator page" "$P" 2>/dev/null && ko "RAW-04 full validator pages are not included, summaries only" || ok "RAW-04 full validator pages are not included, summaries only"
grep -q "SECRET-LOG-LINE" "$P" 2>/dev/null && ko "RAW-05 logs are not included" || ok "RAW-05 logs are not included"

python3 -c "print('{\"resourceType\": \"Bundle\", \"pad\": \"' + 'x'*30000 + '\"}')" > "$OUT/json/demo_haru.fhir.json"
bash "$SRC/jp.sh" > /dev/null 2>&1
if grep -q "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" "$P"; then ko "RAW-08 a json file over 20 kB (a whole Bundle) is not pasted in"
elif grep -qF "json/demo_haru.fhir.json" "$P"; then ok "RAW-08 a json file over 20 kB (a whole Bundle) is not pasted in, only its path and size are listed"
else ko "RAW-08 a json file over 20 kB is listed by path and size"; fi
rm -f "$OUT/json/demo_haru.fhir.json"

echo "stale line from a previous run" > "$P"
bash "$SRC/jp.sh" > /dev/null 2>&1
grep -q "stale line" "$P" && ko "RAW-06 a second run replaces the file, it does not append" || ok "RAW-06 a second run replaces the file, it does not append"

rm -rf "$OUT/qr" "$OUT/json" "$OUT/validator"; mkdir -p "$OUT/qr"
bash "$SRC/jp.sh" > /dev/null 2>&1; rc=$?
[ "$rc" -ne 0 ] && ok "RAW-07 a run with no piece at all is an error, not an empty report" || ko "RAW-07 a run with no piece at all is an error, not an empty report (rc=$rc)"

echo "---"; echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
