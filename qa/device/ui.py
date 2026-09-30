#!/usr/bin/env python3
"""
ui.py — tiny adb/uiautomator driver for the device QA protocol.

Works on the View-based JEMMA screens (every control has a resource-id).

  ui.py dump                          print the visible nodes (text / desc / id / bounds)
  ui.py find  --id immunizations_fab_add
  ui.py tap   --id immunizations_fab_add [--timeout 15]
  ui.py tap   --text "Kurodo"           (substring, case-insensitive; a clickable
                                        match wins over a non-clickable one, e.g.
                                        a dialog button over its title)
  ui.py tap   --desc "Export"           (content-description)
  ui.py longpress --text "QA Lab"       (1.2 s press = context action on a card)
  ui.py exists --text "IMMUNIZATIONS (4)" [--timeout 15]   → exit 0/1
  ui.py text  --text "IMMUNIZATIONS"    print every node text containing it
  ui.py type  "influenza"               type into the focused field
  ui.py screenshot out/screenshots/03-list.png
  ui.py back | home | wait 3

Environment : ADB_SERIAL to target one device when several are attached.
"""
import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "be.heyman.android.jemmapassdemo"


def adb(*args, capture=True, binary=False):
    cmd = ["adb"]
    serial = os.environ.get("ADB_SERIAL")
    if serial:
        cmd += ["-s", serial]
    cmd += list(args)
    if binary:
        return subprocess.run(cmd, stdout=subprocess.PIPE, check=False).stdout
    r = subprocess.run(cmd, stdout=subprocess.PIPE if capture else None, stderr=subprocess.STDOUT, text=True, check=False)
    return r.stdout or ""


def dump_xml(retries=6):
    for i in range(retries):
        adb("shell", "uiautomator", "dump", "/sdcard/jemma_ui_dump.xml")
        raw = adb("exec-out", "cat", "/sdcard/jemma_ui_dump.xml", binary=True)
        try:
            txt = raw.decode("utf-8", errors="replace")
            start = txt.find("<?xml")
            if start >= 0:
                return ET.fromstring(txt[start:])
        except ET.ParseError:
            pass
        time.sleep(0.8)
    raise SystemExit("ui.py: uiautomator dump failed (screen busy or secure window?)")


def nodes():
    root = dump_xml()
    out = []
    for n in root.iter("node"):
        b = n.get("bounds") or ""
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        out.append({
            "text": n.get("text") or "",
            "desc": n.get("content-desc") or "",
            "id": n.get("resource-id") or "",
            "cls": n.get("class") or "",
            "bounds": (x1, y1, x2, y2),
            "center": ((x1 + x2) // 2, (y1 + y2) // 2),
            "clickable": n.get("clickable") == "true",
        })
    return out


def matches(node, args):
    if args.id:
        rid = node["id"]
        if not (rid == args.id or rid.endswith(":id/" + args.id)):
            return False
    if args.text and args.text.lower() not in node["text"].lower():
        return False
    if args.desc and args.desc.lower() not in node["desc"].lower():
        return False
    return bool(args.id or args.text or args.desc)


def find_node(args, timeout, prefer_clickable=False):
    deadline = time.time() + timeout
    while True:
        found = [n for n in nodes() if matches(n, args)]
        if found:
            idx = getattr(args, "index", 0) or 0
            if prefer_clickable and idx == 0:
                # A dialog title and its button often share the same text :
                # for a tap, a clickable match beats a non-clickable one.
                clickable = [n for n in found if n["clickable"]]
                if clickable:
                    return clickable[0]
            if idx < len(found):
                return found[idx]
            return found[0]
        if time.time() > deadline:
            return None
        time.sleep(1.0)


def describe(n):
    return f"id={n['id'].split('/')[-1] or '-'} text={n['text']!r} desc={n['desc']!r} bounds={n['bounds']} clickable={n['clickable']}"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    def selector(p):
        p.add_argument("--id")
        p.add_argument("--text")
        p.add_argument("--desc")
        p.add_argument("--index", type=int, default=0, help="n-th match (0-based)")
        p.add_argument("--timeout", type=float, default=15.0)

    sub.add_parser("dump")
    selector(sub.add_parser("find"))
    selector(sub.add_parser("tap"))
    selector(sub.add_parser("longpress"))
    selector(sub.add_parser("exists"))
    p = sub.add_parser("text"); p.add_argument("--text", required=True)
    p = sub.add_parser("type"); p.add_argument("value")
    p = sub.add_parser("screenshot"); p.add_argument("path")
    sub.add_parser("back")
    sub.add_parser("home")
    p = sub.add_parser("wait"); p.add_argument("seconds", type=float)
    p = sub.add_parser("launch")
    p = sub.add_parser("stop")

    args = ap.parse_args()

    if args.cmd == "dump":
        for n in nodes():
            if n["text"] or n["desc"] or n["id"]:
                print(describe(n))
        return
    if args.cmd in ("find", "tap", "longpress", "exists"):
        n = find_node(args, args.timeout, prefer_clickable=(args.cmd in ("tap", "longpress")))
        if n is None:
            print(f"ui.py: no node matching id={args.id} text={args.text} desc={args.desc}", file=sys.stderr)
            sys.exit(1)
        if args.cmd == "find":
            print(describe(n)); return
        if args.cmd == "exists":
            print("exists:", describe(n)); return
        x, y = n["center"]
        if args.cmd == "longpress":
            adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "1200")
            print(f"long-pressed ({x},{y}) → {describe(n)}")
        else:
            adb("shell", "input", "tap", str(x), str(y))
            print(f"tapped ({x},{y}) → {describe(n)}")
        time.sleep(0.8)
        return
    if args.cmd == "text":
        hits = [n["text"] for n in nodes() if args.text.lower() in n["text"].lower()]
        for h in hits:
            print(h)
        sys.exit(0 if hits else 1)
    if args.cmd == "type":
        # `input text` needs spaces as %s and shell-safe escaping
        v = args.value.replace(" ", "%s")
        v = re.sub(r"([\\\"'`&|;()<>$])", r"\\\1", v)
        adb("shell", "input", "text", v)
        time.sleep(0.5)
        return
    if args.cmd == "screenshot":
        os.makedirs(os.path.dirname(args.path) or ".", exist_ok=True)
        data = adb("exec-out", "screencap", "-p", binary=True)
        with open(args.path, "wb") as f:
            f.write(data)
        print(f"screenshot → {args.path} ({len(data)} bytes)")
        return
    if args.cmd == "back":
        adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(0.8); return
    if args.cmd == "home":
        adb("shell", "input", "keyevent", "KEYCODE_HOME"); time.sleep(0.8); return
    if args.cmd == "wait":
        time.sleep(args.seconds); return
    if args.cmd == "launch":
        adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity"); time.sleep(3); return
    if args.cmd == "stop":
        adb("shell", "am", "force-stop", PKG); time.sleep(1); return


if __name__ == "__main__":
    main()
