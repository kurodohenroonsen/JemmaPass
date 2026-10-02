/*
 * ProfileFilesTest.kt — UC-STO (qa/usecases/03): untrusted profile ids and atomic writes.
 */
package be.heyman.android.jemmapassdemo.profiles

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFilesTest {

    @Test
    fun plainIdsAreKept() {
        assertEquals("demo_haru", ProfileFiles.safeIdOrNull("demo_haru"))
        assertEquals("p-3f2c9a1e-0b7d-4c55-9d2e-1a2b3c4d5e6f", ProfileFiles.safeIdOrNull(" p-3f2c9a1e-0b7d-4c55-9d2e-1a2b3c4d5e6f "))
    }

    @Test
    fun hostileOrBrokenIdsAreRejected() {
        for (bad in listOf(null, "", "  ", "../../databases/x", "..", "a/b", "a\\b", ".hidden", "a..b", "nom avec espace",
            "x".repeat(65), "id\u0000", "été")) {
            assertNull("should reject '$bad'", ProfileFiles.safeIdOrNull(bad))
        }
    }

    @Test
    fun atomicWriteReplacesAndLeavesNoTempFile() {
        val dir = File(System.getProperty("java.io.tmpdir"), "jp-atomic-${System.nanoTime()}")
        try {
            val target = File(dir, "demo.json")
            ProfileFiles.writeAtomic(target, "v1")
            assertEquals("v1", target.readText())
            ProfileFiles.writeAtomic(target, "v2 — é 日本語")
            assertEquals("v2 — é 日本語", target.readText())
            assertFalse(File(dir, "demo.json.tmp").exists())
            assertEquals(listOf("demo.json"), dir.list()!!.toList())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun failedWriteKeepsThePreviousVersion() {
        val dir = File(System.getProperty("java.io.tmpdir"), "jp-atomic-${System.nanoTime()}")
        try {
            val target = File(dir, "demo.json")
            ProfileFiles.writeAtomic(target, "good")
            // a directory squatting the temp name makes the write fail before the rename
            File(dir, "demo.json.tmp").mkdirs()
            File(dir, "demo.json.tmp/keep").writeText("x")
            val failed = try { ProfileFiles.writeAtomic(target, "bad"); false } catch (e: Exception) { true }
            assertTrue(failed)
            assertEquals("good", target.readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
