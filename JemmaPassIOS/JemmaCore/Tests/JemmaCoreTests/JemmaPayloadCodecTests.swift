import XCTest
@testable import JemmaCore

final class JemmaPayloadCodecTests: XCTestCase {

    func testPayloadCodecRoundTrip() throws {
        let profile = JemmaProfileJ(
            schemaVersion: "1.2",
            sid: "test_sid_001",
            p: JPatient(
                gn: "Kurodo",
                fn: "Henro",
                gs: "M",
                bd: "1974-05-12",
                bt: "O+",
                ct: [
                    JContact(n: "Kamekichi", r: "FRND", p: "+32 2 000 00 01")
                ]
            ),
            al: [
                JAllergy(c: "91936005", s: "H", st: "A", d_display: "Penicillin")
            ],
            md: [
                JMedication(c: "2123", t: "1x/day", r: "O", v: "5", u: "mg", d_display: "Warfarin")
            ]
        )

        // 1. Encode to _j2:
        let encoded = try JemmaPayloadCodec.encode(profile: profile)
        XCTAssertTrue(encoded.hasPrefix("_j2:"), "Encoded payload must start with '_j2:'")

        // 2. Decode from _j2:
        let decoded = try JemmaPayloadCodec.decode(payload: encoded)

        XCTAssertEqual(decoded.schemaVersion, profile.schemaVersion)
        XCTAssertEqual(decoded.sid, profile.sid)
        XCTAssertEqual(decoded.p?.gn, profile.p?.gn)
        XCTAssertEqual(decoded.p?.fn, profile.p?.fn)
        XCTAssertEqual(decoded.p?.bt, profile.p?.bt)
        XCTAssertEqual(decoded.p?.ct.count, 1)
        XCTAssertEqual(decoded.p?.ct.first?.n, "Kamekichi")
        XCTAssertEqual(decoded.p?.ct.first?.r, "FRND")
        XCTAssertEqual(decoded.al.count, 1)
        XCTAssertEqual(decoded.al.first?.d_display, "Penicillin")
        XCTAssertEqual(decoded.md.count, 1)
        XCTAssertEqual(decoded.md.first?.d_display, "Warfarin")
    }

    func testUniversalQRTextLimit() throws {
        let profile = JemmaProfileJ(
            schemaVersion: "1.2",
            sid: "test_large",
            p: JPatient(gn: "Large", fn: "Patient", gs: "F", bd: "1980-01-01", bt: "A+"),
            al: (1...30).map { JAllergy(c: "code_\($0)", s: "H", d_display: "Allergy long descriptive name number \($0)") },
            md: (1...30).map { JMedication(c: "med_\($0)", v: "100", u: "mg", d_display: "Medication with extended description \($0)") }
        )

        let qrText = JemmaTextPayloadBuilder.build(profile: profile, lang: .fr)
        let byteCount = JemmaTextPayloadBuilder.utf8Size(qrText)

        XCTAssertLessThanOrEqual(byteCount, JemmaTextPayloadBuilder.maxBytes, "QR text byte size (\(byteCount)) exceeds 1800 bytes cap")
        XCTAssertTrue(qrText.contains(JemmaTextPayloadBuilder.truncationMark), "QR text must contain truncation marker when truncated")
    }
}
