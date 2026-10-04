#!/usr/bin/env bash
# ==============================================================================
# JemmaPassChrome/lane.sh — Couloir exclusif Antigravity-Chrome
# Conforme à PROTOCOL.md §7 & §7 bis (décision de Kudoro, 2026-10-04)
# Commande unique fixe autorisée une fois pour toutes :
#   bash JemmaPassChrome/lane.sh
# ==============================================================================
set -u

CHROME_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$CHROME_DIR/.." && pwd)"
DIR="${JP_DIR:-/tmp/jp/chrome}"
TASK="$DIR/task.txt"
LOG="$DIR/out.txt"
mkdir -p "$DIR"

MB="${JP_MAILBOX:-$ROOT/../jemmapass-mailbox}"
JAVA_BIN="${JAVA_BIN:-/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java}"
JAR="${FHIR_VALIDATOR_JAR:-/tmp/fhir-validator/validator_cli.jar}"
OUT="$CHROME_DIR/tests/out"

act() {
  local a="$1"; shift
  case "$a" in
    mailbox-pull)
      git -C "$MB" pull --rebase origin agent-mailbox
      ;;
    mailbox-push)
      git -C "$MB" add -A && git -C "$MB" commit -m "$*" && git -C "$MB" pull --rebase origin agent-mailbox && git -C "$MB" push origin agent-mailbox
      ;;
    merge-feat)
      git -C "$ROOT" fetch origin && git -C "$ROOT" merge origin/feat/ips-18-pillars-cleanup
      ;;
    test)
      cd "$ROOT" && node --experimental-strip-types --test \
        JemmaPassChrome/tests/test_vectors.ts \
        JemmaPassChrome/tests/test_personas.ts \
        JemmaPassChrome/tests/test_codec_roundtrip.ts
      ;;
    validate)
      if [ -f "$JAR" ] && [ -x "$JAVA_BIN" ]; then
        mkdir -p "$OUT/validator"
        pids=()
        for p in demo_kurodo demo_haru demo_kamekichi; do
          "$JAVA_BIN" -jar "$JAR" "$OUT/files/$p.fhir.json" -version 4.0.1 -ig hl7.fhir.uv.ips#1.1.0 -locale en -tx n/a \
            -output "$OUT/validator/$p.txt" > "$OUT/validator/$p.log" 2>&1 &
          pids+=($!)
        done
        for pid in "${pids[@]}"; do wait "$pid"; done
        echo "Validation terminée."
        echo "Erreurs HL7 par persona :"
        grep -c '<td>Error</td>' "$OUT"/validator/*.txt || true
        echo "Avertissements HL7 par persona :"
        grep -c '<td>Warning</td>' "$OUT"/validator/*.txt || true
      else
        echo "Validateur HL7 non disponible (JAR ou Java absent)."
      fi
      ;;
    suite)
      act test && act validate
      ;;
    push)
      git -C "$ROOT" push --force-with-lease origin ag/chrome-main
      ;;
    commit)
      git -C "$ROOT" add JemmaPassChrome/ && git -C "$ROOT" commit -m "$*"
      ;;
    status)
      git -C "$ROOT" status
      ;;
    mailbox-status)
      git -C "$MB" status
      ;;
    mailbox-files)
      echo "=== to-antigravity ==="
      find "$MB/to-antigravity" -type f 2>/dev/null
      echo "=== to-claude (Chrome) ==="
      find "$MB/to-claude" -type f -name "*Chrome*" 2>/dev/null
      ;;
    find-reports)
      find "$ROOT/../jemmapass-device-reports" -name "*demo_haru*" 2>/dev/null
      ;;
    diff)
      git -C "$ROOT" diff JemmaPassChrome/
      ;;
    find-file)
      find "$ROOT" -name "$1" -not -path "*/.*" -not -path "*/node_modules/*" 2>/dev/null
      ;;
    *)
      echo "lane.sh: action inconnue: $a" >&2
      return 1
      ;;
  esac
}

# Exécution de la liste des tâches
if [ ! -f "$TASK" ]; then
  echo "lane.sh: aucun fichier $TASK trouvé. Créer $TASK avec les actions nommées." >&2
  exit 0
fi

{
  echo "=== Début du passage Chrome $(date -u +'%Y-%m-%dT%H:%M:%SZ') ==="
  while IFS= read -r line || [ -n "$line" ]; do
    # Ignore commentaires et lignes vides
    line="$(echo "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    [ -z "$line" ] && continue
    [[ "$line" =~ ^# ]] && continue
    echo "--- Action: $line ---"
    action="${line%% *}"
    args=""
    if [[ "$line" == *" "* ]]; then
      args="${line#* }"
    fi
    if [ -n "$args" ]; then
      act "$action" "$args"
    else
      act "$action"
    fi
    echo ""
  done < "$TASK"
  echo "=== Fin du passage Chrome ==="
} > "$LOG" 2>&1

cat "$LOG"
