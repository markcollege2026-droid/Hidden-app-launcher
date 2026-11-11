package com.hidden.launcher

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests refresh coordination: no overlapping refreshes, coalescing of rapid requests,
 * discarding of outdated results, and skipping pointless refresh-on-resume work.
 */
class RefreshSchedulerTest {

    @Test
    fun `startup request runs immediately`() {
        val scheduler = RefreshScheduler()
        val token = scheduler.request(RefreshReason.STARTUP, "sig")
        assertNotNull(token)
        assertTrue(scheduler.isCurrent(token!!))
        assertFalse(scheduler.isIdle())
    }

    @Test
    fun `second request while refreshing is coalesced instead of running in parallel`() {
        val scheduler = RefreshScheduler()
        val first = scheduler.request(RefreshReason.STARTUP, "sig")!!

        // No second token: work must not overlap.
        assertNull(scheduler.request(RefreshReason.RESUME, "sig"))
        assertNull(scheduler.request(RefreshReason.USER_ACTION, "sig"))
        assertTrue(scheduler.isCurrent(first))

        // Exactly one follow-up refresh after the first one completes.
        val followUp = scheduler.complete(first, "sig")
        assertNotNull(followUp)
        assertTrue(scheduler.isCurrent(followUp!!))

        // And then the scheduler goes idle again.
        assertNull(scheduler.complete(followUp, "sig"))
        assertTrue(scheduler.isIdle())
    }

    @Test
    fun `repeated rapid refresh requests never produce more than one follow up`() {
        val scheduler = RefreshScheduler()
        val first = scheduler.request(RefreshReason.PACKAGE_CHANGE, "sig")!!
        repeat(10) {
            assertNull(scheduler.request(RefreshReason.USER_ACTION, "sig"))
        }
        val followUp = scheduler.complete(first, "sig")
        assertNotNull(followUp)
        // Completing the follow-up leaves nothing pending.
        assertNull(scheduler.complete(followUp!!, "sig"))
        assertNull(scheduler.request(RefreshReason.RESUME, "sig")) // unchanged signature -> skipped
    }

    @Test
    fun `stale completion does not re-enter the scheduler`() {
        val scheduler = RefreshScheduler()
        val first = scheduler.request(RefreshReason.STARTUP, "sig")!!
        assertNull(scheduler.complete(first, "sig"))
        // A late/duplicate completion for an old token must be ignored.
        assertNull(scheduler.complete(first, "sig"))
        assertFalse(scheduler.isCurrent(first))
        assertTrue(scheduler.isIdle())
    }

    @Test
    fun `resume with unchanged selection is skipped`() {
        val scheduler = RefreshScheduler()
        val startup = scheduler.request(RefreshReason.STARTUP, "sig")!!
        scheduler.complete(startup, "sig")

        // Launching an app and returning changes nothing -> no work at all.
        assertNull(scheduler.request(RefreshReason.RESUME, "sig"))
        assertTrue(scheduler.isIdle())
    }

    @Test
    fun `resume after selection change runs`() {
        val scheduler = RefreshScheduler()
        val startup = scheduler.request(RefreshReason.STARTUP, "sig-a")!!
        scheduler.complete(startup, "sig-a")

        val token = scheduler.request(RefreshReason.RESUME, "sig-b")
        assertNotNull(token)
        assertTrue(scheduler.isCurrent(token!!))
    }

    @Test
    fun `resume after package change runs even with same signature`() {
        val scheduler = RefreshScheduler()
        val startup = scheduler.request(RefreshReason.STARTUP, "sig")!!
        scheduler.complete(startup, "sig")

        val token = scheduler.request(RefreshReason.RESUME, "sig", packagesChanged = true)
        assertNotNull(token)
        assertTrue(scheduler.isCurrent(token!!))
    }

    @Test
    fun `settings return is skipped when nothing changed`() {
        val scheduler = RefreshScheduler()
        val startup = scheduler.request(RefreshReason.STARTUP, "sig")!!
        scheduler.complete(startup, "sig")

        assertNull(scheduler.request(RefreshReason.SETTINGS_RETURN, "sig"))
        // ...but refreshes when the selection actually changed.
        assertNotNull(scheduler.request(RefreshReason.SETTINGS_RETURN, "sig2"))
    }

    @Test
    fun `user action always refreshes`() {
        val scheduler = RefreshScheduler()
        val startup = scheduler.request(RefreshReason.STARTUP, "sig")!!
        scheduler.complete(startup, "sig")

        val token = scheduler.request(RefreshReason.USER_ACTION, "sig")
        assertNotNull(token)
    }

    @Test
    fun `coalesced resume with unchanged signature resolves without extra refresh`() {
        val scheduler = RefreshScheduler()
        val startup = scheduler.request(RefreshReason.STARTUP, "sig")!!
        assertNull(scheduler.request(RefreshReason.RESUME, "sig"))
        // Pending resume is redundant once the signature is known to be unchanged.
        assertNull(scheduler.complete(startup, "sig"))
        assertTrue(scheduler.isIdle())
    }

    @Test
    fun `coalesced package change is not lost`() {
        val scheduler = RefreshScheduler()
        val startup = scheduler.request(RefreshReason.STARTUP, "sig")!!
        assertNull(scheduler.request(RefreshReason.RESUME, "sig", packagesChanged = true))
        val followUp = scheduler.complete(startup, "sig")
        assertNotNull(followUp)
        assertTrue(scheduler.isCurrent(followUp!!))
    }

    @Test
    fun `pending settings return still runs when the previous result was outdated`() {
        val scheduler = RefreshScheduler()
        val first = scheduler.request(RefreshReason.STARTUP, "sig-a")!!
        assertNull(scheduler.request(RefreshReason.SETTINGS_RETURN, "sig-b"))
        // Result was NOT painted (selection changed while loading) -> follow-up runs.
        val followUp = scheduler.complete(first, "sig-a", painted = false)
        assertNotNull(followUp)
        assertTrue(scheduler.isCurrent(followUp!!))
    }

    @Test
    fun `outdated result does not mark itself as the current state`() {
        val scheduler = RefreshScheduler()
        val first = scheduler.request(RefreshReason.STARTUP, "sig-a")!!
        assertNull(scheduler.complete(first, "sig-a", painted = false))
        // Since nothing was painted, the same signature must still be refreshed.
        assertNotNull(scheduler.request(RefreshReason.RESUME, "sig-a"))
    }

    @Test
    fun `invalidateSignature forces next resume to run`() {
        val scheduler = RefreshScheduler()
        val startup = scheduler.request(RefreshReason.STARTUP, "sig")!!
        scheduler.complete(startup, "sig")
        assertNull(scheduler.request(RefreshReason.RESUME, "sig"))

        scheduler.invalidateSignature()
        assertNotNull(scheduler.request(RefreshReason.RESUME, "sig"))
    }
}
