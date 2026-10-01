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
  P7  `_j` has `_j == "1.2"` and `sid == <file id>`
  P8  legacy sections (Allergies 48765-2 · Medications 10160-0) present
      when their `_j` arrays are non-empty

and, for each FHIR-native pillar  💉 im / Immunization · 🏥 pr / Procedure · 📟 dv / DeviceUseStatement(+Device)
· 🧪 rs / Observation (results) · 📜 ph / Condition (past illness, section 11348-0) :
  P4  the `_j.<key>` projection has exactly one entry per resource, same codes (set-wise)
      and same dates (occurrenceDateTime / performedDateTime / timingDateTime ⇄ dt)
  P4b optional expected count (--expect / --expect-pr / --expect-dv / --expect-rs / --expect-ph)
  P5  the Composition has the pillar section (LOINC 11369-6 / 47519-4 / 46264-8) iff there
      are resources, and its entry references are exactly the resource fullUrls
  P6  every resource declares its *-uv-ips profile, references the Patient fullUrl, has a
      status and a date-or-string ; every DeviceUseStatement resolves to a Device entry that
      itself declares Device-uv-ips and references the Patient ; every results Observation has
      a category and one value[x] (valueQuantity with UCUM / valueCodeableConcept / valueString)
      matching the `_j.rs` projection (`v`, `u`) ; every past-problem Condition has a
      clinicalStatus resolved / inactive / remission, no abatement before its onset, and
      the `_j.ph` projection carries the same abatement dates (`ab`) and severities (`sv`)

Optional expectations :
  --expect demo_kurodo=4        (Immunization resources — kept for backward compatibility)
  --expect-im demo_kurodo=4     (same thing, explicit)
  --expect-pr demo_kurodo=2     (Procedure resources)
  --expect-dv demo_haru=2       (DeviceUseStatement resources)
  --expect-rs demo_kurodo=3     (results Observation resources)
  --expect-ph demo_haru=2       (past-problem Condition resources)
  --expect-cn demo_haru=2       (problem-list Condition resources)

Exit code 0 when everything passes, 1 otherwise. --markdown writes a report table.
"""
import argparse
import json
import re
import sys
from pathlib import Path

URN_V3 = re.compile(r"^urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-3[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

IPS = "http://hl7.org/fhir/uv/ips/StructureDefinition/"

# One row per FHIR-native pillar. `date` lists the accepted date fields (first one wins),
# `string` the fallback *String field that must be present when there is no date.
PILLARS = [
    dict(key="im", emoji="💉", label="Immunization", rtype="Immunization", loinc="11369-6",
         profile=IPS + "Immunization-uv-ips", code_path="vaccineCode", patient_path="patient",
         date=("occurrenceDateTime",), string="occurrenceString"),
    dict(key="pr", emoji="🏥", label="Procedure", rtype="Procedure", loinc="47519-4",
         profile=IPS + "Procedure-uv-ips", code_path="code", patient_path="subject",
         date=("performedDateTime",), string="performedString"),
    dict(key="dv", emoji="📟", label="DeviceUseStatement", rtype="DeviceUseStatement", loinc="46264-8",
         profile=IPS + "DeviceUseStatement-uv-ips", code_path=None, patient_path="subject",
         date=("timingDateTime",), string=None),
    dict(key="rs", emoji="🧪", label="Observation", rtype="Observation", loinc="30954-2",
         profile=IPS + "Observation-results", code_path="code", patient_path="subject",
         date=("effectiveDateTime",), string=None, profile_prefix=True),
    dict(key="pg", emoji="🤰", label="Observation (pregnancy)", rtype="Observation", loinc="10162-6",
         profile=IPS + "Observation-pregnancy", code_path="code", patient_path="subject",
         date=("effectiveDateTime",), string=None, profile_prefix=True),
    dict(key="cn", emoji="🩺", label="Condition (problem list)", rtype="Condition", loinc="11450-4",
         profile=IPS + "Condition-uv-ips", code_path="code", patient_path="subject",
         date=("onsetDateTime",), string=None),
    dict(key="ph", emoji="📜", label="Condition", rtype="Condition", loinc="11348-0",
         profile=IPS + "Condition-uv-ips", code_path="code", patient_path="subject",
         date=("onsetDateTime",), string=None),
]
PAST_STATUSES = ("resolved", "inactive", "remission")
PREGNANCY_CODES = {"82810-3", "11778-8", "11779-6", "11780-4", "11640-0", "11636-8", "11639-2", "11637-6",
                   "11638-4", "11612-9", "11614-5", "11613-7", "33065-4"}
CURRENT_STATUSES = ("active", "recurrence", "relapse")
PROBLEMS_LOINC = "11450-4"
PAST_ILLNESS_LOINC = "11348-0"
PROFILE_DEVICE_UV_IPS = IPS + "Device-uv-ips"
RESULT_CATEGORIES = ("laboratory", "imaging", "procedure")
UCUM = "http://unitsofmeasure.org"


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


def entries_of_type(bundle, rtype):
    return [e for e in bundle.get("entry", []) if (e.get("resource") or {}).get("resourceType") == rtype]


def coding_code(cc):
    for c in (cc or {}).get("coding", []) or []:
        if c.get("code"):
            return c["code"]
    return None


def resolve_device(bundle, use_statement):
    """Device referenced by a DeviceUseStatement: by fullUrl first, then by `Device/{id}`."""
    ref = ((use_statement.get("device") or {}).get("reference")) or ""
    for e in entries_of_type(bundle, "Device"):
        if e.get("fullUrl") == ref:
            return e["resource"]
    if ref.startswith("Device/"):
        wanted = ref[len("Device/"):]
        for e in entries_of_type(bundle, "Device"):
            if e["resource"].get("id") == wanted:
                return e["resource"]
    return None


def is_results_observation(r):
    profiles = (r.get("meta") or {}).get("profile") or []
    if any("Observation-results" in p for p in profiles):
        return True
    cats = [c.get("code") for cc in (r.get("category") or []) for c in (cc.get("coding") or [])]
    return any(c in RESULT_CATEGORIES for c in cats)


def past_section_refs(bundle):
    comp = (bundle.get("entry") or [{}])[0].get("resource") or {}
    return {r.get("reference") for s in (comp.get("section") or []) if coding_code(s.get("code")) == PAST_ILLNESS_LOINC
            for r in (s.get("entry") or [])}


def section_refs(bundle, loinc):
    comp = (bundle.get("entry") or [{}])[0].get("resource") or {}
    return {r.get("reference") for s in (comp.get("section") or []) if coding_code(s.get("code")) == loinc
            for r in (s.get("entry") or [])}


def is_problem(bundle, entry):
    """Mirror of IpsFhirCodec.isProblemCondition: in the 11450-4 section, or IPS profile + current status."""
    if entry.get("fullUrl") in section_refs(bundle, PROBLEMS_LOINC):
        return True
    r = entry.get("resource") or {}
    prof = (r.get("meta") or {}).get("profile") or []
    return IPS + "Condition-uv-ips" in prof and coding_code(r.get("clinicalStatus")) in CURRENT_STATUSES


def is_past_problem(bundle, entry):
    """Mirror of IpsFhirCodec.isPastProblemCondition: in the 11348-0 section, or IPS profile + past status."""
    if entry.get("fullUrl") in past_section_refs(bundle):
        return True
    r = entry.get("resource") or {}
    prof = (r.get("meta") or {}).get("profile") or []
    return IPS + "Condition-uv-ips" in prof and coding_code(r.get("clinicalStatus")) in PAST_STATUSES


def observation_value(r):
    """(kind, value-as-text, unit) of a results Observation, mirroring IpsResult.valueLabel()."""
    if "valueQuantity" in r:
        q = r["valueQuantity"]
        v = q.get("value")
        txt = ("%s" % v).rstrip("0").rstrip(".") if isinstance(v, float) else str(v)
        return "quantity", txt, q.get("code") or q.get("unit")
    if "valueCodeableConcept" in r:
        cc = r["valueCodeableConcept"]
        coding = (cc.get("coding") or [{}])[0]
        return "coded", coding.get("display") or cc.get("text") or coding.get("code"), None
    if "valueString" in r:
        return "string", r["valueString"], None
    return None, None, None


def verify_pillar(pid, b, j, entries, patient_url, spec, rep, expected):
    key, label, loinc = spec["key"], spec["label"], spec["loinc"]
    res_entries = entries_of_type(b, spec["rtype"])
    if key == "rs":
        res_entries = [e for e in res_entries if is_results_observation(e["resource"])]
    if key == "pg":
        res_entries = [e for e in res_entries if coding_code(e["resource"].get("code")) in PREGNANCY_CODES]
    if spec["rtype"] == "Condition":
        keep = is_problem if key == "cn" else is_past_problem
        res_entries = [e for e in res_entries if keep(b, e)]
    res = [e["resource"] for e in res_entries]
    jarr = j.get(key, []) or []

    def res_code(r):
        if spec["code_path"]:
            return coding_code(r.get(spec["code_path"])) or ""
        dev = resolve_device(b, r)
        return coding_code((dev or {}).get("type")) or ""

    def res_date(r):
        for f in spec["date"]:
            if r.get(f):
                return r[f]
        return ""

    # P4 projection ⇄ resources
    codes_fhir = sorted(res_code(r) for r in res)
    codes_j = sorted(e.get("c") or "" for e in jarr)
    dates_fhir = sorted(res_date(r) for r in res)
    dates_j = sorted(e.get("dt") or "" for e in jarr)
    name = f"P4 `_j.{key}` projection ⇄ {label} resources"
    if len(res) == len(jarr) and codes_fhir == codes_j and dates_fhir == dates_j:
        rep.ok(pid, name, f"{len(res)} {key}")
    else:
        rep.fail(pid, name, f"fhir={len(res)}/{codes_fhir}/{dates_fhir} j={len(jarr)}/{codes_j}/{dates_j}")
    if expected is not None:
        if len(res) == expected:
            rep.ok(pid, f"P4b expected {expected} {label}")
        else:
            rep.fail(pid, f"P4b expected {expected} {label}", f"found {len(res)}")

    # P5 section
    comp = entries[0]["resource"]
    sections = comp.get("section", []) or []
    secs = [s for s in sections if coding_code(s.get("code")) == loinc]
    urls = sorted(e.get("fullUrl") for e in res_entries)
    if res:
        refs = sorted(r.get("reference") for r in (secs[0].get("entry", []) if secs else []))
        name = f"P5 Composition section {loinc} → {label} fullUrls"
        if len(secs) == 1 and refs == urls:
            rep.ok(pid, name, f"{len(refs)} refs")
        else:
            rep.fail(pid, name, f"sections={len(secs)} refs={refs} urls={urls}")
    else:
        name = f"P5 no {loinc} section when there are no {label}"
        if not secs:
            rep.ok(pid, name)
        else:
            rep.fail(pid, name, "section present")

    # P6 conformance
    bad = []
    for r in res:
        prof = (r.get("meta") or {}).get("profile") or []
        pref = (r.get(spec["patient_path"]) or {}).get("reference")
        has_profile = any(p.startswith(spec["profile"]) for p in prof) if spec.get("profile_prefix") else spec["profile"] in prof
        if not has_profile:
            bad.append(f"{r.get('id')}:no-ips-profile")
        if pref != patient_url:
            bad.append(f"{r.get('id')}:patient-ref")
        if spec["rtype"] == "Condition":
            allowed = CURRENT_STATUSES if key == "cn" else PAST_STATUSES
            if coding_code(r.get("clinicalStatus")) not in allowed:
                bad.append(f"{r.get('id')}:clinicalStatus-{coding_code(r.get('clinicalStatus'))}")
            if key == "cn" and r.get("abatementDateTime"):
                bad.append(f"{r.get('id')}:current-problem-with-abatement")
            onset, abate = r.get("onsetDateTime") or "", r.get("abatementDateTime") or ""
            n = min(len(onset), len(abate))
            if onset and abate and abate[:n] < onset[:n]:
                bad.append(f"{r.get('id')}:abatement-before-onset")
            if not (coding_code(r.get("code")) or (r.get("code") or {}).get("text")):
                bad.append(f"{r.get('id')}:no-code-nor-text")
        elif not r.get("status"):
            bad.append(f"{r.get('id')}:no-status")
        if spec["string"] is not None and not (res_date(r) or r.get(spec["string"])):
            bad.append(f"{r.get('id')}:no-date-nor-string")
        if spec["rtype"] == "DeviceUseStatement":
            dev = resolve_device(b, r)
            if dev is None:
                bad.append(f"{r.get('id')}:device-unresolved")
            else:
                dprof = (dev.get("meta") or {}).get("profile") or []
                if PROFILE_DEVICE_UV_IPS not in dprof:
                    bad.append(f"{dev.get('id')}:device-no-ips-profile")
                if (dev.get("patient") or {}).get("reference") != patient_url:
                    bad.append(f"{dev.get('id')}:device-patient-ref")
                if not (coding_code(dev.get("type")) or (dev.get("type") or {}).get("text") or dev.get("deviceName")):
                    bad.append(f"{dev.get('id')}:device-no-type")
        if key == "pg" and not any(k in r for k in ("valueCodeableConcept", "valueDateTime", "valueInteger")):
            bad.append(f"{r.get('id')}:no-value")
        if key == "rs":
            cats = [c.get("code") for cc in (r.get("category") or []) for c in (cc.get("coding") or [])]
            if not any(c in RESULT_CATEGORIES for c in cats):
                bad.append(f"{r.get('id')}:no-category")
            kind, vtxt, unit = observation_value(r)
            if kind is None:
                bad.append(f"{r.get('id')}:no-value")
            elif kind == "quantity" and unit and (r["valueQuantity"].get("system") != UCUM):
                bad.append(f"{r.get('id')}:unit-not-ucum")
    what = "profile · patient ref · status · date/string" + (" · Device resolved + Device-uv-ips" if key == "dv" else "") \
        + (" · category · value[x]" if key == "rs" else "") \
        + (" · past clinicalStatus · onset ≤ abatement" if key == "ph" else "") \
        + (" · current clinicalStatus · no abatement" if key == "cn" else "")
    name = f"P6 {label}-uv-ips {what}"
    if res and not bad:
        rep.ok(pid, name)
    elif res:
        rep.fail(pid, name, ", ".join(map(str, bad))[:200])

    if key == "dv":
        n_dev = len(entries_of_type(b, "Device"))
        if n_dev != len(res):
            rep.fail(pid, "P6b one Device per DeviceUseStatement", f"devices={n_dev} statements={len(res)}")

    if key == "rs" and res:
        # P6c: the projection carries the same values (multiset of "value unit").
        fhir_vals = sorted(f"{observation_value(r)[1] or ''}|{observation_value(r)[2] or ''}" for r in res)
        j_vals = sorted(f"{e.get('v') or ''}|{e.get('u') or ''}" for e in jarr)
        if fhir_vals == j_vals:
            rep.ok(pid, "P6c `_j.rs` values ⇄ Observation value[x]", f"{len(res)} values")
        else:
            rep.fail(pid, "P6c `_j.rs` values ⇄ Observation value[x]", f"fhir={fhir_vals} j={j_vals}")


    if key == "cn" and res:
        # P6e: the legacy projection keeps clinicalStatus in `st`.
        fhir_st = sorted(coding_code(r.get("clinicalStatus")) or "" for r in res)
        j_st = sorted(e.get("st") or "" for e in jarr)
        name = "P6e `_j.cn` st ⇄ Condition clinicalStatus"
        if fhir_st == j_st:
            rep.ok(pid, name, f"{len(res)} problems")
        else:
            rep.fail(pid, name, f"fhir={fhir_st} j={j_st}")

    if key == "ph" and res:
        # P6d: the projection carries the same abatement dates and severities.
        fhir_ab = sorted(f"{r.get('abatementDateTime') or ''}|{coding_code(r.get('severity')) or ''}" for r in res)
        j_ab = sorted(f"{e.get('ab') or ''}|{e.get('sv') or ''}" for e in jarr)
        name = "P6d `_j.ph` ab/sv ⇄ Condition abatement/severity"
        if fhir_ab == j_ab:
            rep.ok(pid, name, f"{len(res)} problems")
        else:
            rep.fail(pid, name, f"fhir={fhir_ab} j={j_ab}")


def verify_profile(pid, folder, rep, expected):
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

    # P4–P6 per native pillar
    for spec in PILLARS:
        verify_pillar(pid, b, j, entries, patient_url, spec, rep, expected.get(spec["key"]))

    # P8: legacy sections still present when their arrays are non-empty
    comp = entries[0]["resource"]
    sections = comp.get("section", []) or []
    for key, loinc, label in (("al", "48765-2", "Allergies"), ("md", "10160-0", "Medications")):
        if j.get(key):
            present = any(coding_code(s.get("code")) == loinc for s in sections)
            (rep.ok if present else rep.fail)(pid, f"P8 legacy section {label} ({loinc}) present", f"{len(j.get(key))} entries")


def parse_expectations(items, key, into):
    for item in items:
        k, v = item.split("=", 1)
        into.setdefault(k, {})[key] = int(v)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("folder", help="pulled profiles folder")
    ap.add_argument("--expect", action="append", default=[], help="<profileId>=<n Immunization>, repeatable (alias of --expect-im)")
    ap.add_argument("--expect-im", action="append", default=[], help="<profileId>=<n Immunization>, repeatable")
    ap.add_argument("--expect-pr", action="append", default=[], help="<profileId>=<n Procedure>, repeatable")
    ap.add_argument("--expect-dv", action="append", default=[], help="<profileId>=<n DeviceUseStatement>, repeatable")
    ap.add_argument("--expect-rs", action="append", default=[], help="<profileId>=<n results Observation>, repeatable")
    ap.add_argument("--expect-pg", action="append", default=[], help="<profileId>=<n pregnancy Observation>, repeatable")
    ap.add_argument("--expect-cn", action="append", default=[], help="<profileId>=<n problem-list Condition>, repeatable")
    ap.add_argument("--expect-ph", action="append", default=[], help="<profileId>=<n past-problem Condition>, repeatable")
    ap.add_argument("--only", action="append", default=[], help="restrict to these profile ids (repeatable)")
    ap.add_argument("--markdown", help="write the report table to this file")
    ap.add_argument("--title", default="verify_profiles")
    args = ap.parse_args()

    folder = Path(args.folder)
    expectations = {}
    parse_expectations(args.expect + args.expect_im, "im", expectations)
    parse_expectations(args.expect_pr, "pr", expectations)
    parse_expectations(args.expect_dv, "dv", expectations)
    parse_expectations(args.expect_rs, "rs", expectations)
    parse_expectations(args.expect_ph, "ph", expectations)
    parse_expectations(args.expect_cn, "cn", expectations)
    parse_expectations(args.expect_pg, "pg", expectations)

    ids = sorted(p.name[:-5] for p in folder.glob("*.json")
                 if not p.name.endswith(".fhir.json") and p.name != "meta.json")
    if args.only:
        ids = [i for i in ids if i in args.only]
    rep = Report()
    if not ids:
        rep.fail("-", "profiles folder", f"no profile found in {folder}")
    for pid in ids:
        verify_profile(pid, folder, rep, expectations.get(pid, {}))
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
