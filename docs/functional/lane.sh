#!/usr/bin/env bash
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="${JP_DIR:-$ROOT/docs/functional/.jp}"
TASK="$DIR/task.txt"
LOG="$DIR/out.txt"
mkdir -p "$DIR"
[ -f "$HOME/.jemmapass.env" ] && . "$HOME/.jemmapass.env"
MB="${JP_MAILBOX:-$ROOT/../jemmapass-mailbox}"

act() {
  local a="$1"; shift
  case "$a" in
    mailbox-pull)   git -C "$MB" pull --rebase origin agent-mailbox ;;
    mailbox-push)   git -C "$MB" add -A && git -C "$MB" commit -m "$*" && git -C "$MB" pull --rebase origin agent-mailbox && git -C "$MB" push origin agent-mailbox ;;
    branch-pull)    git -C "$ROOT" fetch origin ag/analyse-fonctionnelle && git -C "$ROOT" merge --ff-only origin/ag/analyse-fonctionnelle || true ;;
    branch-commit)  git -C "$ROOT" add docs/functional/35-nfc.md docs/functional/lane.sh && git -C "$ROOT" commit -m "$*" ;;
    branch-push)    git -C "$ROOT" push origin ag/analyse-fonctionnelle ;;
    status)         git -C "$ROOT" status -s docs/functional/; git -C "$ROOT" rev-parse --short HEAD; git -C "$MB" status -s; git -C "$MB" ls-files to-claude/amelioration-Antigravity-Analyse* ;;
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
