import XCTest
@testable import JemmaCore

final class DeviceVectorsTests: XCTestCase {

    struct DeviceVectorFile: Decodable {
        let id: String
        let title: String
        let input_fhir: InputFhir
        let expect: Expect
    }

    struct InputFhir: Decodable {
        let device: AnyCodable
        let device_use_statement: AnyCodable
    }

    struct Expect: Decodable {
        let reexported: Reexported
        let j_dv: [String: AnyCodable]
    }

    struct Reexported: Decodable {
        let device: AnyCodable
        let device_use_statement: AnyCodable
    }

    private func findVectorsDirectory() throws -> URL {
        var current = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        while current.path != "/" {
            let candidate = current.appendingPathComponent("qa/vectors/devices")
            var isDir: ObjCBool = false
            if FileManager.default.fileExists(atPath: candidate.path, isDirectory: &isDir), isDir.boolValue {
                return candidate
            }
            current = current.deletingLastPathComponent()
        }

        XCTFail("Could not locate qa/vectors/devices directory by ascending from: \(#filePath)")
        throw NSError(domain: "VectorsNotFound", code: 404)
    }

    func testAllDeviceVectors() throws {
        let vectorsDir = try findVectorsDirectory()
        let fileManager = FileManager.default
        let files = try fileManager.contentsOfDirectory(at: vectorsDir, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted(by: { $0.lastPathComponent < $1.lastPathComponent })

        XCTAssertGreaterThanOrEqual(files.count, 2, "Expected at least 2 vector files in \(vectorsDir.path)")
        print("Replaying \(files.count) device vector files from: \(vectorsDir.path)")

        var failureMessages: [String] = []

        let patientUrn = "urn:uuid:patient-test"
        let deviceUrn = "urn:uuid:device-test"
        let stmtUrn = "urn:uuid:statement-test"

        for fileURL in files {
            let data = try Data(contentsOf: fileURL)
            let decoder = JSONDecoder()
            let vector = try decoder.decode(DeviceVectorFile.self, from: data)

            print("Testing device vector [\(vector.id)]: \(vector.title)")

            // 1. Prepare raw input JSON string replacing @patient and @device
            let enc = JSONEncoder()
            let devInputData = try enc.encode(vector.input_fhir.device)
            var devJsonString = String(decoding: devInputData, as: UTF8.self)
            devJsonString = devJsonString.replacingOccurrences(of: "@patient", with: patientUrn)

            let stmtInputData = try enc.encode(vector.input_fhir.device_use_statement)
            var stmtJsonString = String(decoding: stmtInputData, as: UTF8.self)
            stmtJsonString = stmtJsonString.replacingOccurrences(of: "@patient", with: patientUrn)
            stmtJsonString = stmtJsonString.replacingOccurrences(of: "@device", with: deviceUrn)

            let parsedDev = try JSONDecoder().decode(FHIRDevice.self, from: Data(devJsonString.utf8))
            let parsedStmt = try JSONDecoder().decode(FHIRDeviceUseStatement.self, from: Data(stmtJsonString.utf8))

            // Build input bundle
            let inputBundle = FHIRBundle(
                id: "test-device-bundle",
                entry: [
                    FHIRBundleEntry(fullUrl: patientUrn, resource: .patient(FHIRPatient(id: "patient-01"))),
                    FHIRBundleEntry(fullUrl: deviceUrn, resource: .device(parsedDev)),
                    FHIRBundleEntry(fullUrl: stmtUrn, resource: .deviceUseStatement(parsedStmt)),
                ]
            )

            // 2. Import into IpsDevice
            let importedDevices = IpsFhirCodec.devicesOf(bundle: inputBundle)
            if importedDevices.isEmpty {
                failureMessages.append("\(vector.id): no devices imported from bundle")
                continue
            }
            let imported = importedDevices[0]

            // 3. Check projection j_dv
            let jEntry = imported.toJEntry()
            let jEntryData = try JSONEncoder().encode(jEntry)
            let jEntryDict = (try JSONSerialization.jsonObject(with: jEntryData) as? [String: Any]) ?? [:]

            let expJData = try JSONEncoder().encode(vector.expect.j_dv)
            let expJDict = (try JSONSerialization.jsonObject(with: expJData) as? [String: Any]) ?? [:]

            var jDiffs: [String] = []
            diffDict(path: "j_dv", expected: expJDict, actual: jEntryDict, diffs: &jDiffs)
            if !jDiffs.isEmpty {
                failureMessages.append("\(vector.id) [j_dv]: " + jDiffs.joined(separator: ", "))
            }

            // 4. Check reexported resources
            let reexportedDev = IpsFhirCodec.toFhirDevice(dv: imported, patientUrn: patientUrn)
            let reexportedStmt = IpsFhirCodec.toFhirUseStatement(dv: imported, patientUrn: patientUrn, deviceUrn: deviceUrn)

            // Normalize device dict: remove id, normalize reference
            let actDevData = try JSONEncoder().encode(reexportedDev)
            var actDevDict = (try JSONSerialization.jsonObject(with: actDevData) as? [String: Any]) ?? [:]
            actDevDict.removeValue(forKey: "id")
            if var pat = actDevDict["patient"] as? [String: Any], let ref = pat["reference"] as? String, ref == patientUrn {
                pat["reference"] = "@patient"
                actDevDict["patient"] = pat
            }

            let expDevData = try JSONEncoder().encode(vector.expect.reexported.device)
            var expDevDict = (try JSONSerialization.jsonObject(with: expDevData) as? [String: Any]) ?? [:]
            expDevDict.removeValue(forKey: "id")

            var devDiffs: [String] = []
            diffDict(path: "reexported.device", expected: expDevDict, actual: actDevDict, diffs: &devDiffs)
            if !devDiffs.isEmpty {
                failureMessages.append("\(vector.id) [device]: " + devDiffs.joined(separator: ", "))
            }

            // Normalize statement dict: remove id, normalize references
            let actStmtData = try JSONEncoder().encode(reexportedStmt)
            var actStmtDict = (try JSONSerialization.jsonObject(with: actStmtData) as? [String: Any]) ?? [:]
            actStmtDict.removeValue(forKey: "id")
            if var sub = actStmtDict["subject"] as? [String: Any], let ref = sub["reference"] as? String, ref == patientUrn {
                sub["reference"] = "@patient"
                actStmtDict["subject"] = sub
            }
            if var dev = actStmtDict["device"] as? [String: Any], let ref = dev["reference"] as? String, ref == deviceUrn {
                dev["reference"] = "@device"
                actStmtDict["device"] = dev
            }

            let expStmtData = try JSONEncoder().encode(vector.expect.reexported.device_use_statement)
            var expStmtDict = (try JSONSerialization.jsonObject(with: expStmtData) as? [String: Any]) ?? [:]
            expStmtDict.removeValue(forKey: "id")

            var stmtDiffs: [String] = []
            diffDict(path: "reexported.device_use_statement", expected: expStmtDict, actual: actStmtDict, diffs: &stmtDiffs)
            if !stmtDiffs.isEmpty {
                failureMessages.append("\(vector.id) [statement]: " + stmtDiffs.joined(separator: ", "))
            }
        }

        XCTAssertTrue(failureMessages.isEmpty, "\(failureMessages.count) vector failure(s):\n" + failureMessages.joined(separator: "\n"))
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
