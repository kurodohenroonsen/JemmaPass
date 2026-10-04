#!/usr/bin/env bash
set -u
ROOT="/Users/kurodohenroonsen/Documents/JemmaPass_IPS_FULL"
UX="/Users/kurodohenroonsen/Documents/jemmapass-ux"
MB="/Users/kurodohenroonsen/Documents/jemmapass-mailbox"
DIR="${JP_DIR:-/tmp/jp/ux}"
TASK="$DIR/task.txt"
LOG="$DIR/out.txt"
mkdir -p "$DIR"

act() {
  local a="$1"; shift
  case "$a" in
    mailbox-pull)
      git -C "$MB" pull --rebase origin agent-mailbox
      echo "=== to-antigravity ==="
      ls -1 "$MB/to-antigravity"
      ;;
    mailbox-push)
      git -C "$MB" add -A && git -C "$MB" commit -m "$*" && git -C "$MB" pull --rebase origin agent-mailbox && git -C "$MB" push origin agent-mailbox
      ;;
    mailbox-list)
      echo "=== to-antigravity ==="
      ls -1 "$MB/to-antigravity"
      echo "=== to-claude ==="
      ls -1 "$MB/to-claude"
      echo "=== meetings ==="
      ls -d "$MB"/meetings/*/ 2>/dev/null
      ;;
    mailbox-log)
      git -C "$MB" log -n 5 --oneline
      ;;
    ux-status)
      git -C "$UX" status -s
      git -C "$UX" rev-parse --short HEAD
      ;;
    ux-commit)
      git -C "$UX" add -A && git -C "$UX" commit -m "$*"
      ;;
    ux-push)
      git -C "$UX" push origin ag/ux-main
      ;;
    ux-pull)
      git -C "$UX" pull --rebase origin ag/ux-main
      ;;
    feat-fetch)
      git -C "$ROOT" fetch origin feat/ips-18-pillars-cleanup
      ;;
    ux-rebase-feat)
      git -C "$UX" fetch origin && git -C "$UX" rebase origin/feat/ips-18-pillars-cleanup
      ;;
    contacts-grep)
      grep -rnE "contactRowSubtitle|ContactsAdapter|ContactsEditFragment" "$ROOT/JemmaPassAndroidDemo/app/src/main/java"
      ;;
    *)
      echo "unknown action: $a"
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
  while IFS= read -r -d '' x; do args+=("$x"); done < <(python3 -c 'import shlex,sys; sys.stdout.write("".join(a+"\0" for a in shlex.split(sys.argv[1])))' "$line")
  [ "${#args[@]}" -eq 0 ] && continue
  act "${args[@]}" > "$DIR/step.txt" 2>&1
  rc=$?
  tee -a "$LOG" < "$DIR/step.txt"
  echo "◀ rc=$rc" | tee -a "$LOG"
  [ "$rc" -ne 0 ] && rc_all=1
done < "$TASK"
exit $rc_all
