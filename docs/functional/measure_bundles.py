#!/usr/bin/env python3
import os
import sys
import json
import zlib

def measure(filepath):
    with open(filepath, "rb") as f:
        raw_bytes = f.read()
    raw_len = len(raw_bytes)
    data = json.loads(raw_bytes.decode("utf-8"))
    minified_str = json.dumps(data, separators=(",", ":"), ensure_ascii=False)
    minified_bytes = minified_str.encode("utf-8")
    minified_len = len(minified_bytes)
    deflate_bytes = zlib.compress(minified_bytes, 9)
    deflate_len = len(deflate_bytes)
    return raw_len, minified_len, deflate_len

def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    out_dir = None
    qa_out = os.path.join(root, "qa", "device", "out")
    if os.path.isdir(qa_out):
        subdirs = sorted(
            [os.path.join(qa_out, d) for d in os.listdir(qa_out) if os.path.isdir(os.path.join(qa_out, d))],
            key=os.path.getmtime,
            reverse=True
        )
        for s in subdirs:
            files_dir = os.path.join(s, "files")
            if os.path.exists(os.path.join(files_dir, "demo_haru.fhir.json")) and os.path.exists(os.path.join(files_dir, "demo_haru.json")):
                out_dir = files_dir
                break

    if not out_dir:
        print("Erreur: Aucun profil demo_*.json trouve dans qa/device/out/*/files", file=sys.stderr)
        sys.exit(1)

    print(f"Source des profils réels mesurés : {out_dir}\n")
    print("| Persona | Profil Médical | Bundle FHIR Formaté | Bundle FHIR Minifié | FHIR Minifié Compressé (DEFLATE) | Profil Compact `_j` Formaté | Profil Compact `_j` Minifié | Profil `_j` Compressé (DEFLATE) |")
    print("|---|---|---|---|---|---|---|---|")

    personas = [
        ("demo_haru", "👵 Haru (32 ressources, pacemaker, anticoagulant)"),
        ("demo_kurodo", "🚶‍♂️ Kurodo (18 ressources, 3 allergies graves, 4 vaccins)"),
        ("demo_kamekichi", "🚶‍♂️ Kamekichi (19 ressources, polymédiqué, 3 cardiopathies)"),
    ]

    for sid, desc in personas:
        fhir_path = os.path.join(out_dir, f"{sid}.fhir.json")
        j_path = os.path.join(out_dir, f"{sid}.json")
        f_raw, f_min, f_def = measure(fhir_path)
        j_raw, j_min, j_def = measure(j_path)
        print(f"| **`{sid}`** | {desc} | **{f_raw} o** | **{f_min} o** | **{f_def} o** | **{j_raw} o** | **{j_min} o** | **{j_def} o** |")

if __name__ == "__main__":
    main()
