package com.hidden.launcher

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the home-screen loading pipeline:
 * - loading state vs. genuine empty selection
 * - instant paint from cached metadata, skeleton tiles when nothing is cached
 * - selected-only resolution and uninstalled/disabled handling
 * - availability always re-checked (stale cache cannot fake availability)
 */
class HomeLoadingTest {

    private fun cached(pkg: String, label: String, enabled: Boolean = true): CachedAppMeta =
        CachedAppMeta(pkg, label, enabled, versionCode = 1L, lastUpdateTime = 10L, cachedAt = 100L)

    private fun available(
        pkg: String,
        label: String,
        enabled: Boolean = true,
        launchable: Boolean = true
    ): AppAvailability = AppAvailability(
        packageName = pkg,
        installed = true,
        enabled = enabled,
        launchable = enabled && launchable,
        label = label,
        versionCode = 1L,
        lastUpdateTime = 10L
    )

    // ---- Loading vs. empty state ----

    @Test
    fun `empty selection is an empty state, not a loading state`() {
        assertEquals(HomeUiState.EMPTY, HomeState.initialState(emptySet()))
    }

    @Test
    fun `non empty selection starts in loading state and never shows empty state`() {
        assertEquals(HomeUiState.LOADING, HomeState.initialState(setOf("com.a")))
        assertNotEquals(HomeUiState.EMPTY, HomeState.initialState(setOf("com.a")))
    }

    // ---- Instant first paint ----

    @Test
    fun `cached metadata renders real labelled tiles before package manager work`() {
        val displays = AppRepository.buildSelectedDisplays(
            selectedPackages = setOf("com.chrome"),
            cachedMetadata = mapOf("com.chrome" to cached("com.chrome", "Chrome")),
            availability = emptyMap()
        )

        assertEquals(1, displays.size)
        assertEquals("Chrome", displays[0].label)
        assertFalse(displays[0].isPlaceholder)
        assertTrue(displays[0].isAvailable)
    }

    @Test
    fun `unknown package without cache becomes a lightweight placeholder`() {
        val displays = AppRepository.buildSelectedDisplays(
            selectedPackages = setOf("com.brand.new"),
            cachedMetadata = emptyMap(),
            availability = emptyMap()
        )

        val display = displays.single()
        assertTrue(display.isPlaceholder)
        assertEquals("com.brand.new", display.packageName)
        assertNull(display.icon)
    }

    @Test
    fun `placeholder is replaced by a real tile as soon as metadata is available`() {
        val placeholder = AppRepository.buildSelectedDisplays(
            selectedPackages = setOf("com.a"),
            cachedMetadata = emptyMap(),
            availability = emptyMap()
        ).single()
        assertTrue(placeholder.isPlaceholder)

        val resolved = AppRepository.buildSelectedDisplays(
            selectedPackages = setOf("com.a"),
            cachedMetadata = emptyMap(),
            availability = mapOf("com.a" to available("com.a", "App A"))
        ).single()

        assertFalse(resolved.isPlaceholder)
        assertEquals("App A", resolved.label)
        assertTrue(resolved.isAvailable)
        assertTrue(resolved.launchable)
    }

    // ---- Selected-only resolution ----

    @Test
    fun `only selected packages are resolved`() {
        val cachedMeta = mapOf(
            "com.a" to cached("com.a", "A"),
            "com.unselected" to cached("com.unselected", "Nope")
        )
        val availability = mapOf(
            "com.a" to available("com.a", "A"),
            "com.unselected" to available("com.unselected", "Nope")
        )

        val displays = AppRepository.buildSelectedDisplays(setOf("com.a"), cachedMeta, availability)

        assertEquals(1, displays.size)
        assertEquals("com.a", displays.single().packageName)
    }

    @Test
    fun `icons are only looked up for selected packages`() {
        val lookedUp = mutableListOf<String>()
        AppRepository.buildSelectedDisplays(
            selectedPackages = setOf("com.a", "com.b"),
            cachedMetadata = mapOf("com.unselected" to cached("com.unselected", "X")),
            availability = emptyMap(),
            iconLookup = { pkg ->
                lookedUp.add(pkg)
                null
            }
        )

        assertEquals(setOf("com.a", "com.b"), lookedUp.toSet())
    }

    @Test
    fun `empty selection resolves to empty list without lookups`() {
        var lookups = 0
        val displays = AppRepository.buildSelectedDisplays(
            selectedPackages = emptySet(),
            cachedMetadata = emptyMap(),
            availability = emptyMap(),
            iconLookup = { lookups++; null }
        )
        assertTrue(displays.isEmpty())
        assertEquals(0, lookups)
    }

    // ---- Missing / uninstalled / disabled packages ----

    @Test
    fun `uninstalled app stays visible and is marked unavailable`() {
        val displays = AppRepository.buildSelectedDisplays(
            selectedPackages = setOf("com.gone"),
            cachedMetadata = mapOf("com.gone" to cached("com.gone", "Gone")),
            availability = mapOf("com.gone" to AppAvailability.missing("com.gone"))
        )

        val display = displays.single()
        assertEquals("com.gone", display.packageName)
        assertFalse(display.isAvailable)
        assertFalse(display.launchable)
        assertFalse(display.isPlaceholder)
        // Selection is preserved, never silently dropped.
        assertEquals(1, displays.size)
    }

    @Test
    fun `stale cache never fakes availability`() {
        // Cache claims the app is enabled and labelled, but PackageManager says it is gone.
        val displays = AppRepository.buildSelectedDisplays(
            selectedPackages = setOf("com.removed"),
            cachedMetadata = mapOf("com.removed" to cached("com.removed", "Removed App", enabled = true)),
            availability = mapOf("com.removed" to AppAvailability.missing("com.removed"))
        )

        assertFalse(displays.single().isAvailable)
        assertFalse(displays.single().launchable)
    }

    @Test
    fun `disabled app is installed but not launchable`() {
        val displays = AppRepository.buildSelectedDisplays(
            selectedPackages = setOf("com.disabled"),
            cachedMetadata = mapOf("com.disabled" to cached("com.disabled", "Disabled App")),
            availability = mapOf(
                "com.disabled" to available("com.disabled", "Disabled App", enabled = false)
            )
        )

        val display = displays.single()
        assertFalse(display.isAvailable)
        assertFalse(display.launchable)
        assertEquals("Disabled App", display.label)
    }

    @Test
    fun `installed app without launch intent is not launchable`() {
        val displays = AppRepository.buildSelectedDisplays(
            selectedPackages = setOf("com.nolauncher"),
            cachedMetadata = emptyMap(),
            availability = mapOf(
                "com.nolauncher" to available("com.nolauncher", "No Launcher", launchable = false)
            )
        )

        val display = displays.single()
        assertTrue(display.isAvailable)
        assertFalse(display.launchable)
    }

    @Test
    fun `display list is stable and sorted`() {
        val displays = AppRepository.buildSelectedDisplays(
            selectedPackages = setOf("com.z", "com.a", "com.m"),
            cachedMetadata = mapOf(
                "com.z" to cached("com.z", "Zebra"),
                "com.a" to cached("com.a", "Apple"),
                "com.m" to cached("com.m", "Mango")
            ),
            availability = emptyMap()
        )
        assertEquals(listOf("Apple", "Mango", "Zebra"), displays.map { it.label })
    }

    // ---- Change detection ----

    @Test
    fun `signature changes when selection changes`() {
        val base = HomeState.selectionSignature(setOf("com.a"), emptyList())
        val changed = HomeState.selectionSignature(setOf("com.a", "com.b"), emptyList())
        assertNotEquals(base, changed)
        assertEquals(base, HomeState.selectionSignature(setOf("com.a"), emptyList()))
    }

    @Test
    fun `signature changes when groups change`() {
        val noGroups = HomeState.selectionSignature(setOf("com.a", "com.b"), emptyList())
        val grouped = HomeState.selectionSignature(
            setOf("com.a", "com.b"),
            listOf(AppGroup("id1", "Work", listOf("com.a", "com.b")))
        )
        assertNotEquals(noGroups, grouped)
    }
}
