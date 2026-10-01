#!/usr/bin/env python3
"""
scrub_logcat.py — remove every logcat line that could carry personal data before
publication. The device may hold real profiles next to the demo personas: any line
mentioning a non-demo profile id (p-xxxxxxxx…) or a `name='…'` / `displayName` that
is not one of the demo names is dropped. Lines are never edited, only removed, and a
final line states how many were removed so the log stays auditable.

Tag allow-list (cycle 10 audit): a full `adb logcat -d` dump carries other apps'
data (Wi-Fi SSIDs / BSSIDs, accounts, notifications). Only JemmaPass tags
(`JEMMA-*`) and crash lines (`AndroidRuntime`) are kept, whatever was dumped.

  scrub_logcat.py <in> [<out>]      (in place when <out> is omitted)
"""
import re
import sys

DEMO_NAMES = ("Kurodo", "Haru", "Kamekichi", "demo_kurodo", "demo_haru", "demo_kamekichi")
RE_PROFILE_ID = re.compile(r"\bp-[0-9a-f]{8}\b")
RE_NAME = re.compile(r"(name='[^']*'|displayName=[^ ·]+|→ '[^']+')")
# threadtime / brief formats: "<date> <time> <pid> <tid> <L> <TAG>: …" or "<L>/<TAG>( pid): …"
RE_TAG = re.compile(r"^(?:\S+ \S+\s+\d+\s+\d+ [VDIWEF] |[VDIWEF]/)([^:(]+?)\s*(?:\(\s*\d+\))?:")
ALLOWED_TAG_PREFIXES = ("JEMMA-", "AndroidRuntime")


def keep(line: str) -> bool:
    if line.startswith("---------"):          # logcat buffer separators
        return True
    m_tag = RE_TAG.match(line)
    if not m_tag or not m_tag.group(1).strip().startswith(ALLOWED_TAG_PREFIXES):
        return False
    if RE_PROFILE_ID.search(line):
        return False
    m = RE_NAME.search(line)
    if m and not any(d in m.group(0) for d in DEMO_NAMES):
        return False
    return True


def main():
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(2)
    src = sys.argv[1]
    dst = sys.argv[2] if len(sys.argv) > 2 else src
    with open(src, encoding="utf-8", errors="replace") as f:
        lines = f.readlines()
    kept = [l for l in lines if keep(l)]
    removed = len(lines) - len(kept)
    with open(dst, "w", encoding="utf-8") as f:
        f.writelines(kept)
        f.write(f"--------- scrub_logcat: {removed} line(s) removed (non-JemmaPass tags or non-demo profile data)\n")
    print(f"scrub_logcat: {len(lines)} → {len(kept)} lines ({removed} removed) → {dst}")


if __name__ == "__main__":
    main()
