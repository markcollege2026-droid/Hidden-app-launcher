package com.hidden.launcher

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests selection persistence filtering, empty state, sanitization,
 * and that only selected apps are displayed (core requirement).
 */
class PrefsHelperTest {

    @Test
    fun `sanitize removes null and blank`() {
        val raw: Set<String?> = setOf("com.example.app", null, "", "  ", "com.other")
        val sanitized = PrefsHelper.sanitizeSelectedSet(raw)
        assertEquals(setOf("com.example.app", "com.other"), sanitized)
    }

    @Test
    fun `filterToSelected returns only selected apps`() {
        val all = listOf(
            AppInfo("com.a", "A"),
            AppInfo("com.b", "B"),
            AppInfo("com.c", "C")
        )
        val selected = setOf("com.a", "com.c")
        val filtered = PrefsHelper.filterToSelected(all, selected)
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.packageName in selected })
        assertFalse(filtered.any { it.packageName == "com.b" })
    }

    @Test
    fun `filterToSelected never exposes unselected apps`() {
        val all = listOf(
            AppInfo("com.a", "A"),
            AppInfo("com.b", "B")
        )
        // Even if all apps provided, only selected should be returned
        val selected = setOf("com.a")
        val filtered = PrefsHelper.filterToSelected(all, selected)
        assertEquals(1, filtered.size)
        assertEquals("com.a", filtered[0].packageName)
    }

    @Test
    fun `filterToSelected returns empty when selection empty`() {
        val all = listOf(AppInfo("com.a", "A"))
        val filtered = PrefsHelper.filterToSelected(all, emptySet())
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `shouldShowEmptyState true when empty`() {
        assertTrue(PrefsHelper.shouldShowEmptyState(emptySet()))
    }

    @Test
    fun `shouldShowEmptyState false when not empty`() {
        assertFalse(PrefsHelper.shouldShowEmptyState(setOf("com.a")))
    }

    @Test
    fun `empty selection never falls back to all apps`() {
        // Core requirement: never display all apps by default
        val all = listOf(
            AppInfo("com.a", "A"),
            AppInfo("com.b", "B"),
            AppInfo("com.c", "C")
        )
        val emptySelection = emptySet<String>()
        val result = PrefsHelper.filterToSelected(all, emptySelection)
        // Must be empty, not all apps
        assertTrue(result.isEmpty())
        assertNotEquals(all.size, result.size)
    }
}
