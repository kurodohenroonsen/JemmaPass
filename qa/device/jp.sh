#!/usr/bin/env bash
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="${JP_DIR:-/tmp/jp}"
TASK="$DIR/task.txt"
LOG="$DIR/out.txt"
mkdir -p "$DIR"
[ -f "$HOME/.jemmapass.env" ] && . "$HOME/.jemmapass.env"
DR="${JP_REPORTS:-$ROOT/../jemmapass-device-reports}"
MB="${JP_MAILBOX:-$ROOT/../jemmapass-mailbox}"
SLUG="feat-ips-18-pillars-cleanup"
PKG="be.heyman.android.jemmapassdemo"
PROFILES="/sdcard/Android/data/$PKG/files/profiles"
KB_REMOTE="/sdcard/Android/data/$PKG/files/knowledge_full_db/1.1/knowledge_full.db"
KB_LOCAL="$DIR/knowledge_full.db"
JAR="${FHIR_VALIDATOR_JAR:-/tmp/fhir-validator/validator_cli.jar}"
TAGS="JEMMA-PROFILES JEMMA-CODEC JEMMA-IMMUNIZATIONS-EDIT JEMMA-IMMUNIZATIONS-FORM JEMMA-IMMUNIZATIONS-ADAPTER JEMMA-PROCEDURES-EDIT JEMMA-PROCEDURES-FORM JEMMA-PROCEDURES-ADAPTER JEMMA-DEVICES-EDIT JEMMA-DEVICES-FORM JEMMA-DEVICES-ADAPTER JEMMA-RESULTS-EDIT JEMMA-RESULTS-FORM JEMMA-RESULTS-ADAPTER JEMMA-PASTPROBLEMS-EDIT JEMMA-PASTPROBLEMS-FORM JEMMA-PASTPROBLEMS-ADAPTER JEMMA-KB-CONDITION-PICKER JEMMA-SNOMED-CAT JEMMA-PREGNANCY-EDIT JEMMA-PROFILE-DETAIL JEMMA-HYDRATOR JEMMA-QR AndroidRuntime"
ADB=(adb)
[ -n "${ADB_SERIAL:-}" ] && ADB=(adb -s "$ADB_SERIAL")

latest_out() { ls -dt "$ROOT"/qa/device/out/*/ 2>/dev/null | head -1 | sed 's:/$::'; }
OUT="${OUT:-$(latest_out)}"

act() {
  local a="$1"; shift
  case "$a" in
    mailbox-pull)   git -C "$MB" pull --rebase origin agent-mailbox && ls "$MB/to-antigravity" ;;
    mailbox-push)   git -C "$MB" add -A && git -C "$MB" commit -m "$*" && git -C "$MB" pull --rebase origin agent-mailbox && git -C "$MB" push origin agent-mailbox ;;
    checkout)       git -C "$ROOT" fetch origin && git -C "$ROOT" checkout "$1" && git -C "$ROOT" rev-parse --short HEAD ;;
    qa-run)         "$ROOT/qa/device/run_device_qa.sh"; OUT="$(latest_out)"; echo "OUT=$OUT" ;;
    out)            echo "OUT=$OUT"; ls "$OUT" ;;
    ui)             python3 "$ROOT/qa/device/ui.py" "$@" ;;
    shot)           mkdir -p "$OUT/screenshots" && python3 "$ROOT/qa/device/ui.py" screenshot "$OUT/screenshots/$1" ;;
    swipe)          "${ADB[@]}" shell input swipe "$@" ;;
    key)            "${ADB[@]}" shell input keyevent "$1" ;;
    locale)         "${ADB[@]}" shell cmd locale set-app-locales "$PKG" --locales "$1" ;;
    pull-profiles)  mkdir -p "$OUT/$1" && "${ADB[@]}" pull "$PROFILES/." "$OUT/$1/" ;;
    verify)         python3 "$ROOT/qa/device/verify_profiles.py" "$OUT/$1" "${@:2}" ;;
    validate)       "$ROOT/qa/device/validate_all.sh" "${1:-$OUT}" "${2:-}" ;;
    decode-qr)      mkdir -p "$OUT/qr" && python3 "$ROOT/qa/device/decode_qr.py" "$OUT/screenshots/$1" "$OUT/qr/$2" ;;
    logcat)         mkdir -p "$OUT/logs" && "${ADB[@]}" logcat -d -s $TAGS > "$OUT/logs/logcat-ui.txt" && python3 "$ROOT/qa/device/scrub_logcat.py" "$OUT/logs/logcat-ui.txt" && wc -l "$OUT/logs/logcat-ui.txt" ;;
    logcat-clear)   "${ADB[@]}" logcat -c ;;
    grep-log)       grep -- "$*" "$OUT/logs/logcat-ui.txt" ;;
    json)           mkdir -p "$OUT/json" && python3 "$ROOT/qa/device/extract_json.py" "$OUT/$1" "$2" "$OUT/json/$3" ;;
    kb-pull)        "${ADB[@]}" pull "$KB_REMOTE" "$KB_LOCAL" && ls -lh "$KB_LOCAL" ;;
    kb-sql)         mkdir -p "$OUT/kb" && sqlite3 -header -column "$KB_LOCAL" < "$DIR/$1" | tee "$OUT/kb/$2" ;;
    kb-rm)          rm -f "$KB_LOCAL" && echo "kb copy removed" ;;
    measure)        git -C "$DR" ls-tree -r -l HEAD | awk '{s+=$4} END {print NR" files", s" bytes"}' ;;
    count-png)      find "$DR/$SLUG" -name '*.png' | wc -l ;;
    prune)          "$ROOT/qa/device/prune_run.sh" "$DR/$SLUG"/* ;;
    gallery)        python3 "$ROOT/qa/device/build_gallery.py" "$DR" ;;
    leakcheck)      git -C "$DR" grep -n -i -E "46071|FDAS" || echo "leakcheck: clean" ;;
    publish)        local run; run="$(basename "$OUT")"; mkdir -p "$DR/$SLUG/$run" "$DR/reports" \
                      && rsync -a --exclude pull/ --exclude 'pull-*/' --exclude backup/ --exclude 'files-*/' "$OUT"/ "$DR/$SLUG/$run"/ \
                      && cp "$OUT/report.md" "$DR/reports/cycle-$1.md" \
                      && python3 "$ROOT/qa/device/build_gallery.py" "$DR" \
                      && git -C "$DR" add -A && git -C "$DR" commit -m "${*:2}" && git -C "$DR" push origin device-reports \
                      && git -C "$DR" rev-parse --short HEAD ;;
    reports-commit) git -C "$DR" add -A && git -C "$DR" commit -m "$*" && git -C "$DR" push origin device-reports && git -C "$DR" rev-parse --short HEAD ;;
    status)         git -C "$ROOT" status -s | head -5; git -C "$ROOT" rev-parse --short HEAD; "${ADB[@]}" get-state ;;
    *)              echo "unknown action: $a"; return 64 ;;
  esac
}

: > "$LOG"
[ -f "$TASK" ] || { echo "no task file: $TASK" | tee "$LOG"; exit 2; }
rc_all=0
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in ''|'#'*) continue ;; esac
  echo "▶ $line" | tee -a "$LOG"
  args=()
  while IFS= read -r -d '' x; do args+=("$x"); done < <(python3 -c 'import shlex,sys; sys.stdout.write("".join(a+"\0" for a in shlex.split(sys.argv[1])))' "$line")
  [ "${#args[@]}" -eq 0 ] && continue
  act "${args[@]}" > "$DIR/step.txt" 2>&1
  rc=$?
  tee -a "$LOG" < "$DIR/step.txt"
  echo "◀ rc=$rc" | tee -a "$LOG"
  [ "$rc" -ne 0 ] && rc_all=1
done < "$TASK"
exit $rc_all
