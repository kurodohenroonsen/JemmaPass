#!/usr/bin/env python3
"""
build_gallery.py — deterministic screen gallery for docs / user guide / design (no image analysis).

  python3 qa/device/build_gallery.py <device-reports checkout>

Scans <root>/feat-*/<commit>-<stamp>/screenshots/*.png, classifies each capture from its FILE NAME,
keeps the newest capture per name, copies it to <root>/screens/<pilier>/ and writes screens/INDEX.md
(pilier, capture, commit and run of origin). Re-runnable; the gallery is documentation, not evidence.
"""
import re
import shutil
import sys
from pathlib import Path

RULES = [  # first match wins
    ("alertes", r"dd-|dialog|alert|crosscheck"),
    ("qr", r"qr"),
    ("grossesses", r"pregnan|grossesse"),
    ("antecedents", r"past-problem|rougeole|free-text|antecedent"),
    ("problemes", r"problems|diabete|condition"),
    ("resultats", r"result|imaging|potassium|blood"),
    ("dispositifs", r"device|udi|pacemaker"),
    ("interventions", r"procedure|appendect"),
    ("vaccins", r"immun|vaccin|form-|picker|errors|t[1-9]-|dose|search|c3-|after-|rotation|double-tap|future-date|not-done|cancel"),
    ("fiche", r"detail|profile|pillars|active"),
]


def pillar_of(name):
    low = name.lower()
    for pillar, pattern in RULES:
        if re.search(pattern, low):
            return pillar
    return "divers"


def main():
    root = Path(sys.argv[1])
    runs = sorted(p for p in root.glob("feat-*/*") if (p / "screenshots").is_dir())
    newest = {}
    for run in runs:                      # sorted by commit-stamp folder name; later runs override
        stamp = run.name.split("-", 1)[-1]
        for png in sorted((run / "screenshots").glob("*.png")):
            prev = newest.get(png.name)
            if prev is None or stamp >= prev[1]:
                newest[png.name] = (png, stamp, run.name)
    screens = root / "screens"
    if screens.exists():
        shutil.rmtree(screens)
    rows = []
    for name, (png, _stamp, run_name) in sorted(newest.items()):
        pillar = pillar_of(name)
        dest = screens / pillar / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(png, dest)
        rows.append((pillar, name, run_name.split("-", 1)[0], run_name))
    rows.sort()
    lines = ["# Galerie d'écrans (générée par qa/device/build_gallery.py)", "",
             "| Pilier | Capture | Commit | Run d'origine |", "|---|---|---|---|"]
    lines += [f"| {p} | [{n}]({p}/{n}) | `{c}` | {r} |" for p, n, c, r in rows]
    (screens / "INDEX.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(rows)} screens in {len({r[0] for r in rows})} pillars -> {screens}")


if __name__ == "__main__":
    main()
