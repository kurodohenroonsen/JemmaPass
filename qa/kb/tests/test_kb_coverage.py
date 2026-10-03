#!/usr/bin/env python3
"""Acceptance test of a knowledge base build (task 0055). Not to be edited by the implementer.

Usage:  KB=/path/knowledge_full.db KB_OLD=/path/knowledge_full_1.1.db python3 qa/kb/tests/test_kb_coverage.py
        COV=docs/analysis/kb-only-evidence/coverage.tsv (default)

Expected: red on the current base (build_version 2.0-omnis), green on the next build. Exit code 0 only when every check passes.
Read-only: both databases are opened with mode=ro.
"""
import csv, os, sqlite3, sys

KB, OLD = os.environ.get("KB"), os.environ.get("KB_OLD")
COV = os.environ.get("COV", "docs/analysis/kb-only-evidence/coverage.tsv")
NOT_A_CODE = {"0123456789"}                      # digit alphabet in sos/JemmaDeviceId.kt, not a concept
KEYS = {                                           # tables that must only grow, with their row key
    "atc_hierarchy": "atc_code",
    "ips_valuesets": "vs_id || '|' || code || '|' || code_system",
    "ips_valuesets_translations": "vs_id || '|' || code || '|' || code_system || '|' || lang",
    "terminology_codes": "code",
}
PROVENANCE_COLS = {"table_name", "row_key", "source", "source_version", "fetched_on"}
results = []

def check(cid, title, ok, detail=""):
    results.append(ok)
    print(("PASS  " if ok else "FAIL  ") + cid + " " + title + ("" if ok or not detail else "\n        " + detail))

def ro(path):
    return sqlite3.connect("file:" + path + "?mode=ro", uri=True)

def one(db, sql, args=()):
    return db.execute(sql, args).fetchone()[0]

def tables(db, schema="main"):
    return {r[0] for r in db.execute("SELECT name FROM %s.sqlite_master WHERE type IN ('table','view')" % schema)}

if not KB or not os.path.isfile(KB):
    print("FAIL  KBC-00 KB=<path to the new database> is required and must exist"); sys.exit(1)
if not os.path.isfile(COV):
    print("FAIL  KBC-00 coverage file not found: " + COV); sys.exit(1)
db = ro(KB)
rows = [r for r in csv.reader(open(COV, encoding="utf-8"), delimiter="\t")][1:]
lits = sorted({(r[1], r[2]) for r in rows if r[1] not in NOT_A_CODE})

# KBC-01 every clinical literal still written in the Kotlin source exists in the KB
missing = []
for code, system in lits:
    if system.endswith("/atc"):
        n = one(db, "SELECT count(*) FROM atc_hierarchy WHERE atc_code=?", (code,))
    else:
        n = one(db, "SELECT (SELECT count(*) FROM ips_valuesets WHERE code=?) + "
                    "(SELECT count(*) FROM terminology_codes WHERE code=? OR snomed_code=?)", (code, code, code))
    if n == 0:
        missing.append(code)
check("KBC-01", "every clinical code of coverage.tsv is in the KB (%d distinct codes)" % len(lits),
      not missing, "%d missing: %s" % (len(missing), ", ".join(missing[:40])))

# KBC-02 the build declares itself: a new build_version and a later build_timestamp than the previous KB
def meta(path):
    d = ro(path)
    try:
        return dict(d.execute("SELECT key, value FROM build_metadata").fetchall())
    except sqlite3.Error:
        return {}
m_new, m_old = meta(KB), (meta(OLD) if OLD and os.path.isfile(OLD) else None)
if m_old is None:
    check("KBC-02", "build_metadata declares a new version", False, "KB_OLD is required")
else:
    v_new, v_old = m_new.get("build_version"), m_old.get("build_version")
    t_new, t_old = m_new.get("build_timestamp") or "", m_old.get("build_timestamp") or ""
    check("KBC-02", "build_metadata declares a new build_version and a later build_timestamp",
          bool(v_new) and v_new != v_old and t_new > t_old,
          "build_version %r -> %r ; build_timestamp %r -> %r" % (v_old, v_new, t_old, t_new))

# KBC-03 / KBC-04 need the previous database
if not OLD or not os.path.isfile(OLD):
    check("KBC-03", "nothing removed since the previous KB", False, "KB_OLD=<path to knowledge_full.db 1.1> is required")
    check("KBC-04", "every added row has a provenance", False, "KB_OLD is required")
else:
    db.execute("ATTACH DATABASE ? AS old", ("file:" + OLD + "?mode=ro",))
    lost_tables = sorted(t for t in tables(db, "old") if t not in tables(db) and not t.startswith("sqlite_"))
    lost_rows = {t: one(db, "SELECT count(*) FROM (SELECT %s FROM old.%s EXCEPT SELECT %s FROM main.%s)" % (k, t, k, t))
                 for t, k in KEYS.items() if t in tables(db)}
    lost_rows = {t: n for t, n in lost_rows.items() if n}
    lost_cols = []
    for t in sorted(tables(db, "old") & tables(db)):
        oc = {r[1] for r in db.execute("PRAGMA old.table_info(%s)" % t)}
        nc = {r[1] for r in db.execute("PRAGMA main.table_info(%s)" % t)}
        lost_cols += ["%s.%s" % (t, c) for c in sorted(oc - nc)]
    check("KBC-03", "nothing removed since the previous KB (tables, columns, rows of %d key tables)" % len(KEYS),
          not lost_tables and not lost_rows and not lost_cols,
          "tables lost: %s ; rows lost: %s ; columns lost: %s" % (lost_tables, lost_rows, lost_cols[:20]))

    has_prov = "kb_provenance" in tables(db) and PROVENANCE_COLS <= {r[1] for r in db.execute("PRAGMA main.table_info(kb_provenance)")}
    if not has_prov:
        check("KBC-04", "every added row has a provenance", False,
              "table kb_provenance(%s) is missing" % ", ".join(sorted(PROVENANCE_COLS)))
    else:
        orphans, added = {}, 0
        for t, k in KEYS.items():
            added += one(db, "SELECT count(*) FROM (SELECT %s FROM main.%s EXCEPT SELECT %s FROM old.%s)" % (k, t, k, t))
            n = one(db, "SELECT count(*) FROM (SELECT %s AS rk FROM main.%s EXCEPT SELECT %s FROM old.%s) a "
                        "WHERE NOT EXISTS (SELECT 1 FROM kb_provenance p WHERE p.table_name=? AND p.row_key=a.rk "
                        "AND trim(coalesce(p.source,''))<>'' AND trim(coalesce(p.source_version,''))<>'' "
                        "AND trim(coalesce(p.fetched_on,''))<>'')" % (k, t, k, t), (t,))
            if n:
                orphans[t] = n
        check("KBC-04", "every added row has a provenance (%d rows added)" % added, added > 0 and not orphans,
              "no row added" if added == 0 else "rows without source/version/date: %s" % orphans)

# KBC-05 no translation is a copy of the English label (a missing label must stay missing)
codes = [c for c, s in lits if not s.endswith("/atc")]
q = ",".join("?" * len(codes))
copies = db.execute("SELECT t.lang, t.code, t.display FROM ips_valuesets_translations t JOIN ips_valuesets v "
                    "ON v.vs_id=t.vs_id AND v.code=t.code AND v.code_system=t.code_system "
                    "WHERE t.lang IN ('fr','ja') AND t.code IN (%s) AND lower(trim(t.display))=lower(trim(v.display_en)) "
                    "AND length(v.display_en) > 6" % q, codes).fetchall()
check("KBC-05", "no French or Japanese label is the English label copied",
      not copies, "%d copies, e.g. %s" % (len(copies), copies[:5]))

# KBC-06 one drug, two international names: both resolve to the same ATC code
def resolves(name, atc):
    return one(db, "SELECT (SELECT count(*) FROM ddinter_drugs WHERE lower(name)=? AND (primary_atc=? OR atc_codes LIKE ?)) + "
                   "(SELECT count(*) FROM terminology_codes WHERE lower(primary_display)=? AND atc_code=?)",
               (name, atc, "%" + atc + "%", name, atc)) > 0
bad = [n for n in ("salbutamol", "albuterol") if not resolves(n, "R03AC02")]
check("KBC-06", "salbutamol and albuterol both resolve to R03AC02", not bad, "not resolved: %s" % bad)

# Information only: how many of the codes have an official label per language
for lang in ("fr", "ja"):
    n = one(db, "SELECT count(DISTINCT code) FROM ips_valuesets_translations WHERE lang=? AND code IN (%s)" % q, [lang] + codes)
    print("INFO  %s labels for %d of %d non-ATC codes" % (lang, n, len(codes)))
for col in ("name_fr", "name_jp"):
    print("INFO  atc_hierarchy.%s filled: %d" % (col, one(db, "SELECT count(*) FROM atc_hierarchy WHERE trim(coalesce(%s,''))<>''" % col)))

print("---\n%d passed, %d failed" % (sum(results), len(results) - sum(results)))
sys.exit(0 if all(results) else 1)
