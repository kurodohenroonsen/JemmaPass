import XCTest
@testable import JemmaCore

final class BloodGroupVectorsTests: XCTestCase {

    struct VectorFile: Decodable {
        let id: String
        let title: String
        let input_j: JemmaProfileJ
        let expect: VectorExpect
    }

    struct VectorExpect: Decodable {
        let blood_group_observations: [AnyCodable]
    }

    private func findVectorsDirectory() throws -> URL {
        var current = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        while current.path != "/" {
            let candidate = current.appendingPathComponent("qa/vectors/bloodgroup")
            var isDir: ObjCBool = false
            if FileManager.default.fileExists(atPath: candidate.path, isDirectory: &isDir), isDir.boolValue {
                return candidate
            }
            current = current.deletingLastPathComponent()
        }

        XCTFail("Could not locate qa/vectors/bloodgroup directory by ascending from: \(#filePath)")
        throw NSError(domain: "VectorsNotFound", code: 404)
    }

    func testAllBloodGroupVectors() throws {
        let vectorsDir = try findVectorsDirectory()
        let fileManager = FileManager.default
        let files = try fileManager.contentsOfDirectory(at: vectorsDir, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted(by: { $0.lastPathComponent < $1.lastPathComponent })

        XCTAssertGreaterThanOrEqual(files.count, 10, "Expected at least 10 vector files in \(vectorsDir.path)")
        print("Replaying \(files.count) blood group vector files from: \(vectorsDir.path)")

        var failureMessages: [String] = []

        for fileURL in files {
            let data = try Data(contentsOf: fileURL)
            let decoder = JSONDecoder()
            let vector = try decoder.decode(VectorFile.self, from: data)

            print("Testing vector [\(vector.id)]: \(vector.title)")

            let bundle = JemmaFhirBundleBuilder.build(profile: vector.input_j)

            // Extract patient fullUrl
            var patientUrl = ""
            for entry in bundle.entry {
                if case .patient = entry.resource {
                    patientUrl = entry.fullUrl
                    break
                }
            }

            // Extract blood group observations (code LOINC 882-1)
            var actualObservations: [[String: Any]] = []
            for entry in bundle.entry {
                if case .observation(let o) = entry.resource {
                    let hasAboRh = o.code.coding?.contains { $0.code == "882-1" } ?? false
                    if hasAboRh {
                        // Encode to JSON and parse as Dictionary for structural comparison
                        let enc = JSONEncoder()
                        let oData = try enc.encode(o)
                        if var oDict = try JSONSerialization.jsonObject(with: oData) as? [String: Any] {
                            // Rule: ignore id
                            oDict.removeValue(forKey: "id")
                            // Rule: replace patient URL with @patient
                            if var subject = oDict["subject"] as? [String: Any],
                               let ref = subject["reference"] as? String, ref == patientUrl {
                                subject["reference"] = "@patient"
                                oDict["subject"] = subject
                            }
                            if var performers = oDict["performer"] as? [[String: Any]] {
                                for i in 0..<performers.count {
                                    if let ref = performers[i]["reference"] as? String, ref == patientUrl {
                                        performers[i]["reference"] = "@patient"
                                    }
                                }
                                oDict["performer"] = performers
                            }
                            actualObservations.append(oDict)
                        }
                    }
                }
            }

            // Expected observations
            let expData = try JSONEncoder().encode(vector.expect.blood_group_observations)
            let expArray = try JSONSerialization.jsonObject(with: expData) as? [[String: Any]] ?? []

            // Compare count
            if actualObservations.count != expArray.count {
                failureMessages.append("\(vector.id): expected \(expArray.count) observations, got \(actualObservations.count)")
                continue
            }

            // Deep compare dictionaries
            for (idx, (exp, act)) in zip(expArray, actualObservations).enumerated() {
                var diffs: [String] = []
                diffDict(path: "obs[\(idx)]", expected: exp, actual: act, diffs: &diffs)
                if !diffs.isEmpty {
                    failureMessages.append("\(vector.id): \(diffs.joined(separator: ", "))")
                }
            }
        }

        XCTAssertTrue(failureMessages.isEmpty, "\(failureMessages.count) vector(s) failed:\n" + failureMessages.joined(separator: "\n"))
    }

    private func diffDict(path: String, expected: [String: Any], actual: [String: Any], diffs: inout [String]) {
        for (k, vExp) in expected {
            guard let vAct = actual[k] else {
                diffs.append("\(path).\(k) is missing")
                continue
            }
            diffAny(path: "\(path).\(k)", expected: vExp, actual: vAct, diffs: &diffs)
        }
        for (k, vAct) in actual {
            if expected[k] == nil {
                diffs.append("\(path).\(k) unexpected (found \(vAct))")
            }
        }
    }

    private func diffAny(path: String, expected: Any, actual: Any, diffs: inout [String]) {
        if let dExp = expected as? [String: Any], let dAct = actual as? [String: Any] {
            diffDict(path: path, expected: dExp, actual: dAct, diffs: &diffs)
        } else if let aExp = expected as? [Any], let aAct = actual as? [Any] {
            if aExp.count != aAct.count {
                diffs.append("\(path) has \(aAct.count) items, expected \(aExp.count)")
            } else {
                for i in 0..<aExp.count {
                    diffAny(path: "\(path)[\(i)]", expected: aExp[i], actual: aAct[i], diffs: &diffs)
                }
            }
        } else {
            let strExp = "\(expected)"
            let strAct = "\(actual)"
            if strExp != strAct {
                diffs.append("\(path) expected \"\(strExp)\", got \"\(strAct)\"")
            }
        }
    }
}
