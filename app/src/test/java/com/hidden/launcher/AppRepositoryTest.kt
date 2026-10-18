package com.hidden.launcher

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests handling of uninstalled/disabled apps and launch intent logic.
 * Uses pure logic in AppRepository companion to avoid needing real PackageManager.
 */
class AppRepositoryTest {

    @Test
    fun `resolveSelectedApps handles available and unavailable`() {
        val availableMap = mapOf(
            "com.a" to AppInfo("com.a", "App A"),
            "com.b" to AppInfo("com.b", "App B")
        )
        val selected = setOf("com.a", "com.b", "com.missing")

        val result = AppRepository.resolveSelectedApps(
            selectedPackages = selected,
            availableAppsMap = availableMap,
            launchableCheck = { pkg -> pkg != "com.b" } // b not launchable
        )

        assertEquals(3, result.size)

        val a = result.find { it.packageName == "com.a" }!!
        assertTrue(a.isAvailable)
        assertTrue(a.launchable)
        assertEquals("App A", a.label)

        val b = result.find { it.packageName == "com.b" }!!
        assertTrue(b.isAvailable)
        assertFalse(b.launchable) // not launchable

        val missing = result.find { it.packageName == "com.missing" }!!
        assertFalse(missing.isAvailable)
        assertFalse(missing.launchable)
        assertEquals("com.missing", missing.label)
    }

    @Test
    fun `resolveSelectedApps preserves selections when temporarily unavailable`() {
        // Even if app missing from available map, selection is preserved as unavailable entry
        // Never silently replace or drop selections
        val availableMap = mapOf("com.a" to AppInfo("com.a", "App A"))
        val selected = setOf("com.a", "com.removed")

        val result = AppRepository.resolveSelectedApps(selected, availableMap) { true }

        // Both should be present, removed marked unavailable but still in list
        assertEquals(2, result.size)
        assertTrue(result.any { it.packageName == "com.removed" && !it.isAvailable })
    }

    @Test
    fun `resolveSelectedApps handles null launch intent safely`() {
        val availableMap = mapOf("com.a" to AppInfo("com.a", "App A"))
        val selected = setOf("com.a")

        val result = AppRepository.resolveSelectedApps(selected, availableMap) { false }

        val a = result.first()
        assertFalse(a.launchable)
        // Should not crash, should mark not launchable gracefully
    }

    @Test
    fun `resolveSelectedApps empty selection returns empty`() {
        val result = AppRepository.resolveSelectedApps(emptySet(), emptyMap()) { true }
        assertTrue(result.isEmpty())
    }
}
