#!/usr/bin/env bash
set -u
SRC="$(cd "$(dirname "$0")/.." && pwd)"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
export HOME="$T/home"; mkdir -p "$HOME"
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t
export ADB_SERIAL="TESTSERIAL0042XYZ"
pass=0; fail=0
ok()  { pass=$((pass+1)); echo "PASS  $1"; }
ko()  { fail=$((fail+1)); echo "FAIL  $1"; }

git init -q --bare "$T/reports.git"
git init -q --bare "$T/code.git"
mkdir -p "$T/root/qa/device"
cp "$SRC"/*.sh "$SRC"/*.py "$T/root/qa/device/" 2>/dev/null
git -C "$T/root" init -q && git -C "$T/root" remote add origin "$T/code.git"
git -C "$T/root" add -A && git -C "$T/root" commit -q -m init
git -C "$T/root" branch -M feat/ips-18-pillars-cleanup
git -C "$T/root" push -q origin feat/ips-18-pillars-cleanup
git clone -q "$T/reports.git" "$T/dr" 2>/dev/null
git -C "$T/dr" checkout -q -b device-reports
echo seed > "$T/dr/README.md"; git -C "$T/dr" add -A; git -C "$T/dr" commit -q -m seed; git -C "$T/dr" push -q origin device-reports
export JP_REPORTS="$T/dr" JP_MAILBOX="$T/mb" JP_DIR="$T/jp"; mkdir -p "$JP_DIR" "$JP_MAILBOX"

new_run() {
  OUT="$T/root/qa/device/out/$1"; export OUT
  mkdir -p "$OUT/screenshots" "$OUT/logs"
  echo "# report $1" > "$OUT/report.md"
  printf 'png' > "$OUT/screenshots/001.png"
}
run_jp() { printf '%s\n' "$@" > "$JP_DIR/task.txt"; bash "$T/root/qa/device/jp.sh" > /dev/null 2>&1; }
remote_head() { git -C "$T/reports.git" rev-parse device-reports; }
remote_files() { git -C "$T/reports.git" ls-tree -r --name-only device-reports; }

new_run r1-clean
before=$(remote_head); run_jp 'publish 01-aaaaaaa "clean run"'; rc=$?
[ "$rc" -eq 0 ] && [ "$(remote_head)" != "$before" ] && ok "GUARD-01 a clean run is published" || ko "GUARD-01 a clean run is published (rc=$rc)"

new_run r2-pdf
printf '%%PDF-1.4 fake' > "$OUT/Kamekichi.pdf"
run_jp 'publish 02-bbbbbbb "run with a pdf"'
remote_files | grep -qi '\.pdf$' && ko "GUARD-02 a PDF is never published" || ok "GUARD-02 a PDF is never published"

new_run r3-big
dd if=/dev/zero of="$OUT/logs/big.bin" bs=1024 count=3072 2>/dev/null
before=$(remote_head); run_jp 'publish 03-ccccccc "run with a 3 MB file"'; rc=$?
[ "$rc" -ne 0 ] && [ "$(remote_head)" = "$before" ] && ok "GUARD-03 a file over 2 MB blocks the publication, nothing is pushed" || ko "GUARD-03 a file over 2 MB blocks the publication, nothing is pushed (rc=$rc)"
rm -rf "$T/dr/feat-ips-18-pillars-cleanup/r3-big"; git -C "$T/dr" reset -q --hard origin/device-reports; git -C "$T/dr" clean -qfd

new_run r4-serial
echo "- device: Pixel 9 Pro XL ($ADB_SERIAL)" >> "$OUT/report.md"
before=$(remote_head); run_jp 'publish 04-ddddddd "run with the serial number"'; rc=$?
[ "$rc" -ne 0 ] && [ "$(remote_head)" = "$before" ] && ok "GUARD-04 the serial number in a new report blocks the publication" || ko "GUARD-04 the serial number in a new report blocks the publication (rc=$rc)"
git -C "$T/dr" reset -q --hard origin/device-reports; git -C "$T/dr" clean -qfd

mkdir -p "$T/dr/old" && echo 'git grep -n -i -E "46071\|FDAS" : 0 fuite' > "$T/dr/old/report.md"
git -C "$T/dr" add -A; git -C "$T/dr" commit -q -m "old report quoting the pattern"; git -C "$T/dr" push -q origin device-reports
new_run r5-after-old
before=$(remote_head); run_jp 'publish 05-eeeeeee "clean run after an old report that quotes the search pattern"'; rc=$?
[ "$rc" -eq 0 ] && [ "$(remote_head)" != "$before" ] && ok "GUARD-05 an old report that only quotes the search pattern does not block clean runs" || ko "GUARD-05 an old report that only quotes the search pattern does not block clean runs (rc=$rc)"

echo change >> "$T/root/qa/device/jp.sh.note"
before=$(git -C "$T/code.git" rev-parse feat/ips-18-pillars-cleanup)
run_jp 'branch-commit "should never reach the main work branch"' 'branch-push feat/ips-18-pillars-cleanup'; rc=$?
[ "$rc" -ne 0 ] && [ "$(git -C "$T/code.git" rev-parse feat/ips-18-pillars-cleanup)" = "$before" ] && ok "GUARD-06 branch-push refuses feat/ips-18-pillars-cleanup" || ko "GUARD-06 branch-push refuses feat/ips-18-pillars-cleanup (rc=$rc)"
run_jp 'branch-push tests/sd-wave-1'; rc=$?
[ "$rc" -ne 0 ] && ! git -C "$T/code.git" rev-parse -q --verify tests/sd-wave-1 >/dev/null && ok "GUARD-07 branch-push refuses tests/ branches" || ko "GUARD-07 branch-push refuses tests/ branches (rc=$rc)"
git -C "$T/root" checkout -q -b ag/9999-probe
run_jp 'branch-push ag/9999-probe'; rc=$?
[ "$rc" -eq 0 ] && git -C "$T/code.git" rev-parse -q --verify ag/9999-probe >/dev/null && ok "GUARD-08 branch-push accepts an ag/ branch" || ko "GUARD-08 branch-push accepts an ag/ branch (rc=$rc)"

git -C "$T/root" checkout -q feat/ips-18-pillars-cleanup 2>/dev/null
git -C "$T/dr" reset -q --hard origin/device-reports; git -C "$T/dr" clean -qfd
SCRUB_MARK='--------- scrub_logcat: 3 line(s) removed (non-JemmaPass tags or non-demo profile data)'
DEMO_LINE='10-03 22:23:03.384 27712 27712 D JEMMA-PROFILES: [Adapter] bind pos=2 · id=demo_kurodo · name=Kurodo Henro'
REAL_LINE='10-03 22:23:03.388 27712 27712 D JEMMA-PROFILES: [Adapter] bind pos=3 · id=p-0a1b2c3d-0000-4000-8000-000000000000 · name=Someone Real'

new_run r9-raw-logcat
printf '%s\n' "$DEMO_LINE" > "$OUT/logs/logcat-ui.txt"
before=$(remote_head); run_jp 'publish 09-fffffff "logcat that never went through scrub_logcat"'; rc=$?
[ "$rc" -ne 0 ] && [ "$(remote_head)" = "$before" ] && ok "GUARD-09 a logcat file without the scrub_logcat end line blocks the publication" || ko "GUARD-09 a logcat file without the scrub_logcat end line blocks the publication (rc=$rc)"
git -C "$T/dr" reset -q --hard origin/device-reports; git -C "$T/dr" clean -qfd

new_run r10-real-profile
printf '%s\n%s\n%s\n' "$DEMO_LINE" "$REAL_LINE" "$SCRUB_MARK" > "$OUT/logs/logcat-seed.txt"
before=$(remote_head); run_jp 'publish 10-0000000 "non-demo profile id in a published file"'; rc=$?
[ "$rc" -ne 0 ] && [ "$(remote_head)" = "$before" ] && ok "GUARD-10 a non-demo profile id (p-xxxxxxxx) in any published text file blocks the publication, even with the end line" || ko "GUARD-10 a non-demo profile id (p-xxxxxxxx) in any published text file blocks the publication, even with the end line (rc=$rc)"
git -C "$T/dr" reset -q --hard origin/device-reports; git -C "$T/dr" clean -qfd

new_run r11-real-profile-in-report
echo "bind pos=3 id=p-0a1b2c3d-0000-4000-8000-000000000000" >> "$OUT/report.md"
before=$(remote_head); run_jp 'publish 11-1111111 "non-demo profile id quoted in the report"'; rc=$?
[ "$rc" -ne 0 ] && [ "$(remote_head)" = "$before" ] && ok "GUARD-11 a non-demo profile id quoted in report.md blocks the publication" || ko "GUARD-11 a non-demo profile id quoted in report.md blocks the publication (rc=$rc)"
git -C "$T/dr" reset -q --hard origin/device-reports; git -C "$T/dr" clean -qfd

new_run r12-scrubbed
printf '%s\n%s\n' "$DEMO_LINE" "$SCRUB_MARK" > "$OUT/logs/logcat-ui.txt"
before=$(remote_head); run_jp 'publish 12-2222222 "scrubbed logcat"'; rc=$?
[ "$rc" -eq 0 ] && [ "$(remote_head)" != "$before" ] && ok "GUARD-12 a scrubbed logcat with demo personas only is published" || ko "GUARD-12 a scrubbed logcat with demo personas only is published (rc=$rc)"

echo "---"; echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
