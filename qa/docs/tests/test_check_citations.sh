#!/usr/bin/env bash
set -u
CHECK="${CHECK:-$(cd "$(dirname "$0")/.." && pwd)/check_citations.py}"
pass=0; fail=0
ok() { pass=$((pass+1)); echo "PASS  $1"; }
ko() { fail=$((fail+1)); echo "FAIL  $1"; }
[ -f "$CHECK" ] || { echo "FAIL  check_citations.py not found at $CHECK"; exit 1; }

mk() {
  T="$(mktemp -d)"; mkdir -p "$T/docs" "$T/app/src/main/java/demo/triage"
  {
    echo "package demo.triage"
    for i in $(seq 1 60); do echo "// filler $i"; done
    echo "class StatusResolver {"
    echo "    fun shouldOverwrite(a: Int, b: Int): Boolean = b > a"
    for i in $(seq 1 60); do echo "    // filler $i"; done
    echo "    fun apply(x: Int): Int = x"
    echo "}"
  } > "$T/app/src/main/java/demo/triage/StatusResolver.kt"
  : > "$T/docs/SPECIFICATION_FONCTIONNELLE_ET_PORTAGE_IOS.md"
}
run() { (cd "$T" && python3 "$CHECK" > "$T/out.txt" 2>&1); echo $?; }

mk; echo 'La règle est dans `shouldOverwrite` (`triage/StatusResolver.kt:63`) puis `apply` (`triage/StatusResolver.kt:124`).' > "$T/docs/DOCUMENTATION_UML_FONCTIONNELLE.md"
rc=$(run); [ "$rc" -eq 0 ] && ok "CIT-01 exact citations are accepted" || ko "CIT-01 exact citations are accepted (rc=$rc)"; rm -rf "$T"

mk; echo 'La règle est dans `shouldOverwrite` (`triage/StatusResolver.kt:120`).' > "$T/docs/DOCUMENTATION_UML_FONCTIONNELLE.md"
rc=$(run); [ "$rc" -ne 0 ] && ok "CIT-02 a function cited 57 lines away is refused, even though another word of the sentence is near that line" || ko "CIT-02 a function cited 57 lines away is refused (rc=$rc)"; rm -rf "$T"

mk; echo 'Le relais utilise `triageEndpointId` (`triage/StatusResolver.kt:63`).' > "$T/docs/DOCUMENTATION_UML_FONCTIONNELLE.md"
rc=$(run); [ "$rc" -ne 0 ] && ok "CIT-03 a symbol that exists nowhere in the code is refused" || ko "CIT-03 a symbol that exists nowhere in the code is refused (rc=$rc)"; rm -rf "$T"

mk; echo 'Voir `StatusResolver` (`triage/StatusResolver.kt:3`) et sa méthode `resolveAll`.' > "$T/docs/DOCUMENTATION_UML_FONCTIONNELLE.md"
rc=$(run); [ "$rc" -ne 0 ] && ok "CIT-04 an invented method named in prose without a citation is refused too" || ko "CIT-04 an invented method named in prose without a citation is refused too (rc=$rc)"; rm -rf "$T"

mk; echo 'Voir `shouldOverwrite` (`triage/Missing.kt:10`).' > "$T/docs/DOCUMENTATION_UML_FONCTIONNELLE.md"
rc=$(run); [ "$rc" -ne 0 ] && ok "CIT-05 a citation of a missing file is refused" || ko "CIT-05 a citation of a missing file is refused (rc=$rc)"; rm -rf "$T"

mk; printf '## 8. Proposé — n'"'"'existe pas encore\nInterface cible `TransferHub` et `QrBudgetManager`.\n' > "$T/docs/DOCUMENTATION_UML_FONCTIONNELLE.md"
rc=$(run); [ "$rc" -eq 0 ] && ok "CIT-06 symbols under a heading that says 'Proposé' are not required to exist" || ko "CIT-06 symbols under a heading that says 'Proposé' are not required to exist (rc=$rc)"; rm -rf "$T"

echo "---"; echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
