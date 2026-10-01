#!/usr/bin/env python3
"""
extract_json.py — copy one piece of a FHIR Bundle, verbatim, into a file (rule 8).

  python3 qa/device/extract_json.py <bundle.fhir.json> <selector> <out.json>

Selectors:  code:<code>      first resource whose `code` / `vaccineCode` has that coding code
            section:<loinc>  the Composition section with that LOINC code
            id:<resource id> the resource with that id
            type:<Type>      every resource of that type (array)
"""
import json
import sys


def codes(cc):
    return [c.get("code") for c in (cc or {}).get("coding", []) or []]


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    bundle = json.load(open(sys.argv[1], encoding="utf-8"))
    kind, _, value = sys.argv[2].partition(":")
    entries = bundle.get("entry", [])
    found = None
    if kind == "section":
        comp = entries[0]["resource"] if entries else {}
        found = next((s for s in comp.get("section", []) if value in codes(s.get("code"))), None)
    elif kind == "code":
        found = next((e for e in entries if value in codes(e["resource"].get("code")) + codes(e["resource"].get("vaccineCode"))), None)
    elif kind == "id":
        found = next((e for e in entries if e["resource"].get("id") == value), None)
    elif kind == "type":
        found = [e for e in entries if e["resource"].get("resourceType") == value]
    if not found:
        sys.exit(f"nothing matches {sys.argv[2]}")
    text = json.dumps(found, indent=2, ensure_ascii=False)
    open(sys.argv[3], "w", encoding="utf-8").write(text + "\n")
    print(text)


if __name__ == "__main__":
    main()
