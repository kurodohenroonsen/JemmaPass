#!/usr/bin/env bash
set -eo pipefail

TASK_FILE="/tmp/jp/usb/task.txt"
OUT_FILE="/tmp/jp/usb/out.txt"
USB_DIR="/Users/kurodohenroonsen/Documents/jemmapass-usb"
MAILBOX_DIR="/Users/kurodohenroonsen/Documents/jemmapass-mailbox"

mkdir -p "/tmp/jp/usb"
exec > >(tee "$OUT_FILE") 2>&1

echo "=== JemmaPass USB Lane Runner ==="
echo "Timestamp: $(date -u +'%Y-%m-%dT%H:%M:%SZ')"

if [ ! -f "$TASK_FILE" ]; then
    echo "No task file found at $TASK_FILE"
    exit 0
fi

while IFS= read -r action || [ -n "$action" ]; do
    [[ -z "$action" || "$action" =~ ^# ]] && continue
    echo "--- Action: $action ---"
    case "$action" in
        "test")
            cd "$USB_DIR"
            node --test JemmaPassUSB/tests/*.test.js
            ;;
        "commit-and-push")
            cd "$USB_DIR"
            git add JemmaPassUSB/
            if ! git diff --cached --quiet; then
                git commit -m "feat(usb): Tour 2 - codec FHIR, rejeu vecteurs et suite verte"
                git push origin ag/usb-main
            else
                echo "Nothing to commit on ag/usb-main"
            fi
            echo "Current commit: $(git rev-parse --short HEAD)"
            ;;
        "mailbox-sync")
            cd "$MAILBOX_DIR"
            git fetch origin agent-mailbox
            git pull --rebase origin agent-mailbox
            git add to-claude/ meetings/ state/ 2>/dev/null || true
            if ! git diff --cached --quiet; then
                git commit -m "report(usb): Tour 2 rapport et amélioration"
                git push origin agent-mailbox
            else
                echo "Nothing to commit on agent-mailbox"
            fi
            ;;
        *)
            echo "Unknown action: $action"
            exit 1
            ;;
    esac
done < "$TASK_FILE"

echo "=== Lane finished successfully ==="
