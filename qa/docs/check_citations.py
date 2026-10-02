#!/usr/bin/env python3
"""
Verification of Kotlin line citations in documentation:
- docs/DOCUMENTATION_UML_FONCTIONNELLE.md
- docs/SPECIFICATION_FONCTIONNELLE_ET_PORTAGE_IOS.md

Checks that each cited file.kt:line matches the relevant symbol/keyword within +-5 lines.
Returns exit code 0 only if 0 citations are shifted or missing.
"""

import os
import re
import sys

DOC_FILES = [
    "docs/DOCUMENTATION_UML_FONCTIONNELLE.md",
    "docs/SPECIFICATION_FONCTIONNELLE_ET_PORTAGE_IOS.md",
]

STOP_WORDS = {
    "val", "var", "fun", "class", "object", "override", "private", "public",
    "protected", "import", "package", "return", "true", "false", "null", "and",
    "the", "for", "with", "sur", "dans", "avec", "pour", "par", "les", "des",
    "une", "aux", "non", "oui", "est", "que", "qui", "soit", "line", "ligne",
    "lignes", "file", "fichier", "code", "voir", "item", "items", "list", "map",
    "set", "string", "int", "long", "boolean", "any", "format", "version", "vers"
}

def index_kt_files(root_dir="."):
    kt_map = {}
    for root, _, files in os.walk(root_dir):
        for f in files:
            if f.endswith(".kt"):
                full_path = os.path.join(root, f)
                kt_map[f] = full_path
                # Also index relative path suffix
                rel = os.path.relpath(full_path, root_dir)
                kt_map[rel] = full_path
                parts = rel.split(os.sep)
                for i in range(len(parts)):
                    kt_map["/".join(parts[i:])] = full_path
    return kt_map

def extract_candidate_tokens(line_text, kt_base):
    tokens = set()
    base_no_ext = kt_base.replace(".kt", "")
    
    # Mermaid member: +foo(...) or +Type foo or -foo(...)
    for m in re.finditer(r'[+-](?:[a-zA-Z0-9_<>~]+\s+)?([a-zA-Z_0-9]+)(?:\(|\s|=)', line_text):
        name = m.group(1)
        if len(name) >= 3 and name.lower() not in STOP_WORDS:
            tokens.add(name)
            
    # Backtick code snippets: `foo` or `Foo.bar`
    for b in re.findall(r'`([^`]+)`', line_text):
        for part in re.split(r'[^a-zA-Z0-9_]', b):
            if len(part) >= 3 and part.lower() not in STOP_WORDS and not part.isdigit():
                tokens.add(part)
                
    # Function calls or identifiers before citation: foo(bar) or foo.bar
    for ident in re.findall(r'([A-Za-z_][A-Za-z0-9_]{2,})', line_text):
        if ident.lower() not in STOP_WORDS and ident != kt_base.replace(".kt", ""):
            tokens.add(ident)
            
    # Always include the base class name as fallback token
    tokens.add(base_no_ext)
    return tokens

def find_citation_specs(line_text):
    # Matches patterns like:
    #   ProfilesRepository.kt:146
    #   ProfilesRepository.kt:146,371
    #   ai/medscan/MedScanController.kt:343-345
    #   downloads/JemmaModelCatalog.kt:3,80-90
    results = []
    # Regex matches path/file.kt:lines
    pattern = re.compile(r'([\w/\.-]+\.kt):([0-9,\-]+)')
    for m in pattern.finditer(line_text):
        kt_path = m.group(1)
        line_spec = m.group(2)
        # Parse individual line chunks separated by commas
        for chunk in line_spec.split(','):
            chunk = chunk.strip()
            if not chunk:
                continue
            if '-' in chunk:
                parts = chunk.split('-')
                if len(parts) == 2 and parts[0].isdigit() and parts[1].isdigit():
                    results.append((kt_path, int(parts[0]), int(parts[1])))
            elif chunk.isdigit():
                results.append((kt_path, int(chunk), int(chunk)))
    return results

def main():
    kt_index = index_kt_files()
    total_citations = 0
    exact_count = 0
    shifted = []
    missing_files = []

    for doc_path in DOC_FILES:
        if not os.path.exists(doc_path):
            print(f"Error: {doc_path} not found")
            sys.exit(2)

        with open(doc_path, "r", encoding="utf-8") as f:
            lines = f.readlines()

        for doc_lno, line in enumerate(lines, 1):
            specs = find_citation_specs(line)
            if not specs:
                continue

            for kt_path, start_line, end_line in specs:
                total_citations += 1
                kt_base = os.path.basename(kt_path)
                
                # Resolve target file
                target_file = None
                if kt_path in kt_index:
                    target_file = kt_index[kt_path]
                elif kt_base in kt_index:
                    target_file = kt_index[kt_base]

                if not target_file or not os.path.exists(target_file):
                    missing_files.append({
                        "doc": doc_path,
                        "doc_line": doc_lno,
                        "citation": f"{kt_path}:{start_line}-{end_line}" if start_line != end_line else f"{kt_path}:{start_line}",
                        "file": kt_path,
                        "text": line.strip()
                    })
                    continue

                with open(target_file, "r", encoding="utf-8") as tf:
                    target_lines = tf.readlines()

                tokens = extract_candidate_tokens(line, kt_base)
                
                # Check window [start_line - 5, end_line + 5]
                w_start = max(1, start_line - 5)
                w_end = min(len(target_lines), end_line + 5)
                window_content = "".join(target_lines[w_start - 1 : w_end])

                matched_token = None
                for token in tokens:
                    if token in window_content:
                        matched_token = token
                        break

                if matched_token:
                    exact_count += 1
                else:
                    # Find candidate locations in the file for best matching token
                    token_locations = {}
                    for token in tokens:
                        for lidx, tline in enumerate(target_lines, 1):
                            if token in tline:
                                token_locations.setdefault(token, []).append(lidx)

                    shifted.append({
                        "doc": doc_path,
                        "doc_line": doc_lno,
                        "citation": f"{kt_path}:{start_line}-{end_line}" if start_line != end_line else f"{kt_path}:{start_line}",
                        "target_file": target_file,
                        "tokens": list(tokens),
                        "actual_locations": token_locations,
                        "cited_window": f"{w_start}-{w_end}",
                        "text": line.strip()
                    })

    print(f"=== Rapport de Vérification des Citations de Code ===")
    print(f"Total citations analysées : {total_citations}")
    print(f"Citations exactes (±5 lignes) : {exact_count}")
    print(f"Citations décalées : {len(shifted)}")
    print(f"Fichiers manquants : {len(missing_files)}")

    if missing_files:
        print("\n--- Fichiers Manquants ---")
        for m in missing_files:
            print(f"  [{m['doc']}:{m['doc_line']}] {m['citation']} -> fichier introuvable: {m['file']}")

    if shifted:
        print("\n--- Citations Décalées ---")
        for s in shifted:
            print(f"  [{s['doc']}:{s['doc_line']}] {s['citation']}")
            print(f"     Ligne doc : {s['text'][:90]}")
            print(f"     Fenêtre testée : lignes {s['cited_window']} dans {os.path.basename(s['target_file'])}")
            if s['actual_locations']:
                locs_repr = {k: v[:5] for k, v in s['actual_locations'].items()}
                print(f"     Occurrences trouvées : {locs_repr}")
            else:
                print(f"     Aucun token trouvé ({s['tokens'][:5]})")

    if shifted or missing_files:
        sys.exit(1)
    else:
        print("\nSUCCÈS : 0 décalage détecté.")
        sys.exit(0)

if __name__ == "__main__":
    main()
