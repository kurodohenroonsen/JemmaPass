#!/usr/bin/env bash
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"; T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
printf 'fichier_kotlin\tcode\tsystème\tprésent_KB(0/1)\ttable\tlibellé_en\tfr(0/1)\tja(0/1)\n' > "$T/cov.tsv"
printf 'a.kt:1\tM01AE01\thttp://www.whocc.no/atc\t1\t\t\t0\t0\nb.kt:2\t2823-3\thttp://loinc.org\t0\t\t\t0\t0\nc.kt:3\t26643006\thttp://snomed.info/sct\t1\t\t\t0\t0\nd.kt:4\t0123456789\thttp://snomed.info/sct\t0\t\t\t0\t0\n' >> "$T/cov.tsv"
python3 - "$T" <<'PY'
import sqlite3, sys
T = sys.argv[1]
SCHEMA = """
CREATE TABLE atc_hierarchy(atc_code TEXT PRIMARY KEY, parent_atc TEXT, level INTEGER, name_en TEXT, name_fr TEXT, name_jp TEXT, description TEXT);
CREATE TABLE ips_valuesets(vs_id TEXT, code TEXT, code_system TEXT, display_en TEXT, ips_required INTEGER);
CREATE TABLE ips_valuesets_translations(vs_id TEXT, code TEXT, code_system TEXT, lang TEXT, display TEXT);
CREATE TABLE terminology_codes(code TEXT PRIMARY KEY, atc_code TEXT, snomed_code TEXT, primary_display TEXT);
CREATE TABLE ddinter_drugs(ddinter_id TEXT, name TEXT, primary_atc TEXT, atc_codes TEXT);
CREATE TABLE build_metadata(key TEXT, value TEXT);
INSERT INTO atc_hierarchy(atc_code, level, name_en) VALUES('M01AE01', 7, 'ibuprofen');
INSERT INTO terminology_codes VALUES('C1527415', NULL, '26643006', 'Oral Route of Administration');
INSERT INTO ips_valuesets VALUES('routes', '26643006', 'http://snomed.info/sct', 'Oral route', 0);
"""
def make(name, extra):
    db = sqlite3.connect(T + "/" + name); db.executescript(SCHEMA + extra); db.commit(); db.close()
make("old.db", "INSERT INTO build_metadata VALUES('version','1.1');")
GOOD = """
INSERT INTO build_metadata VALUES('version','1.2');
INSERT INTO ips_valuesets VALUES('results', '2823-3', 'http://loinc.org', 'Potassium [Moles/volume] in Serum or Plasma', 0);
INSERT INTO ips_valuesets_translations VALUES('results', '2823-3', 'http://loinc.org', 'fr', 'Potassium [Moles/Volume] Sérum/Plasma');
INSERT INTO ddinter_drugs VALUES('D1', 'Salbutamol', 'R03AC02', 'R03AC02');
INSERT INTO terminology_codes VALUES('X1', 'R03AC02', NULL, 'albuterol');
CREATE TABLE kb_provenance(table_name TEXT, row_key TEXT, source TEXT, source_version TEXT, fetched_on TEXT);
INSERT INTO kb_provenance VALUES('ips_valuesets', 'results|2823-3|http://loinc.org', 'LOINC', '2.78', '2026-10-03');
INSERT INTO kb_provenance VALUES('ips_valuesets_translations', 'results|2823-3|http://loinc.org|fr', 'LOINC fr-FR', '2.78', '2026-10-03');
INSERT INTO kb_provenance VALUES('terminology_codes', 'X1', 'RxNorm', '2026-09', '2026-10-03');
"""
make("good.db", GOOD)
make("copied.db", GOOD + "INSERT INTO ips_valuesets_translations VALUES('results', '2823-3', 'http://loinc.org', 'ja', 'Potassium [Moles/volume] in Serum or Plasma'); INSERT INTO kb_provenance VALUES('ips_valuesets_translations', 'results|2823-3|http://loinc.org|ja', 'x', '1', '2026-10-03');")
make("noprov.db", GOOD + "DELETE FROM kb_provenance WHERE table_name='terminology_codes';")
make("removed.db", GOOD + "DELETE FROM atc_hierarchy; INSERT INTO atc_hierarchy(atc_code, level, name_en) VALUES('M01AE02', 7, 'naproxen');")
PY
pass=0; fail=0
t() { # name expected-rc db expected-FAIL-id
  out="$(KB="$T/$3" KB_OLD="$T/old.db" COV="$T/cov.tsv" python3 "$HERE/test_kb_coverage.py" 2>&1)"; rc=$?
  if { [ "$2" = 0 ] && [ $rc -eq 0 ]; } || { [ "$2" != 0 ] && [ $rc -ne 0 ] && echo "$out" | grep -q "^FAIL  $4"; }; then pass=$((pass+1)); echo "PASS  $1"; else fail=$((fail+1)); echo "FAIL  $1 (rc=$rc)"; echo "$out" | sed 's/^/      /'; fi
}
t "SELF-01 the 1.1-like base is refused for a missing code"        1 old.db     KBC-01
t "SELF-02 a complete additive base with provenance is accepted"  0 good.db    -
t "SELF-03 an English label copied as Japanese is refused"        1 copied.db  KBC-05
t "SELF-04 an added row without provenance is refused"            1 noprov.db  KBC-04
t "SELF-05 a base that lost rows of the previous one is refused"  1 removed.db KBC-03
echo "---"; echo "$pass passed, $fail failed"; [ "$fail" -eq 0 ]
