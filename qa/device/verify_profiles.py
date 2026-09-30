#!/usr/bin/env python3
"""
verify_profiles.py — device-side invariants of the FHIR-native profile store.

Input : a folder pulled from the device
        (/sdcard/Android/data/be.heyman.android.jemmapassdemo/files/profiles)
        containing {id}.json (JemmaProfileJ, `_j 1.2`) and {id}.fhir.json (FHIR R4 Bundle).

Checks, for every profile :
  P1  both files exist and parse
  P2  Bundle is a `document`, first entry is the Composition, Patient present
  P3  every Bundle.entry has a fullUrl of the form urn:uuid:<UUIDv3>  (deterministic URNs)
  P4  the `_j.im` projection has exactly one entry per Immunization resource,
      same codes (set-wise) and same dates (occurrenceDateTime ⇄ dt)
  P5  the Composition has a section coded LOINC 11369-6 iff there are Immunizations,
      and its entry references are exactly the Immunization fullUrls
  P6  every Immunization declares the Immunization-uv-ips profile and a patient reference
      that resolves to the Patient fullUrl
  P7  `_j` has `_j == "1.2"` and `sid == <file id>`

Optional expectations : --expect demo_kurodo=4 --expect demo_haru=3
(number of Immunization resources for a given profile id).

Exit code 0 when everything passes, 1 otherwise. --markdown writes a report table.
"""
import argparse
import json
import re
import sys
from pathlib import Path

LOINC_IMMUNIZATIONS = "11369-6"
PROFILE_IMMUNIZATION_UV_IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/Immunization-uv-ips"
URN_V3 = re.compile(r"^urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-3[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")


class Report:
    def __init__(self):
        self.rows = []      # (profile, check, status, detail)
        self.failed = 0

    def ok(self, profile, check, detail=""):
        self.rows.append((profile, check, "✅", detail))

    def fail(self, profile, check, detail=""):
        self.rows.append((profile, check, "❌", detail))
        self.failed += 1

    def warn(self, profile, check, detail=""):
        self.rows.append((profile, check, "⚠️", detail))

    def markdown(self, title):
        out = [f"## {title}", "", "| Profile | Check | Status | Detail |", "|---|---|---|---|"]
        for p, c, s, d in self.rows:
            out.append(f"| `{p}` | {c} | {s} | {d.replace('|', '/')} |")
        out.append("")
        out.append(f"**{'PASS' if self.failed == 0 else 'FAIL'}** — {len(self.rows)} checks, {self.failed} failed")
        return "\n".join(out) + "\n"


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def resources(bundle):
    return [e.get("resource") for e in bundle.get("entry", []) if e.get("resource")]


def entries_of_type(bundle, rtype):
    return [e for e in bundle.get("entry", []) if (e.get("resource") or {}).get("resourceType") == rtype]


def coding_code(cc):
    for c in (cc or {}).get("coding", []) or []:
        if c.get("code"):
            return c["code"]
    return None


def verify_profile(pid, folder, rep, expected_immunizations=None):
    jpath = folder / f"{pid}.json"
    fpath = folder / f"{pid}.fhir.json"

    # P1
    if not jpath.exists() or not fpath.exists():
        rep.fail(pid, "P1 files present", f"json={jpath.exists()} fhir={fpath.exists()}")
        return
    try:
        j = load_json(jpath)
        b = load_json(fpath)
    except Exception as e:  # noqa: BLE001
        rep.fail(pid, "P1 files parse", str(e)[:120])
        return
    rep.ok(pid, "P1 files present + parse")

    # P7
    if j.get("_j") == "1.2" and j.get("sid") == pid:
        rep.ok(pid, "P7 `_j`=1.2, sid=file id")
    else:
        rep.fail(pid, "P7 `_j`=1.2, sid=file id", f"_j={j.get('_j')} sid={j.get('sid')}")

    # P2
    entries = b.get("entry", [])
    first_type = (entries[0].get("resource") or {}).get("resourceType") if entries else None
    patients = entries_of_type(b, "Patient")
    if b.get("resourceType") == "Bundle" and b.get("type") == "document" and first_type == "Composition" and len(patients) == 1:
        rep.ok(pid, "P2 document Bundle · Composition first · 1 Patient", f"{len(entries)} entries")
    else:
        rep.fail(pid, "P2 document Bundle · Composition first · 1 Patient",
                 f"resourceType={b.get('resourceType')} type={b.get('type')} first={first_type} patients={len(patients)}")
        return
    patient_url = patients[0].get("fullUrl")

    # P3
    bad = [e.get("fullUrl") for e in entries if not URN_V3.match(e.get("fullUrl") or "")]
    if not bad:
        rep.ok(pid, "P3 deterministic urn:uuid (v3) on every entry")
    else:
        rep.fail(pid, "P3 deterministic urn:uuid (v3) on every entry", f"{len(bad)} bad, e.g. {bad[0]}")
    urls = [e.get("fullUrl") for e in entries]
    if len(urls) != len(set(urls)):
        rep.fail(pid, "P3b fullUrls unique", "duplicates found")

    # P4
    imm_entries = entries_of_type(b, "Immunization")
    imm = [e["resource"] for e in imm_entries]
    jim = j.get("im", []) or []
    codes_fhir = sorted(coding_code(r.get("vaccineCode")) or "" for r in imm)
    codes_j = sorted(e.get("c") or "" for e in jim)
    dates_fhir = sorted(r.get("occurrenceDateTime") or "" for r in imm)
    dates_j = sorted(e.get("dt") or "" for e in jim)
    if len(imm) == len(jim) and codes_fhir == codes_j and dates_fhir == dates_j:
        rep.ok(pid, "P4 `_j.im` projection ⇄ Immunization resources", f"{len(imm)} immunizations")
    else:
        rep.fail(pid, "P4 `_j.im` projection ⇄ Immunization resources",
                 f"fhir={len(imm)}/{codes_fhir}/{dates_fhir} j={len(jim)}/{codes_j}/{dates_j}")
    if expected_immunizations is not None:
        if len(imm) == expected_immunizations:
            rep.ok(pid, f"P4b expected {expected_immunizations} immunizations")
        else:
            rep.fail(pid, f"P4b expected {expected_immunizations} immunizations", f"found {len(imm)}")

    # P5
    comp = entries[0]["resource"]
    sections = comp.get("section", []) or []
    imm_sections = [s for s in sections if coding_code(s.get("code")) == LOINC_IMMUNIZATIONS]
    imm_urls = sorted(e.get("fullUrl") for e in imm_entries)
    if imm:
        refs = sorted(r.get("reference") for r in (imm_sections[0].get("entry", []) if imm_sections else []))
        if len(imm_sections) == 1 and refs == imm_urls:
            rep.ok(pid, "P5 Composition section 11369-6 → Immunization fullUrls", f"{len(refs)} refs")
        else:
            rep.fail(pid, "P5 Composition section 11369-6 → Immunization fullUrls",
                     f"sections={len(imm_sections)} refs={refs} urls={imm_urls}")
    else:
        if not imm_sections:
            rep.ok(pid, "P5 no 11369-6 section when there are no immunizations")
        else:
            rep.fail(pid, "P5 no 11369-6 section when there are no immunizations", "section present")

    # P6
    bad6 = []
    for r in imm:
        prof = (r.get("meta") or {}).get("profile") or []
        pref = (r.get("patient") or {}).get("reference")
        if PROFILE_IMMUNIZATION_UV_IPS not in prof or pref != patient_url:
            bad6.append(r.get("id"))
        if not (r.get("occurrenceDateTime") or r.get("occurrenceString")):
            bad6.append(f"{r.get('id')}:no-occurrence")
        if not r.get("status"):
            bad6.append(f"{r.get('id')}:no-status")
    if imm and not bad6:
        rep.ok(pid, "P6 Immunization-uv-ips profile · patient ref · occurrence · status")
    elif imm:
        rep.fail(pid, "P6 Immunization-uv-ips profile · patient ref · occurrence · status", ", ".join(map(str, bad6))[:160])

    # Extra: legacy sections still present when their arrays are non-empty
    for key, loinc, label in (("al", "48765-2", "Allergies"), ("md", "10160-0", "Medications"), ("cn", "11450-4", "Problems")):
        if j.get(key):
            present = any(coding_code(s.get("code")) == loinc for s in sections)
            (rep.ok if present else rep.fail)(pid, f"P8 legacy section {label} ({loinc}) present", f"{len(j.get(key))} entries")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("folder", help="pulled profiles folder")
    ap.add_argument("--expect", action="append", default=[], help="<profileId>=<n immunizations>, repeatable")
    ap.add_argument("--only", action="append", default=[], help="restrict to these profile ids (repeatable)")
    ap.add_argument("--markdown", help="write the report table to this file")
    ap.add_argument("--title", default="verify_profiles")
    args = ap.parse_args()

    folder = Path(args.folder)
    expectations = {}
    for item in args.expect:
        k, v = item.split("=", 1)
        expectations[k] = int(v)

    ids = sorted(p.name[:-5] for p in folder.glob("*.json")
                 if not p.name.endswith(".fhir.json") and p.name != "meta.json")
    if args.only:
        ids = [i for i in ids if i in args.only]
    rep = Report()
    if not ids:
        rep.fail("-", "profiles folder", f"no profile found in {folder}")
    for pid in ids:
        verify_profile(pid, folder, rep, expectations.get(pid))
    for pid in expectations:
        if pid not in ids:
            rep.fail(pid, "expected profile present", "missing")

    md = rep.markdown(args.title)
    print(md)
    if args.markdown:
        Path(args.markdown).write_text(md, encoding="utf-8")
    sys.exit(0 if rep.failed == 0 else 1)


if __name__ == "__main__":
    main()
