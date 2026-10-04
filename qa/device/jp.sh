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

guard_run() {
  local run_dir="$1"
  local report_file="${2:-}"
  if find "$run_dir" -type f -name '*.pdf' 2>/dev/null | grep -q .; then
    echo "guard_run: rejected: PDF found in $run_dir" >&2
    return 1
  fi
  if find "$run_dir" -type f -size +2097152c 2>/dev/null | grep -q .; then
    echo "guard_run: rejected: file > 2MB in $run_dir" >&2
    return 1
  fi
  if [ -n "${ADB_SERIAL:-}" ]; then
    if grep -r -F "$ADB_SERIAL" "$run_dir" >/dev/null 2>&1; then
      echo "guard_run: rejected: ADB_SERIAL found in $run_dir" >&2
      return 1
    fi
    if [ -n "$report_file" ] && [ -f "$report_file" ]; then
      if grep -F "$ADB_SERIAL" "$report_file" >/dev/null 2>&1; then
        echo "guard_run: rejected: ADB_SERIAL found in $report_file" >&2
        return 1
      fi
    fi
  fi
  # GUARD-09: check that every logcat file has the scrub_logcat end line
  local lf
  while IFS= read -r lf; do
    [ -z "$lf" ] && continue
    if ! grep -q '^--------- scrub_logcat:' "$lf"; then
      echo "guard_run: rejected: unscrubbed logcat without end line: $lf" >&2
      return 1
    fi
  done < <(find "$run_dir" -type f -name '*logcat*.txt' 2>/dev/null)
  # GUARD-10: check for non-demo profile id (p-xxxxxxxx) in published text files
  if grep -r -E -I -q '(^|[^a-zA-Z0-9_-])p-[0-9a-f]{8}' "$run_dir" 2>/dev/null; then
    echo "guard_run: rejected: non-demo profile id found in $run_dir" >&2
    return 1
  fi
  # GUARD-11: check for non-demo profile id in report file
  if [ -n "$report_file" ] && [ -f "$report_file" ]; then
    if grep -E -q '(^|[^a-zA-Z0-9_-])p-[0-9a-f]{8}' "$report_file" 2>/dev/null; then
      echo "guard_run: rejected: non-demo profile id found in $report_file" >&2
      return 1
    fi
  fi
  return 0
}

act() {
  local a="$1"; shift
  case "$a" in
    mailbox-pull)   git -C "$MB" pull --rebase origin agent-mailbox && ls "$MB/to-antigravity" ;;
    mailbox-push)   git -C "$MB" add -A && git -C "$MB" commit -m "$*" && git -C "$MB" pull --rebase origin agent-mailbox && git -C "$MB" push origin agent-mailbox ;;
    checkout)       git -C "$ROOT" fetch origin && git -C "$ROOT" checkout "$1" && git -C "$ROOT" rev-parse --short HEAD ;;
    set-root)       ROOT="$1" && echo "ROOT=$ROOT" ;;
    merge)          git -C "$ROOT" merge --no-edit "$@" && git -C "$ROOT" rev-parse --short HEAD ;;
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
                      && rsync -a --exclude '*.pdf' --exclude pull/ --exclude 'pull-*/' --exclude backup/ --exclude 'files-*/' "$OUT"/ "$DR/$SLUG/$run"/ \
                      && cp "$OUT/report.md" "$DR/reports/cycle-$1.md" \
                      && guard_run "$DR/$SLUG/$run" "$DR/reports/cycle-$1.md" \
                      && python3 "$ROOT/qa/device/build_gallery.py" "$DR" \
                      && git -C "$DR" add -A && git -C "$DR" commit -m "${*:2}" && git -C "$DR" push origin device-reports \
                      && git -C "$DR" rev-parse --short HEAD ;;
    reports-commit) git -C "$DR" add -A && git -C "$DR" commit -m "$*" && git -C "$DR" push origin device-reports && git -C "$DR" rev-parse --short HEAD ;;
    status)         git -C "$ROOT" status -s | head -5; git -C "$ROOT" rev-parse --short HEAD; "${ADB[@]}" get-state ;;
    guard-test)     bash "$ROOT/qa/device/tests/test_publish_guard.sh" ;;
    gradle-test)    (cd "$ROOT/JemmaPassAndroidDemo" && ./gradlew :app:testDebugUnitTest "$@") ;;
    branch-commit)  git -C "$ROOT" add -A && git -C "$ROOT" commit -m "$*" ;;
    branch-push)
      case "$1" in
        ag/*) git -C "$ROOT" push --force-with-lease origin "$1" ;;
        *)    echo "branch-push: refused (only ag/* branches allowed: $1)" >&2; return 1 ;;
      esac
      ;;
    report-raw)
      local target="$1"
      if [[ "$target" != /* ]]; then
        if [ -n "${OUT:-}" ] && [ -d "$OUT" ]; then target="$OUT/$target"; else target="$PWD/$target"; fi
      fi
      local rel count=0
      local tmp_target="${target}.tmp.$$"
      mkdir -p "$(dirname "$target")"
      : > "$tmp_target"
      for dir in "$OUT/qr" "$OUT/json" "$OUT/validator"; do
        [ -d "$dir" ] || continue
        for f in "$dir"/*; do
          [ -f "$f" ] || continue
          case "$dir" in
            */validator)
              case "$(basename "$f")" in summary*.txt) ;; *) continue ;; esac
              ;;
          esac
          rel="${f#$OUT/}"
          count=$((count+1))
          if [ "$dir" = "$OUT/json" ] && [ "$(wc -c < "$f")" -gt 20480 ]; then
            echo "### \`$rel\` ($(wc -c < "$f" | tr -d ' ') bytes > 20 kB, omitted)" >> "$tmp_target"
            echo "" >> "$tmp_target"
            continue
          fi
          echo "### \`$rel\`" >> "$tmp_target"
          echo '```' >> "$tmp_target"
          cat "$f" >> "$tmp_target"
          [ -z "$(tail -c 1 "$f")" ] || echo "" >> "$tmp_target"
          echo '```' >> "$tmp_target"
          echo "" >> "$tmp_target"
        done
      done
      if [ "$count" -eq 0 ]; then
        rm -f "$tmp_target"
        echo "report-raw: error: no report pieces found in $OUT" >&2
        return 1
      fi
      mv "$tmp_target" "$target"
      echo "report-raw: wrote $(wc -l < "$target" | tr -d ' ') lines to $target"
      ;;
    report-test)    bash "$ROOT/qa/device/tests/test_report_raw.sh" ;;
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
