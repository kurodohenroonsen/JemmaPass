import XCTest
@testable import JemmaCore

final class ContactsVectorsTests: XCTestCase {

    struct VectorFile: Decodable {
        let id: String
        let title: String
        let ui_lang: String?
        let input_j: JemmaProfileJ
        let expect: VectorExpect
    }

    struct VectorExpect: Decodable {
        let patient_contact: [FHIRPatientContact]?
        let text_qr_contains: [String]
        let text_qr_absent: [String]?
    }

    private func findVectorsDirectory() throws -> URL {
        let thisFile = URL(fileURLWithPath: #filePath)
        let rootRepo = thisFile
            .deletingLastPathComponent() // Tests/JemmaCoreTests
            .deletingLastPathComponent() // Tests
            .deletingLastPathComponent() // JemmaCore
            .deletingLastPathComponent() // JemmaPassIOS

        let candidates = [
            rootRepo.appendingPathComponent("qa/vectors/contacts"),
            URL(fileURLWithPath: "/Users/kurodohenroonsen/Documents/jemmapass-contacts/qa/vectors/contacts"),
            URL(fileURLWithPath: FileManager.default.currentDirectoryPath).appendingPathComponent("qa/vectors/contacts")
        ]

        for candidate in candidates {
            var isDir: ObjCBool = false
            if FileManager.default.fileExists(atPath: candidate.path, isDirectory: &isDir), isDir.boolValue {
                return candidate
            }
        }

        XCTFail("Could not locate qa/vectors/contacts directory in any of: \(candidates.map { $0.path })")
        throw NSError(domain: "VectorsNotFound", code: 404)
    }

    func testAllContactsVectors() throws {
        let vectorsDir = try findVectorsDirectory()
        let fileManager = FileManager.default
        let files = try fileManager.contentsOfDirectory(at: vectorsDir, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted(by: { $0.lastPathComponent < $1.lastPathComponent })

        XCTAssertFalse(files.isEmpty, "No vector JSON files found in \(vectorsDir.path)")
        print("Replaying \(files.count) vector files from: \(vectorsDir.path)")

        var replayedCount = 0

        for fileURL in files {
            let data = try Data(contentsOf: fileURL)
            let decoder = JSONDecoder()
            let vector = try decoder.decode(VectorFile.self, from: data)

            let uiLangStr = vector.ui_lang ?? "en"
            let jemmaLang = JemmaLang.from(iso: uiLangStr)

            print("Testing vector [\(vector.id)]: \(vector.title)")

            // 1. Build FHIR Bundle from input_j
            let bundle = JemmaFhirBundleBuilder.build(profile: vector.input_j, uiLang: uiLangStr)

            // Extract Patient resource
            guard let patientEntry = bundle.entry.first(where: {
                if case .patient = $0.resource { return true }
                return false
            }), case .patient(let patient) = patientEntry.resource else {
                XCTFail("[\(vector.id)] Patient resource missing in generated Bundle")
                continue
            }

            // Verify Patient.contact structural match
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]

            let actualContactsData = try encoder.encode(patient.contact ?? [])
            let expectedContactsData = try encoder.encode(vector.expect.patient_contact ?? [])

            let actualJsonObj = try JSONSerialization.jsonObject(with: actualContactsData)
            let expectedJsonObj = try JSONSerialization.jsonObject(with: expectedContactsData)

            assertEqualJson(actualJsonObj, expectedJsonObj, path: "[\(vector.id)].expect.patient_contact")

            // 2. Round-trip verification: contactsOf(bundle)
            let extractedContacts = IpsFhirCodec.contactsOf(bundle: bundle)
            // Filter non-blank input contacts to compare
            let expectedInputContacts = (vector.input_j.p?.ct ?? []).filter { c in
                let n = c.n?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                let p = c.p?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                let e = c.e?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                let adr = c.adr?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                return n != nil || p != nil || e != nil || adr != nil
            }
            XCTAssertEqual(extractedContacts.count, expectedInputContacts.count, "[\(vector.id)] contactsOf(bundle) count mismatch")

            // 3. Render Universal Text QR in uiLang
            let textQR = JemmaTextPayloadBuilder.build(profile: vector.input_j, lang: jemmaLang)

            // Must fit within 1800 UTF-8 bytes
            let qrByteSize = textQR.utf8.count
            XCTAssertLessThanOrEqual(qrByteSize, JemmaTextPayloadBuilder.maxBytes, "[\(vector.id)] QR text size \(qrByteSize) exceeds cap \(JemmaTextPayloadBuilder.maxBytes)")

            // Check text_qr_contains in sequence
            var searchStartIndex = textQR.startIndex
            for expectedString in vector.expect.text_qr_contains {
                guard let foundRange = textQR.range(of: expectedString, range: searchStartIndex..<textQR.endIndex) else {
                    XCTFail("[\(vector.id)] QR text missing expected string: '\(expectedString)'. Full QR text:\n\(textQR)")
                    break
                }
                searchStartIndex = foundRange.upperBound
            }

            // Check text_qr_absent
            if let absentStrings = vector.expect.text_qr_absent {
                for absent in absentStrings {
                    XCTAssertFalse(textQR.contains(absent), "[\(vector.id)] QR text contains forbidden string '\(absent)'. Full text:\n\(textQR)")
                }
            }

            replayedCount += 1
        }

        XCTAssertEqual(replayedCount, 6, "Expected exactly 6 test vectors replayed")
    }

    // MARK: - Structural JSON Comparison
    private func assertEqualJson(_ actual: Any, _ expected: Any, path: String) {
        if let actualDict = actual as? [String: Any], let expectedDict = expected as? [String: Any] {
            let actualKeys = Set(actualDict.keys)
            let expectedKeys = Set(expectedDict.keys)

            XCTAssertEqual(actualKeys, expectedKeys, "\(path): keys mismatch. Missing: \(expectedKeys.subtracting(actualKeys)), Extra: \(actualKeys.subtracting(expectedKeys))")

            for key in actualKeys.intersection(expectedKeys) {
                assertEqualJson(actualDict[key]!, expectedDict[key]!, path: "\(path).\(key)")
            }
        } else if let actualArr = actual as? [Any], let expectedArr = expected as? [Any] {
            XCTAssertEqual(actualArr.count, expectedArr.count, "\(path): array count mismatch")
            let minCount = min(actualArr.count, expectedArr.count)
            for i in 0..<minCount {
                assertEqualJson(actualArr[i], expectedArr[i], path: "\(path)[\(i)]")
            }
        } else if let actualStr = actual as? String, let expectedStr = expected as? String {
            XCTAssertEqual(actualStr, expectedStr, "\(path): string mismatch")
        } else if let actualNum = actual as? NSNumber, let expectedNum = expected as? NSNumber {
            XCTAssertEqual(actualNum, expectedNum, "\(path): number mismatch")
        } else if let _ = actual as? NSNull, let _ = expected as? NSNull {
            // Nulls match
        } else {
            XCTFail("\(path): type mismatch between actual (\(type(of: actual))) and expected (\(type(of: expected)))")
        }
    }
}

private extension String {
    var nonEmpty: String? {
        return isEmpty ? nil : self
    }
}
