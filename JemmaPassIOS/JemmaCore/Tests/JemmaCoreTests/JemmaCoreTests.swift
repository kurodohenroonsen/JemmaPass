import XCTest
@testable import JemmaCore

final class JemmaCoreTests: XCTestCase {
    func testDeterministicUrns() throws {
        let patientUrn = IpsFhirCodec.stableUrn("demo_haru|Patient")
        let compositionUrn = IpsFhirCodec.stableUrn("demo_haru|Composition")
        let bundleUrn = IpsFhirCodec.stableUrn("demo_haru|Bundle")

        print("Deterministic URNs for demo_haru:")
        print("Patient: \(patientUrn)")
        print("Composition: \(compositionUrn)")
        print("Bundle: \(bundleUrn)")

        // Validate cross-platform parity against Android reference (cycle 27 demo_haru.fhir.json)
        XCTAssertEqual(patientUrn, "urn:uuid:d4d6f377-2bc2-3fdf-b2ca-c2dd4477cddf")
        XCTAssertEqual(compositionUrn, "urn:uuid:cc4566d1-4052-3189-a1fd-c30ce0aac947")
        XCTAssertEqual(bundleUrn, "urn:uuid:243a6333-028d-3926-a461-0566a8eb442f")
    }
}
