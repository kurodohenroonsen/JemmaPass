/*
 * JemmaSosChunkCodecTest.kt — JEMMA Pass · Plan B · L2 v2.5.1
 *
 * Round-trip JUnit tests for the SOS chunk codec ported in L1.
 *
 * Strategy:
 *   - Build a minimal `_j 1.2` short-profile JSON in-memory.
 *   - Encode it → list of chunk byte arrays.
 *   - Decode each chunk back to a ParsedChunk.
 *   - Assert the values that crossed the wire are bit-identical to the
 *     ones we originally encoded — guarantees the codec preserves the
 *     critical fields needed by the killer demo (sex/age, blood type,
 *     criticality, GPS, broadcaster name).
 *
 * What we DON'T test in L1:
 *   - The full hash canonicalisation (L44.89 reference) — needs a
 *     companion test fixture from Plan A's 84-test suite, which we'll
 *     port in L2.1 once the basic round-trip is green.
 *   - Multi-section profiles (allergies × N, meds × N) — the codec
 *     splits them across multiple chunks; covered separately when we
 *     port the multi-event 6/6 zero-loss tests.
 *
 * Why this works without Robolectric:
 *   `testOptions { unitTests.isReturnDefaultValues = true }` in
 *   build.gradle.kts makes android.util.Log calls return their default
 *   (Int 0) instead of throwing. The codec's logging is purely
 *   diagnostic, so this is harmless for these assertions.
 */
package be.heyman.android.jemmapassdemo.sos

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JemmaSosChunkCodecTest {

    /**
     * Smoke test: a profile with only patient identity (no allergies,
     * meds, conditions, immunizations) should produce at least one
     * Header chunk that round-trips bit-identically.
     */
    @Test
    fun `header chunk round-trips with patient identity preserved`() {
        // Brussels coordinates × 1e6 — Kurodo persona reference point
        val expectedLatE6 = 50846900
        val expectedLonE6 = 4351700

        val sid = JemmaSosChunkCodec.newSessionId()
        val config = JemmaSosChunkCodec.BroadcastConfig(
            sessionId = sid,
            latE6 = expectedLatE6,
            lonE6 = expectedLonE6,
            criticality = JemmaSosChunkCodec.CRIT_STABLE,
            flags = 0x08.toByte(),               // |J=08 rescuer-default
            tsOffsetSeconds = 0,
            broadcasterName = "Kurodo",
        )

        val profile = JSONObject().apply {
            put("p", JSONObject().apply {
                put("gn", "Kurodo")
                put("gs", "M")
                put("bd", "1979-05-17")           // birth date YYYY-MM-DD
                put("bt", "B-")
            })
            put("al", JSONArray())
            put("md", JSONArray())
            put("cn", JSONArray())
            put("im", JSONArray())
        }

        val chunks = JemmaSosChunkCodec.encode(profile, config)
        assertTrue("encoder must emit at least 1 chunk", chunks.isNotEmpty())

        val firstParsed = JemmaSosChunkCodec.decode(chunks[0])
        assertNotNull("first chunk must decode to a non-null ParsedChunk", firstParsed)
        assertTrue(
            "first chunk should be a Header (tier-0 vital info)",
            firstParsed is JemmaSosChunkCodec.ParsedChunk.Header,
        )
        val header = firstParsed as JemmaSosChunkCodec.ParsedChunk.Header

        // Session id must be preserved bit-identically across encode/decode
        assertEquals(
            "sid hex must match",
            JemmaSosChunkCodec.sessionIdToHex(sid),
            JemmaSosChunkCodec.sessionIdToHex(header.sessionId),
        )

        // GPS must round-trip — the decoded values are stored as Int×1e6.
        assertEquals("latE6 must round-trip", expectedLatE6, header.latE6)
        assertEquals("lonE6 must round-trip", expectedLonE6, header.lonE6)

        // Identity fields
        assertEquals("sex must round-trip", 'M', header.sex)
        assertEquals("blood type must round-trip", "B-", header.bloodType)

        // Broadcaster name (≤ 16 UTF-8 bytes)
        assertEquals("broadcaster name must round-trip", "Kurodo", header.broadcasterName)

        // Criticality + CapFlags
        assertEquals("criticality must round-trip", JemmaSosChunkCodec.CRIT_STABLE, header.criticality)
        assertEquals("CapFlags |J=08 must round-trip", 0x08.toByte(), header.flags)
    }

    /** Session ids must always be 16 bytes (UUID v4 width). */
    @Test
    fun `newSessionId emits 16 bytes`() {
        val sid = JemmaSosChunkCodec.newSessionId()
        assertEquals("session id must be 16 bytes (UUID v4)", 16, sid.size)
    }

    /**
     * sessionIdToHex must produce a stable 32-char lowercase hex string
     * for any valid 16-byte sid. This is the key used for dedup in the
     * BLE scanner + Nearby controller, and any drift would cause the
     * "same victim seen as different sids" pathology described in
     * Plan A's L44.16.x bug log.
     */
    @Test
    fun `sessionIdToHex produces 32-char lowercase string`() {
        val sid = JemmaSosChunkCodec.newSessionId()
        val hex = JemmaSosChunkCodec.sessionIdToHex(sid)
        assertEquals("hex length", 32, hex.length)
        assertEquals("hex must be lowercase", hex, hex.lowercase())
        assertTrue(
            "hex must only contain 0-9 a-f",
            hex.all { c -> c in '0'..'9' || c in 'a'..'f' },
        )
    }

    /** Garbage bytes must decode to null, not throw. */
    @Test
    fun `decode of too-short bytes returns null cleanly`() {
        val tooShort = ByteArray(2)        // less than PREAMBLE_SIZE
        val result = JemmaSosChunkCodec.decode(tooShort)
        assertEquals("decode of garbage must return null", null, result)
    }
}
