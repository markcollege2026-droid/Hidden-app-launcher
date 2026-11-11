package com.hidden.launcher

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tiny key/value abstraction over SharedPreferences so [AppMetadataCache] can be
 * unit tested without Android (and so it stays trivially replaceable).
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class SharedPrefsKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

/**
 * Very small persistent cache of app labels/metadata for the *selected* packages.
 *
 * Purpose: the home screen can paint real app names on the very first frame without
 * blocking on PackageManager. The cache only supplies labels and a best-effort
 * enabled flag; availability is always re-checked against PackageManager, so stale
 * entries can never make an uninstalled/disabled app look usable in the final result.
 *
 * Entries are invalidated on package add/remove/change/replace broadcasts and when a
 * refresh observes a different versionCode/lastUpdateTime, and the cache is pruned to
 * the current selection so it never grows beyond [maxEntries].
 */
class AppMetadataCache(
    private val store: KeyValueStore,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private var entries: MutableMap<String, CachedAppMeta>? = null

    @Synchronized
    fun snapshot(): Map<String, CachedAppMeta> = load().toMap()

    @Synchronized
    fun get(packageName: String): CachedAppMeta? = load()[packageName]

    @Synchronized
    fun put(meta: CachedAppMeta) {
        val map = load()
        map[meta.packageName] = meta
        save(map)
    }

    @Synchronized
    fun putAll(metas: Collection<CachedAppMeta>) {
        if (metas.isEmpty()) return
        val map = load()
        for (m in metas) map[m.packageName] = m
        save(map)
    }

    /** Drop a single package (uninstall / update / enable-state change). */
    @Synchronized
    fun invalidate(packageName: String) {
        val map = load()
        if (map.remove(packageName) != null) save(map)
    }

    /** Keep only entries for the given packages (called with the current selection). */
    @Synchronized
    fun retainOnly(packageNames: Set<String>) {
        val map = load()
        val removed = map.keys.filterNot { it in packageNames }
        if (removed.isEmpty()) return
        for (key in removed) map.remove(key)
        save(map)
    }

    @Synchronized
    fun clear() {
        entries = mutableMapOf()
        store.putString(KEY_METADATA, "[]")
    }

    @Synchronized
    fun size(): Int = load().size

    /**
     * True when a cached entry still matches the live package (same version and update
     * stamp). Used for bookkeeping/tests only - availability is always re-read live.
     */
    fun isFresh(meta: CachedAppMeta, availability: AppAvailability): Boolean {
        if (!availability.installed) return false
        if (availability.versionCode < 0) return false
        return meta.versionCode == availability.versionCode &&
            meta.lastUpdateTime == availability.lastUpdateTime
    }

    private fun load(): MutableMap<String, CachedAppMeta> {
        entries?.let { return it }
        val map = decode(store.getString(KEY_METADATA))
        entries = map
        return map
    }

    private fun save(map: MutableMap<String, CachedAppMeta>) {
        val trimmed = if (map.size > maxEntries) {
            map.values.sortedByDescending { it.cachedAt }.take(maxEntries)
        } else {
            map.values.toList()
        }
        val keep = trimmed.map { it.packageName }.toSet()
        map.keys.retainAll(keep)
        store.putString(KEY_METADATA, encode(trimmed))
    }

    /** Build a fresh cache entry stamped with the current time. */
    fun entryFor(availability: AppAvailability, now: Long = clock()): CachedAppMeta? {
        if (!availability.installed) return null
        val label = availability.label ?: return null
        return CachedAppMeta(
            packageName = availability.packageName,
            label = label,
            enabled = availability.enabled,
            versionCode = availability.versionCode,
            lastUpdateTime = availability.lastUpdateTime,
            cachedAt = now
        )
    }

    companion object {
        const val KEY_METADATA = "app_metadata"
        const val DEFAULT_MAX_ENTRIES = 200

        fun encode(metas: List<CachedAppMeta>): String {
            val arr = JSONArray()
            for (m in metas) {
                val obj = JSONObject()
                obj.put("p", m.packageName)
                obj.put("l", m.label)
                obj.put("e", m.enabled)
                obj.put("v", m.versionCode)
                obj.put("u", m.lastUpdateTime)
                obj.put("t", m.cachedAt)
                arr.put(obj)
            }
            return arr.toString()
        }

        fun decode(json: String?): MutableMap<String, CachedAppMeta> {
            val out = LinkedHashMap<String, CachedAppMeta>()
            if (json.isNullOrBlank()) return out
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val pkg = obj.optString("p", "")
                    if (pkg.isBlank()) continue
                    out[pkg] = CachedAppMeta(
                        packageName = pkg,
                        label = obj.optString("l", pkg).ifBlank { pkg },
                        enabled = obj.optBoolean("e", true),
                        versionCode = obj.optLong("v", -1L),
                        lastUpdateTime = obj.optLong("u", -1L),
                        cachedAt = obj.optLong("t", 0L)
                    )
                }
            } catch (e: Exception) {
                return LinkedHashMap()
            }
            return out
        }
    }
}
