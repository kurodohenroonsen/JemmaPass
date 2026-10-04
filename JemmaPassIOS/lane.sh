#!/usr/bin/env bash
set -u

# Script de couloir unique Antigravity-iOS (conforme PROTOCOL.md §7 bis)
# Usage: bash JemmaPassIOS/lane.sh
# Lit /tmp/jp/ios/task.txt et écrit dans /tmp/jp/ios/out.txt

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DIR="/tmp/jp/ios"
TASK="$DIR/task.txt"
LOG="$DIR/out.txt"
mkdir -p "$DIR"

[ -f "$HOME/.jemmapass.env" ] && . "$HOME/.jemmapass.env"
MB="${JP_MAILBOX:-$ROOT/../jemmapass-mailbox}"
DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
export DEVELOPER_DIR

act() {
  local a="$1"; shift
  case "$a" in
    status)
      echo "=== git status (iOS worktree) ==="
      git -C "$ROOT" status -s | head -10
      git -C "$ROOT" rev-parse --short HEAD
      ;;
    swift-test)
      echo "=== swift test (JemmaCore) ==="
      (cd "$SCRIPT_DIR/JemmaCore" && xcrun swift test "$@")
      ;;
    mailbox-pull)
      echo "=== mailbox pull ==="
      git -C "$MB" pull --rebase origin agent-mailbox
      ;;
    mailbox-status)
      echo "=== mailbox status ==="
      git -C "$MB" status -s
      ;;
    mailbox-log)
      echo "=== mailbox log ==="
      git -C "$MB" log --oneline -n 15
      ;;
    mailbox-ls)
      echo "=== mailbox ls ==="
      ls -la "$MB/to-antigravity" 2>/dev/null || true
      ls -la "$MB/to-claude" 2>/dev/null || true
      ls -la "$MB/meetings" 2>/dev/null || true
      ;;
    inspect-reports)
      echo "=== inspect reports ==="
      git -C "$ROOT" fetch origin device-reports:refs/remotes/origin/device-reports 2>/dev/null || true
      git -C "$ROOT" ls-tree --name-only origin/device-reports:reports | head -30
      ;;
    show-report-file)
      echo "=== show report file ==="
      git -C "$ROOT" show "origin/device-reports:$1" | head -40
      ;;
    show-feat-file)
      echo "=== show feat file ==="
      git -C "$ROOT" show "origin/feat/ips-18-pillars-cleanup:$1" | head -50
      ;;
    find-feat)
      echo "=== find in feat ==="
      git -C "$ROOT" ls-tree -r --name-only origin/feat/ips-18-pillars-cleanup | grep -E "$1" | head -10
      ;;
    mailbox-push)
      echo "=== mailbox push ==="
      git -C "$MB" add -A && git -C "$MB" commit -m "$*" && git -C "$MB" push origin agent-mailbox
      ;;
    feat-merge)
      echo "=== merge origin/feat/ips-18-pillars-cleanup ==="
      git -C "$ROOT" fetch origin feat/ips-18-pillars-cleanup && git -C "$ROOT" merge origin/feat/ips-18-pillars-cleanup
      ;;
    branch-commit)
      echo "=== commit ag/ios-main ==="
      git -C "$ROOT" add JemmaPassIOS/ && git -C "$ROOT" commit -m "$*"
      ;;
    branch-push)
      echo "=== push ag/ios-main ==="
      git -C "$ROOT" push origin ag/ios-main
      ;;
    *)
      echo "lane-ios: action inconnue: $a" >&2
      return 64
      ;;
  esac
}

: > "$LOG"
[ -f "$TASK" ] || { echo "no task file: $TASK" | tee "$LOG"; exit 2; }

rc_all=0
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in ''|'#'*) continue ;; esac
  echo "▶ $line" | tee -a "$LOG"
  args=()
  while IFS= read -r -d '' x; do
    args+=("$x")
  done < <(python3 -c 'import shlex,sys; sys.stdout.write("".join(a+"\0" for a in shlex.split(sys.argv[1])))' "$line")
  [ "${#args[@]}" -eq 0 ] && continue
  act "${args[@]}" > "$DIR/step.txt" 2>&1
  rc=$?
  tee -a "$LOG" < "$DIR/step.txt"
  echo "◀ rc=$rc" | tee -a "$LOG"
  [ "$rc" -ne 0 ] && rc_all=1
done < "$TASK"

exit $rc_all
