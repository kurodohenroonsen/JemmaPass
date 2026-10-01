#!/usr/bin/env python3
"""
build_gallery.py — deterministic screen gallery for docs / user guide / design (no image analysis).

  python3 qa/device/build_gallery.py <device-reports checkout>

Scans <root>/feat-*/<commit>-<stamp>/screenshots/*.png, classifies each capture from its FILE NAME,
keeps the newest capture per name and writes <root>/screens/INDEX.md: one section per pillar with
LINKS to the captures kept in the run folders (no copy — the first version doubled the repo size).
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
        shutil.rmtree(screens)          # the gallery is an index of links: no duplicated PNG
    screens.mkdir(parents=True)
    rows = []
    for name, (png, _stamp, run_name) in sorted(newest.items()):
        rel = "../" + png.relative_to(root).as_posix()
        rows.append((pillar_of(name), name, rel, run_name.split("-", 1)[0], run_name))
    rows.sort()
    lines = ["# Galerie d'écrans (générée par qa/device/build_gallery.py)", "",
             "Index par pilier vers les captures conservées dans les dossiers de run (aucune copie).", ""]
    current = None
    for pillar, name, rel, commit, run in rows:
        if pillar != current:
            lines += ["", f"## {pillar}", "", "| Capture | Commit | Run d'origine |", "|---|---|---|"]
            current = pillar
        lines.append(f"| [{name}]({rel}) | `{commit}` | {run} |")
    (screens / "INDEX.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(rows)} screens in {len({r[0] for r in rows})} pillars -> {screens / 'INDEX.md'} (links only)")

if __name__ == "__main__":
    main()
