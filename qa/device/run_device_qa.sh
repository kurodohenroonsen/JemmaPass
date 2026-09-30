#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP_DIR="$ROOT/JemmaPassAndroidDemo"
PKG="be.heyman.android.jemmapassdemo"
DEV_PROFILES="/sdcard/Android/data/$PKG/files/profiles"
STAMP="$(date +%Y%m%d-%H%M)"
SHA="$(git -C "$ROOT" rev-parse --short HEAD)"
BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD)"
OUT="${OUT:-$ROOT/qa/device/out/${SHA}-${STAMP}}"
ADB=(adb)
if [[ -n "${ADB_SERIAL:-}" ]]; then ADB=(adb -s "$ADB_SERIAL"); fi
UI="python3 $ROOT/qa/device/ui.py"
SKIP_BUILD="${SKIP_BUILD:-0}"

mkdir -p "$OUT/logs" "$OUT/files" "$OUT/screenshots" "$OUT/pull" "$OUT/backup"
printf 'pull/\nbackup/\n' > "$OUT/.gitignore"

log() { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; echo "▶ $*" >> "$OUT/logs/run.log"; }
step_result() { echo "| $1 | $2 | $3 |" >> "$OUT/steps.md"; }

echo "| Step | Status | Detail |" > "$OUT/steps.md"
echo "|---|---|---|" >> "$OUT/steps.md"

log "0. Device"
"${ADB[@]}" get-state >/dev/null 2>&1 || { echo "no device attached (adb get-state)"; exit 2; }
"${ADB[@]}" devices -l | tee "$OUT/logs/adb-devices.txt"
MODEL="$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
ANDROID="$("${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r')"
{
  echo "branch: $BRANCH"
  echo "sha: $SHA"
  echo "date: $STAMP"
  echo "device: $MODEL (Android $ANDROID)"
  echo "host: $(uname -s) $(uname -m)"
} | tee "$OUT/env.txt"
step_result "0 device" "✅" "$MODEL · Android $ANDROID"

if [[ "$SKIP_BUILD" != "1" ]]; then
  log "1. JVM unit tests"
  if (cd "$APP_DIR" && ./gradlew --console=plain :app:testDebugUnitTest 2>&1 | tee "$OUT/logs/unit-tests.log"); then
    TESTS="$(grep -hoE '[0-9]+ tests completed' "$OUT/logs/unit-tests.log" | tail -1 || true)"
    step_result "1 unit tests" "✅" "${TESTS:-see logs/unit-tests.log}"
  else
    step_result "1 unit tests" "❌" "see logs/unit-tests.log"
  fi

  log "2. assembleDebug"
  if (cd "$APP_DIR" && ./gradlew --console=plain :app:assembleDebug 2>&1 | tee "$OUT/logs/assemble.log"); then
    step_result "2 assembleDebug" "✅" "$(du -h "$APP_DIR/app/build/outputs/apk/debug/app-debug.apk" | cut -f1)"
  else
    step_result "2 assembleDebug" "❌" "see logs/assemble.log"; exit 3
  fi
fi

log "3. Install (keeps data — the 6 GB model/KB stay in place)"
"${ADB[@]}" install -r -g "$APP_DIR/app/build/outputs/apk/debug/app-debug.apk" 2>&1 | tee "$OUT/logs/install.log"
step_result "3 install -r -g" "✅" "$(tail -1 "$OUT/logs/install.log")"

log "4. Backup profiles + force re-seed of the demo personas only"
"${ADB[@]}" shell am force-stop "$PKG"
"${ADB[@]}" pull "$DEV_PROFILES" "$OUT/backup/" >/dev/null 2>&1 || echo "(no profiles dir yet)"
for id in demo_kurodo demo_haru demo_kamekichi; do
  "${ADB[@]}" shell rm -f "$DEV_PROFILES/$id.json" "$DEV_PROFILES/$id.fhir.json" || true
done
step_result "4 backup + demo reseed" "✅" "backup in out/backup (not published)"

log "5. Launch, wait for seeding, pull the profiles (no screenshot here: the profiles list may show real profiles)"
"${ADB[@]}" logcat -c || true
"${ADB[@]}" shell am start -n "$PKG/.MainActivity" >/dev/null
sleep 15
"${ADB[@]}" pull "$DEV_PROFILES" "$OUT/pull/" >/dev/null
cp "$OUT"/pull/profiles/demo_*.json "$OUT/files/" 2>/dev/null || true
"${ADB[@]}" logcat -d -s JEMMA-PROFILES:* JEMMA-CODEC:* JEMMA-IMMUNIZATIONS-EDIT:* JEMMA-IMMUNIZATIONS-FORM:* JEMMA-IMMUNIZATIONS-ADAPTER:* JEMMA-PROFILE-DETAIL:* AndroidRuntime:E > "$OUT/logs/logcat-seed.txt" || true
python3 "$ROOT/qa/device/scrub_logcat.py" "$OUT/logs/logcat-seed.txt"
ls -la "$OUT/files" | tee -a "$OUT/logs/run.log"

log "6. verify_profiles.py on the seeded demo personas"
if python3 "$ROOT/qa/device/verify_profiles.py" "$OUT/pull/profiles" \
     --only demo_kurodo --only demo_haru --only demo_kamekichi \
     --expect demo_kurodo=4 --expect demo_haru=3 --expect demo_kamekichi=0 \
     --expect-pr demo_kurodo=2 --expect-pr demo_haru=2 --expect-pr demo_kamekichi=0 \
     --expect-dv demo_kurodo=0 --expect-dv demo_haru=2 --expect-dv demo_kamekichi=0 \
     --title "Seed verification (demo personas)" --markdown "$OUT/verify-seed.md"; then
  step_result "6 verify seed" "✅" "see verify-seed.md"
else
  step_result "6 verify seed" "❌" "see verify-seed.md"
fi

log "Done — automated part finished. Continue with the UI protocol (README §3), then re-run:"
echo "  python3 $ROOT/qa/device/verify_profiles.py <pulled folder> --only demo_kurodo --expect demo_kurodo=<n im> --expect-pr demo_kurodo=<n pr> --expect-dv demo_kurodo=<n dv>"
echo "Report folder: $OUT"
