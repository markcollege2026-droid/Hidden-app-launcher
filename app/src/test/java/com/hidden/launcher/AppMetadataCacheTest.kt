package com.hidden.launcher

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the small persistent metadata cache: round trip, invalidation, bounding and
 * freshness checks. Uses an in-memory [KeyValueStore] so no Android is required.
 */
class AppMetadataCacheTest {

    private class FakeStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
        val values = initial.toMutableMap()
        var writes = 0
            private set

        override fun getString(key: String): String? = values[key]

        override fun putString(key: String, value: String) {
            writes++
            values[key] = value
        }
    }

    private fun meta(pkg: String, label: String, version: Long = 1, cachedAt: Long = 1): CachedAppMeta =
        CachedAppMeta(pkg, label, enabled = true, versionCode = version, lastUpdateTime = 10L, cachedAt = cachedAt)

    @Test
    fun `put and get round trip`() {
        val cache = AppMetadataCache(FakeStore())
        cache.put(meta("com.a", "App A"))

        assertEquals("App A", cache.get("com.a")?.label)
        assertEquals(1, cache.size())
        assertNull(cache.get("com.unknown"))
    }

    @Test
    fun `entries survive a new cache instance (persistence)`() {
        val store = FakeStore()
        AppMetadataCache(store).put(meta("com.a", "App A"))

        val reopened = AppMetadataCache(store)
        assertEquals("App A", reopened.get("com.a")?.label)
    }

    @Test
    fun `invalidate removes a single package and persists`() {
        val store = FakeStore()
        val cache = AppMetadataCache(store)
        cache.putAll(listOf(meta("com.a", "A"), meta("com.b", "B")))

        cache.invalidate("com.a")
        assertNull(cache.get("com.a"))
        assertNotNull(cache.get("com.b"))
        assertNull(AppMetadataCache(store).get("com.a"))
    }

    @Test
    fun `retainOnly prunes to the current selection`() {
        val store = FakeStore()
        val cache = AppMetadataCache(store)
        cache.putAll(listOf(meta("com.a", "A"), meta("com.b", "B"), meta("com.c", "C")))

        cache.retainOnly(setOf("com.a", "com.c"))
        assertEquals(setOf("com.a", "com.c"), cache.snapshot().keys)
        assertEquals(setOf("com.a", "com.c"), AppMetadataCache(store).snapshot().keys)
    }

    @Test
    fun `cache is bounded to max entries keeping newest`() {
        val cache = AppMetadataCache(FakeStore(), maxEntries = 2)
        cache.put(meta("com.old", "Old", cachedAt = 1))
        cache.put(meta("com.mid", "Mid", cachedAt = 2))
        cache.put(meta("com.new", "New", cachedAt = 3))

        assertEquals(2, cache.size())
        assertNull(cache.get("com.old"))
        assertNotNull(cache.get("com.mid"))
        assertNotNull(cache.get("com.new"))
    }

    @Test
    fun `clear empties the cache`() {
        val cache = AppMetadataCache(FakeStore())
        cache.put(meta("com.a", "A"))
        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get("com.a"))
    }

    @Test
    fun `entryFor builds fresh entry from availability`() {
        val cache = AppMetadataCache(FakeStore(), clock = { 1234L })
        val availability = AppAvailability(
            packageName = "com.a",
            installed = true,
            enabled = true,
            launchable = true,
            label = "App A",
            versionCode = 7L,
            lastUpdateTime = 99L
        )
        assertNotNull(cache.entryFor(availability))
        val entry = requireNotNull(cache.entryFor(availability))
        assertEquals("App A", entry.label)
        assertEquals(7L, entry.versionCode)
        assertEquals(99L, entry.lastUpdateTime)
        assertEquals(1234L, entry.cachedAt)
    }

    @Test
    fun `entryFor returns null for missing packages or unknown label`() {
        val cache = AppMetadataCache(FakeStore())
        assertNull(cache.entryFor(AppAvailability.missing("com.gone")))
        assertNull(
            cache.entryFor(
                AppAvailability("com.x", installed = true, enabled = true, launchable = true, label = null)
            )
        )
    }

    @Test
    fun `isFresh detects updated or updated-version packages`() {
        val cache = AppMetadataCache(FakeStore())
        val entry = meta("com.a", "A", version = 1)

        val sameVersion = AppAvailability("com.a", true, true, true, "A", 1L, 10L)
        assertTrue(cache.isFresh(entry, sameVersion))

        val newVersion = AppAvailability("com.a", true, true, true, "A", 2L, 10L)
        assertFalse(cache.isFresh(entry, newVersion))

        val newUpdateTime = AppAvailability("com.a", true, true, true, "A", 1L, 11L)
        assertFalse(cache.isFresh(entry, newUpdateTime))

        assertFalse(cache.isFresh(entry, AppAvailability.missing("com.a")))
    }

    @Test
    fun `malformed json decodes to empty cache`() {
        val store = FakeStore(mapOf(AppMetadataCache.KEY_METADATA to "{not valid json"))
        val cache = AppMetadataCache(store)
        assertEquals(0, cache.size())
        assertNull(cache.get("com.a"))
    }

    @Test
    fun `blank or missing json decodes to empty cache`() {
        assertTrue(AppMetadataCache.decode(null).isEmpty())
        assertTrue(AppMetadataCache.decode("").isEmpty())
        assertTrue(AppMetadataCache.decode("[]").isEmpty())
    }

    @Test
    fun `encode and decode are lossless`() {
        val original = listOf(meta("com.a", "A", version = 3, cachedAt = 5), meta("com.b", "B"))
        val decoded = AppMetadataCache.decode(AppMetadataCache.encode(original))
        assertEquals(2, decoded.size)
        assertEquals("A", decoded["com.a"]?.label)
        assertEquals(3L, decoded["com.a"]?.versionCode)
        assertEquals(5L, decoded["com.a"]?.cachedAt)
    }

    @Test
    fun `no writes happen when nothing changes`() {
        val store = FakeStore()
        val cache = AppMetadataCache(store)
        cache.put(meta("com.a", "A"))
        val writes = store.writes

        cache.retainOnly(setOf("com.a"))
        cache.invalidate("com.unknown")
        assertEquals(writes, store.writes)
    }
}
