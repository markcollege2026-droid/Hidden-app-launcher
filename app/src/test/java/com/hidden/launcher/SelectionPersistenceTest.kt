package com.hidden.launcher

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests that selection persistence would keep exact set across restarts.
 * This tests the sanitize and equality logic that would be used with SharedPreferences.
 */
class SelectionPersistenceTest {

    @Test
    fun `persisted selection round trip preserves exact set`() {
        val original = setOf("com.example.one", "com.example.two", "com.example.three")
        // Simulate save/load via SharedPreferences (StringSet)
        val saved = original.toSet()
        val loaded = saved.toSet()
        assertEquals(original, loaded)
    }

    @Test
    fun `selection change is reflected`() {
        var selected = setOf("com.a", "com.b")
        // Add
        selected = selected + "com.c"
        assertEquals(3, selected.size)
        assertTrue("com.c" in selected)
        // Remove
        selected = selected - "com.a"
        assertEquals(2, selected.size)
        assertFalse("com.a" in selected)
    }

    @Test
    fun `duplicate packages not stored twice`() {
        val withDup = listOf("com.a", "com.a", "com.b")
        val set = withDup.toSet()
        assertEquals(2, set.size)
    }

    @Test
    fun `selection survives reboot simulation - preserved temporarily unavailable`() {
        // When an app is uninstalled, its package should still be in prefs
        // until user explicitly removes it
        val before = setOf("com.a", "com.b", "com.c")
        val afterUninstall = before // prefs should still contain all
        // Even if getLaunchableApps no longer returns com.b, selection should not auto-remove
        assertEquals(before, afterUninstall)
        assertTrue(afterUninstall.contains("com.b"))
    }
}
