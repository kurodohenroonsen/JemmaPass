/*
 * StatusResolverTest.kt — triage status shared between rescuers (SALT codes over the mesh).
 *
 * Use cases (qa/usecases/02) : UC-SOS-020 (a stale "deceased" must not replace a fresh
 * "stabilized"), UC-SOS-021 (same instant, two rescuers), UC-SOS-004 (explicit cancel).
 *
 * Only the rules that hold today are pinned here. The order-dependent cases (two phones
 * receiving the same two events in a different order end up with different statuses) are
 * recorded in qa/usecases/suspected-defects.md with the test that exposes them.
 */
package be.heyman.android.jemmapassdemo.triage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusResolverTest {

    private fun event(status: SaltCode, at: Long, rescuer: String = "r1", cancel: Boolean = false, victim: String = "v1") =
        StatusEvent(victimSid = victim, status = status, rescuerSid = rescuer, timestampSec = at, isExplicitOverride = cancel)

    private val nonDeceased = SaltCode.values().filter { it != SaltCode.DCD }

    @Test
    fun `UC-SOS-020 a deceased event older than the current status is rejected, whatever the current status`() {
        for (current in nonDeceased) {
            val existing = event(current, at = 1_000)
            for (age in listOf(1L, 30L, 240L, 86_400L)) {
                assertFalse("$current then DCD ${age}s older", StatusResolver.shouldOverwrite(existing, event(SaltCode.DCD, at = 1_000 - age)))
            }
            // same second : not newer, so not accepted
            assertFalse("$current then DCD same second", StatusResolver.shouldOverwrite(existing, event(SaltCode.DCD, at = 1_000, rescuer = "r9")))
            // a newer one is accepted (the two-tap confirmation is the screen's job)
            assertTrue("$current then newer DCD", StatusResolver.shouldOverwrite(existing, event(SaltCode.DCD, at = 1_001)))
        }
    }

    @Test
    fun `UC-SOS-020 leaving deceased needs more than the grace window or an explicit cancel`() {
        val deceased = event(SaltCode.DCD, at = 1_000)
        assertEquals(30L, StatusResolver.DCD_GRACE_SEC)
        for (next in nonDeceased) {
            assertFalse("$next 1 s later", StatusResolver.shouldOverwrite(deceased, event(next, at = 1_001)))
            assertFalse("$next at the end of the grace window", StatusResolver.shouldOverwrite(deceased, event(next, at = 1_030)))
            assertTrue("$next after the grace window", StatusResolver.shouldOverwrite(deceased, event(next, at = 1_031)))
            assertFalse("$next older than the deceased event", StatusResolver.shouldOverwrite(deceased, event(next, at = 999)))
            // the rescuer's "cancel deceased" is applied at once
            assertTrue("$next explicit cancel", StatusResolver.shouldOverwrite(deceased, event(next, at = 1_001, cancel = true)))
        }
    }

    @Test
    fun `UC-SOS-021 between two living statuses the latest event wins and a tie is decided the same way everywhere`() {
        for (a in nonDeceased) for (b in nonDeceased) {
            val older = event(a, at = 1_000, rescuer = "r1")
            val newer = event(b, at = 1_005, rescuer = "r2")
            assertTrue("$a then newer $b", StatusResolver.shouldOverwrite(older, newer))
            assertFalse("$b then older $a", StatusResolver.shouldOverwrite(newer, older))
            // same second : the rescuer id decides, so both phones keep the same event
            val tieLow = event(a, at = 2_000, rescuer = "r1")
            val tieHigh = event(b, at = 2_000, rescuer = "r2")
            assertTrue("tie $a/$b", StatusResolver.shouldOverwrite(tieLow, tieHigh))
            assertFalse("tie $b/$a", StatusResolver.shouldOverwrite(tieHigh, tieLow))
        }
        // two deceased events : plain last-write-wins too
        assertTrue(StatusResolver.shouldOverwrite(event(SaltCode.DCD, 1_000), event(SaltCode.DCD, 1_001)))
        assertFalse(StatusResolver.shouldOverwrite(event(SaltCode.DCD, 1_001), event(SaltCode.DCD, 1_000)))
        // an identical event received twice (relay) changes nothing
        assertFalse(StatusResolver.shouldOverwrite(event(SaltCode.STAB, 1_000), event(SaltCode.STAB, 1_000)))
    }

    @Test
    fun `UC-SOS-020 the resolver keeps one status per victim and applies the same rules`() {
        val resolver = StatusResolver()
        assertNull(resolver.get("v1"))

        val stab = event(SaltCode.STAB, at = 1_000)
        assertEquals(stab, resolver.apply(stab))
        // the killer scenario : a deceased event created 4 minutes earlier arrives late through a relay
        val staleDeceased = event(SaltCode.DCD, at = 760, rescuer = "r2")
        assertEquals("the stale deceased event is ignored", stab, resolver.apply(staleDeceased))
        assertEquals(SaltCode.STAB, resolver.get("v1")!!.status)

        // another victim is independent
        val other = event(SaltCode.HELP, at = 10, victim = "v2")
        assertEquals(other, resolver.apply(other))
        assertEquals(setOf("v1", "v2"), resolver.all().keys)
        assertEquals(SaltCode.STAB, resolver.get("v1")!!.status)

        // a real, newer deceased event is applied ; a quick correction needs the explicit cancel
        val deceased = event(SaltCode.DCD, at = 1_200, rescuer = "r2")
        assertEquals(deceased, resolver.apply(deceased))
        assertEquals(deceased, resolver.apply(event(SaltCode.STAB, at = 1_210)))
        val cancel = event(SaltCode.EVAL, at = 1_211, cancel = true)
        assertEquals(cancel, resolver.apply(cancel))
        assertEquals(SaltCode.EVAL, resolver.get("v1")!!.status)

        resolver.clear()
        assertTrue(resolver.all().isEmpty())
    }

    @Test
    fun `UC-SOS-020 wire codes are parsed strictly and an unknown code is dropped`() {
        for (code in SaltCode.values()) {
            assertEquals(code, SaltCode.parse(code.code))
            assertEquals(code, SaltCode.parse(" " + code.code.lowercase() + " "))
        }
        assertEquals(SaltCode.values().map { it.code }.toSet(), SaltCode.allCodes())
        for (bad in listOf("", " ", "DEAD", "DC", "DCDX", "STABLE", "0")) assertNull("'$bad'", SaltCode.parse(bad))
        // the map sent to the screen carries the code, never the enum name of another status
        val map = event(SaltCode.DCD, at = 5, cancel = true).toMap()
        assertEquals("DCD", map["status"])
        assertEquals(true, map["isExplicitOverride"])
        assertEquals("v1", map["victimSid"])
    }
}
