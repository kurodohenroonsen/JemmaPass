/*
 * RED TEST (KB update, PROTOCOL 9.2) — not to be edited by the implementer.
 * A phone that stayed several versions behind reaches the current knowledge base through differential patches.
 * The choice of the patches is a pure function : null means "no safe path, download the full base".
 */
package be.heyman.android.jemmapassdemo.kbupdate

import be.heyman.android.jemmapassdemo.downloads.PatchChainResolver.resolveChain
import be.heyman.android.jemmapassdemo.downloads.PatchDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatchChainResolverTest {

    private fun p(from: String, to: String, bytes: Long = 1_000_000L) =
        PatchDescriptor(from, to, "https://example.invalid/patch_${from}_to_$to.db", bytes, "0".repeat(64))

    private fun path(chain: List<PatchDescriptor>?): String? = chain?.joinToString(" ") { "${it.fromVersion}>${it.toVersion}" }

    @Test
    fun `UC-UPD-020 a phone already up to date needs nothing`() {
        assertEquals(emptyList<PatchDescriptor>(), resolveChain("2.1-omnis", "2.1-omnis", listOf(p("2.0-omnis", "2.1-omnis"))))
    }

    @Test
    fun `UC-UPD-021 one version behind takes the single patch`() {
        assertEquals("2.0-omnis>2.1-omnis", path(resolveChain("2.0-omnis", "2.1-omnis", listOf(p("2.0-omnis", "2.1-omnis")))))
    }

    @Test
    fun `UC-UPD-022 three versions behind takes the patches in order, whatever their order in the manifest`() {
        val manifest = listOf(p("2.2", "2.3"), p("2.0", "2.1"), p("2.1", "2.2"))
        assertEquals("2.0>2.1 2.1>2.2 2.2>2.3", path(resolveChain("2.0", "2.3", manifest)))
    }

    @Test
    fun `UC-UPD-023 a hole in the chain means no path`() {
        assertNull(resolveChain("2.0", "2.3", listOf(p("2.0", "2.1"), p("2.2", "2.3"))))
    }

    @Test
    fun `UC-UPD-024 no manifest entry means no path`() {
        assertNull(resolveChain("2.0", "2.1", emptyList()))
    }

    @Test
    fun `UC-UPD-025 a cumulative patch is preferred to a longer chain`() {
        val manifest = listOf(p("2.0", "2.1"), p("2.1", "2.2"), p("2.2", "2.3"), p("2.0", "2.3", bytes = 9_000_000L))
        assertEquals("fewer patches = fewer chances to stop half way", "2.0>2.3", path(resolveChain("2.0", "2.3", manifest)))
    }

    @Test
    fun `UC-UPD-026 between two paths of the same length the lighter one wins`() {
        val manifest = listOf(p("2.0", "2.1a", 5_000_000L), p("2.1a", "2.2", 5_000_000L), p("2.0", "2.1b", 1_000_000L), p("2.1b", "2.2", 1_000_000L))
        assertEquals("2.0>2.1b 2.1b>2.2", path(resolveChain("2.0", "2.2", manifest)))
    }

    @Test
    fun `UC-UPD-027 a dead end does not hide the real path`() {
        val manifest = listOf(p("2.0", "2.1-test"), p("2.0", "2.1"), p("2.1", "2.2"))
        assertEquals("2.0>2.1 2.1>2.2", path(resolveChain("2.0", "2.2", manifest)))
    }

    @Test
    fun `UC-UPD-028 a loop in the manifest ends with no path instead of running for ever`() {
        val manifest = listOf(p("2.0", "2.1"), p("2.1", "2.0"))
        assertNull(resolveChain("2.0", "2.3", manifest))
    }

    @Test
    fun `UC-UPD-029 there is no way back to an older version`() {
        assertNull(resolveChain("2.3", "2.1", listOf(p("2.0", "2.1"), p("2.1", "2.2"), p("2.2", "2.3"))))
    }

    @Test
    fun `UC-UPD-030 an unknown installed version has no path`() {
        assertNull(resolveChain("9.9-custom", "2.1", listOf(p("2.0", "2.1"))))
        assertNull(resolveChain("", "2.1", listOf(p("2.0", "2.1"))))
    }

    @Test
    fun `UC-UPD-031 the same step listed twice uses the lighter file`() {
        val chain = resolveChain("2.0", "2.1", listOf(p("2.0", "2.1", 4_000_000L), p("2.0", "2.1", 2_000_000L)))
        assertEquals(listOf(2_000_000L), chain?.map { it.sizeBytes })
    }
}
