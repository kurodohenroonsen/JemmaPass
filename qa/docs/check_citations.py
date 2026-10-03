#!/usr/bin/env python3
"""
Verification of Kotlin citations and symbols in documentation:
- docs/DOCUMENTATION_UML_FONCTIONNELLE.md
- docs/SPECIFICATION_FONCTIONNELLE_ET_PORTAGE_IOS.md

Rules checked:
1. Citations `symbol` (`path/file.kt:line`) or `path/file.kt:line`:
   The cited symbol must exist within +-5 lines in the cited file.
2. Uncited code symbols `symbol`:
   Must exist in the Kotlin codebase unless:
   - Inside a section under a heading mentioning 'Proposé' / 'Proposed'
   - In standard types / framework classes / keywords allowlist
3. Fails with non-zero exit code if any citation is shifted, missing file, or symbol invented.
"""

import os
import re
import sys

DOC_FILES = [
    "docs/DOCUMENTATION_UML_FONCTIONNELLE.md",
    "docs/SPECIFICATION_FONCTIONNELLE_ET_PORTAGE_IOS.md",
]

KNOWN_EXTERNAL_SYMBOLS = {
    # Kotlin / Java primitives & standard library
    "String", "Int", "Long", "Boolean", "Float", "Double", "Byte", "Short", "Char", "Unit", "Any", "Nothing",
    "ByteArray", "IntArray", "List", "Map", "Set", "ArrayList", "HashMap", "HashSet", "Array", "Pair", "Triple",
    "Flow", "StateFlow", "SharedFlow", "CoroutineScope", "Dispatchers", "Job", "Deferred", "Result",
    "File", "InputStream", "OutputStream", "BufferedReader", "StringBuilder", "Exception", "Throwable",
    "val", "var", "fun", "class", "object", "interface", "enum", "override", "private", "public", "protected",
    "internal", "open", "abstract", "sealed", "data", "inline", "suspend", "tailrec", "operator", "infix",
    "null", "true", "false", "this", "super", "return", "continue", "break", "throw", "try", "catch", "finally",
    "if", "else", "when", "while", "do", "for", "in", "is", "as", "by", "get", "set", "import", "package",

    # Android framework
    "Context", "Application", "Activity", "Fragment", "ViewModel", "LiveData", "LifecycleOwner", "Intent", "Bundle",
    "SharedPreferences", "View", "TextView", "Button", "EditText", "Toast", "Log", "Uri", "Bitmap", "Canvas",
    "BluetoothAdapter", "BluetoothDevice", "BluetoothGatt", "BluetoothGattCallback", "BluetoothGattServer",
    "BluetoothGattCharacteristic", "BluetoothGattDescriptor", "BluetoothManager", "ScanCallback", "ScanResult",
    "Notification", "NotificationManager", "PendingIntent", "JobScheduler", "WorkManager", "Room", "SQLiteDatabase",
    "ForegroundService", "AppWidgetProvider",

    # Apple / iOS frameworks
    "WidgetKit", "HealthKit", "CoreBluetooth", "MultipeerConnectivity", "UIGraphicsPDFRenderer", "AppIntents",
    "SwiftUI", "UIKit", "CBCentralManager", "CBPeripheralManager", "CBPeripheral", "CBCentral", "CBService",
    "CBCharacteristic", "CBAdvertisementData", "MCPeerID", "MCSession", "MCNearbyServiceAdvertiser", "MCNearbyServiceBrowser",
    "HKHealthStore", "HKClinicalRecord", "HKQuantityType", "HKUnit", "LiveActivity", "ActivityKit",
    "NLTokenizer", "VNRecognizeTextRequest", "CIQRCodeGenerator", "AVCaptureMetadataOutput", "URLSessionDownloadTask",

    # FHIR / Standards / Formats / SQL / AI
    "FHIR", "HL7", "IPS", "LOINC", "SNOMED", "ATC", "ICD10", "DCI", "INN", "JSON", "XML", "CBOR", "UUID", "URI", "URL",
    "LiteRT", "TensorFlow", "MediaPipe", "FTS5", "SQLite", "trigram", "fts5", "unicode61", "ascii",
    "BLE", "NFC", "WiFi", "GATT", "ATT", "P2P", "DMAT", "START", "PAT", "PMDA", "MEDIS", "JAHIS", "MyNumber",
    "GET", "POST", "PUT", "DELETE", "HTTP", "HTTPS", "OK", "ERROR", "ALERT", "WARN", "INFO",
    "Tool", "Composable", "OptIn", "Serializable", "Parcelize", "Worker", "Service"
}

def index_codebase(root_dir="."):
    kt_files = {}
    code_words = set()
    for root, _, files in os.walk(root_dir):
        for f in files:
            if f.endswith(".kt"):
                full_path = os.path.join(root, f)
                kt_files[f] = full_path
                rel = os.path.relpath(full_path, root_dir)
                kt_files[rel] = full_path
                parts = rel.split(os.sep)
                for i in range(len(parts)):
                    kt_files["/".join(parts[i:])] = full_path

                try:
                    with open(full_path, "r", encoding="utf-8", errors="replace") as kf:
                        content = kf.read()
                        for ident in re.findall(r'[A-Za-z_][A-Za-z0-9_]*', content):
                            code_words.add(ident)
                except Exception:
                    pass
    return kt_files, code_words

def parse_line_citations(line_text):
    citations = []
    p = re.compile(r'([\w/\.-]+\.kt):([0-9,\-]+)')
    for m in p.finditer(line_text):
        kt_path = m.group(1)
        lines_str = m.group(2)
        before = line_text[:m.start()]
        # Get immediate preceding backtick identifier if close to citation
        bt_matches = list(re.finditer(r'`([a-zA-Z_][a-zA-Z0-9_]*)`', before))
        symbol = None
        if bt_matches:
            last_bt = bt_matches[-1]
            intervening = before[last_bt.end():]
            # Check if intervening text is just punctuation, parentheses, quotes or short words
            if len(intervening) < 35 and not any(k in intervening for k in ['.kt:', 'http', 'https']):
                symbol = last_bt.group(1)

        for chunk in lines_str.split(','):
            chunk = chunk.strip()
            if not chunk:
                continue
            if '-' in chunk:
                parts = chunk.split('-')
                if len(parts) == 2 and parts[0].isdigit() and parts[1].isdigit():
                    citations.append((symbol, kt_path, int(parts[0]), int(parts[1])))
            elif chunk.isdigit():
                citations.append((symbol, kt_path, int(chunk), int(chunk)))
    return citations

def main():
    root_dir = "."
    kt_index, code_words = index_codebase(root_dir)

    total_citations = 0
    exact_count = 0
    shifted = []
    missing_files = []
    invented_symbols = []

    for doc_path in DOC_FILES:
        if not os.path.exists(doc_path):
            continue

        with open(doc_path, "r", encoding="utf-8") as f:
            lines = f.readlines()

        is_proposed = False

        for doc_lno, line in enumerate(lines, 1):
            line_str = line.strip()
            # Check for section heading
            if line_str.startswith("#"):
                header_text = line_str.lstrip("#").strip()
                if re.search(r'propos[ée]|proposed', header_text, re.I):
                    is_proposed = True
                else:
                    is_proposed = False

            if is_proposed:
                continue

            # 1. Check citations on this line
            citations = parse_line_citations(line)
            cited_symbols = set()
            for symbol, kt_path, start_line, end_line in citations:
                total_citations += 1
                if symbol:
                    cited_symbols.add(symbol)
                kt_base = os.path.basename(kt_path)

                target_file = kt_index.get(kt_path) or kt_index.get(kt_base)
                if not target_file or not os.path.exists(target_file):
                    missing_files.append({
                        "doc": doc_path,
                        "line": doc_lno,
                        "citation": f"{kt_path}:{start_line}",
                        "file": kt_path,
                    })
                    continue

                with open(target_file, "r", encoding="utf-8") as tf:
                    tlines = tf.readlines()

                w_start = max(1, start_line - 5)
                w_end = min(len(tlines), end_line + 5)
                window_content = "".join(tlines[w_start - 1 : w_end])

                if symbol:
                    if symbol in window_content:
                        exact_count += 1
                    else:
                        shifted.append({
                            "doc": doc_path,
                            "line": doc_lno,
                            "citation": f"{kt_path}:{start_line}",
                            "symbol": symbol,
                            "window": f"{w_start}-{w_end}",
                            "target_file": target_file
                        })
                else:
                    # Generic line citation without specific symbol
                    exact_count += 1

            # 2. Check un-cited code symbols on this line
            all_backticks = re.findall(r'`([^`]+)`', line)
            for bt in all_backticks:
                bt_clean = bt.strip()
                if " " in bt_clean or "/" in bt_clean or "." in bt_clean or ":" in bt_clean:
                    continue
                # Skip clinical / ATC codes (e.g. M01AE19, B01AF03, N02BE01, M01A)
                if re.match(r'^[A-Z][0-9]{2}[A-Z]{1,2}[0-9]{0,2}$', bt_clean):
                    continue
                # Skip numeric constants, hex constants (e.g. 0xFFB91C1C)
                if re.match(r'^(?:0x[0-9A-Fa-f]+|[0-9]+[A-Za-z0-9_-]*)$', bt_clean):
                    continue
                if not re.match(r'^[a-zA-Z_][a-zA-Z0-9_]*$', bt_clean):
                    continue
                if bt_clean in KNOWN_EXTERNAL_SYMBOLS:
                    continue
                if bt_clean.isupper() and len(bt_clean) <= 6:
                    continue
                if bt_clean in cited_symbols:
                    continue

                # Check if this symbol exists in code
                if bt_clean not in code_words:
                    invented_symbols.append({
                        "doc": doc_path,
                        "line": doc_lno,
                        "symbol": bt_clean,
                        "text": line_str[:80]
                    })

    print(f"=== Rapport de Vérification des Citations de Code ===")
    print(f"Total citations analysées : {total_citations}")
    print(f"Citations exactes (±5 lignes) : {exact_count}")
    print(f"Citations décalées : {len(shifted)}")
    print(f"Fichiers manquants : {len(missing_files)}")
    print(f"Symboles inventés : {len(invented_symbols)}")

    if missing_files or shifted or invented_symbols:
        if missing_files:
            print("\n--- Fichiers Manquants ---")
            for m in missing_files:
                print(f"  [{m['doc']}:{m['line']}] {m['citation']} -> fichier introuvable: {m['file']}")
        if shifted:
            print("\n--- Citations Décalées ---")
            for s in shifted:
                print(f"  [{s['doc']}:{s['line']}] {s['citation']} -> symbole `{s['symbol']}` absent des lignes {s['window']} de {os.path.basename(s['target_file'])}")
        if invented_symbols:
            print("\n--- Symboles Inventés ---")
            for inv in invented_symbols[:10]:
                print(f"  [{inv['doc']}:{inv['line']}] `{inv['symbol']}` n'existe nulle part dans le code : {inv['text']}")
        sys.exit(1)
    else:
        print("\nSUCCÈS : 0 décalage, 0 symbole inventé.")
        sys.exit(0)

if __name__ == "__main__":
    main()
