package com.hidden.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache

/**
 * Centralizes PackageManager queries with caching and error handling.
 * Handles Android 11+ package visibility via <queries> declaration.
 *
 * Threading contract: every method that talks to PackageManager is blocking and must
 * be called off the UI thread. The home screen only asks about *selected* packages
 * (labels/availability/icon per package) instead of enumerating all installed apps;
 * full enumeration remains available for the picker.
 */
class AppRepository(
    private val packageManager: PackageManager,
    private val metadataCache: AppMetadataCache? = null
) {

    /**
     * Returns all launchable installed apps (those with MAIN/LAUNCHER intent).
     * Result is sorted alphabetically by label.
     * Runs PackageManager query - should be called off UI thread or cached.
     * Used by the app picker only; the home screen never enumerates all packages.
     */
    fun getLaunchableApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.MATCH_ALL
        // For Android 13+ use ResolveInfoFlags, fallback handled via suppression
        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, flags)
            }
        } catch (e: Exception) {
            // SecurityException or other - return empty gracefully
            return emptyList()
        }

        val apps = mutableListOf<AppInfo>()
        for (ri in resolveInfos) {
            try {
                val packageName = ri.activityInfo.packageName ?: continue
                val label = ri.loadLabel(packageManager)?.toString() ?: packageName
                // Try cache first, then load (kept for picker compatibility)
                val icon = try {
                    getCachedIcon(packageName) ?: ri.loadIcon(packageManager)?.also {
                        sharedIconCache.put(packageName, it)
                    }
                } catch (e: Exception) {
                    null
                }
                apps.add(AppInfo(packageName = packageName, label = label, icon = icon))
            } catch (e: Exception) {
                continue
            }
        }
        return apps.sortedBy { it.label.lowercase() }
    }

    /**
     * Get launch intent for a package, handling null and exceptions.
     */
    fun getLaunchIntent(packageName: String): Intent? {
        return try {
            packageManager.getLaunchIntentForPackage(packageName)
        } catch (e: Exception) {
            null
        }
    }

    // ---- Selected-app-only metadata queries (home screen hot path) ----

    /** Last known metadata for selected packages (persistent, tiny, no icons). */
    fun readCachedMetadata(): Map<String, CachedAppMeta> =
        metadataCache?.snapshot() ?: emptyMap()

    /** Icon from the in-memory LruCache only; never touches PackageManager. */
    fun getCachedIcon(packageName: String): Drawable? = sharedIconCache.get(packageName)

    fun isIconCached(packageName: String): Boolean = getCachedIcon(packageName) != null

    /**
     * Load (and cache) one icon. Never re-loads an icon that is already cached.
     * Must be called off the UI thread.
     */
    fun loadIcon(packageName: String): Drawable? {
        getCachedIcon(packageName)?.let { return it }
        return try {
            packageManager.getApplicationIcon(packageName)?.also {
                sharedIconCache.put(packageName, it)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Live availability check for a single selected package.
     * Installed-but-disabled and uninstalled packages are reported explicitly.
     * Must be called off the UI thread.
     */
    fun queryAvailability(packageName: String): AppAvailability {
        return try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            val appInfo = pkgInfo.applicationInfo
                ?: return AppAvailability.missing(packageName)
            val label = try {
                packageManager.getApplicationLabel(appInfo)?.toString()
            } catch (e: Exception) {
                null
            }
            val enabledSetting = try {
                packageManager.getApplicationEnabledSetting(packageName)
            } catch (e: Exception) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            }
            val enabled = appInfo.enabled &&
                enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                enabledSetting != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            val hasLaunchIntent = try {
                packageManager.getLaunchIntentForPackage(packageName) != null
            } catch (e: Exception) {
                false
            }
            AppAvailability(
                packageName = packageName,
                installed = true,
                enabled = enabled,
                launchable = enabled && hasLaunchIntent,
                label = label,
                versionCode = pkgInfo.longVersionCode,
                lastUpdateTime = pkgInfo.lastUpdateTime
            )
        } catch (e: PackageManager.NameNotFoundException) {
            AppAvailability.missing(packageName)
        } catch (e: Exception) {
            AppAvailability.missing(packageName)
        }
    }

    /**
     * Persist metadata for the packages that were just validated. Packages that are no
     * longer installed are dropped from the cache so stale data never lingers.
     */
    fun rememberAvailability(results: Collection<AppAvailability>, now: Long = System.currentTimeMillis()) {
        val cache = metadataCache ?: return
        val fresh = ArrayList<CachedAppMeta>(results.size)
        for (result in results) {
            if (result.installed) {
                cache.entryFor(result, now)?.let { fresh.add(it) }
            } else {
                cache.invalidate(result.packageName)
            }
        }
        cache.putAll(fresh)
    }

    /** Cache labels discovered by the picker's full enumeration (version unknown). */
    fun rememberLabels(apps: Collection<AppInfo>, now: Long = System.currentTimeMillis()) {
        val cache = metadataCache ?: return
        val entries = apps.mapNotNull { app ->
            val label = app.label.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CachedAppMeta(
                packageName = app.packageName,
                label = label,
                enabled = app.isAvailable,
                versionCode = -1L,
                lastUpdateTime = -1L,
                cachedAt = now
            )
        }
        cache.putAll(entries)
    }

    /** Keep the persistent metadata cache bounded to the current selection. */
    fun pruneMetadataCache(selectedPackages: Set<String>) {
        metadataCache?.retainOnly(selectedPackages)
    }

    /** Invalidate everything we cached for one package (install/remove/update/disable). */
    fun invalidatePackage(packageName: String) {
        metadataCache?.invalidate(packageName)
        sharedIconCache.remove(packageName)
    }

    /**
     * Check if package is still installed and launchable.
     * Returns detailed info for selected app.
     */
    fun getAppForPackage(packageName: String, cachedAllApps: Map<String, AppInfo>? = null): SelectedAppDisplay {
        // Try cached map first
        cachedAllApps?.get(packageName)?.let { cached ->
            val intent = getLaunchIntent(packageName)
            return SelectedAppDisplay(
                packageName = packageName,
                label = cached.label,
                icon = cached.icon ?: loadIcon(packageName),
                isAvailable = true,
                launchable = intent != null
            )
        }

        // Fallback: query individually
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val enabled = appInfo.enabled
            val label = try {
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }
            val icon = loadIcon(packageName)
            val launchIntent = getLaunchIntent(packageName)
            SelectedAppDisplay(
                packageName = packageName,
                label = label,
                icon = icon,
                isAvailable = enabled,
                launchable = launchIntent != null && enabled
            )
        } catch (e: PackageManager.NameNotFoundException) {
            SelectedAppDisplay(
                packageName = packageName,
                label = packageName,
                icon = null,
                isAvailable = false,
                launchable = false
            )
        } catch (e: Exception) {
            SelectedAppDisplay(
                packageName = packageName,
                label = packageName,
                icon = null,
                isAvailable = false,
                launchable = false
            )
        }
    }

    fun clearCache() {
        sharedIconCache.evictAll()
    }

    companion object {
        private const val ICON_CACHE_SIZE = 100

        /**
         * Icon LruCache retained across activities: icons loaded by the picker are
         * reused by the home screen and vice versa, avoiding duplicate loads.
         * android.util.LruCache is internally synchronized.
         */
        private val sharedIconCache = LruCache<String, Drawable>(ICON_CACHE_SIZE)

        /** Shared repository factory used by both activities. */
        fun create(context: Context): AppRepository {
            val prefs = context.getSharedPreferences(PrefsHelper.PREFS_NAME, Context.MODE_PRIVATE)
            return AppRepository(
                packageManager = context.packageManager,
                metadataCache = AppMetadataCache(SharedPrefsKeyValueStore(prefs))
            )
        }

        /**
         * Build the home-screen display list for the selected packages only.
         *
         * [availability] holds the results of live PackageManager checks (missing
         * entries mean "not validated yet"). [cachedMetadata] is only used for labels
         * and optimistic first paint, never to declare a package available.
         *
         * Selected packages that are not in [availability] produce placeholder/skeleton
         * tiles (or cached labels) instead of disappearing, and missing packages stay
         * in the list marked unavailable so selections are never silently dropped.
         */
        fun buildSelectedDisplays(
            selectedPackages: Set<String>,
            cachedMetadata: Map<String, CachedAppMeta>,
            availability: Map<String, AppAvailability>,
            iconLookup: (String) -> Drawable? = { null }
        ): List<SelectedAppDisplay> {
            if (selectedPackages.isEmpty()) return emptyList()
            val out = ArrayList<SelectedAppDisplay>(selectedPackages.size)
            for (pkg in selectedPackages) {
                val cached = cachedMetadata[pkg]
                val live = availability[pkg]
                val icon = try {
                    iconLookup(pkg)
                } catch (e: Exception) {
                    null
                }
                val display = when {
                    live == null -> {
                        if (cached != null) {
                            // Previously known package: show its real tile immediately.
                            SelectedAppDisplay(
                                packageName = pkg,
                                label = cached.label,
                                icon = icon,
                                isAvailable = cached.enabled,
                                launchable = cached.enabled
                            )
                        } else {
                            SelectedAppDisplay(
                                packageName = pkg,
                                label = "",
                                icon = icon,
                                isAvailable = false,
                                launchable = false,
                                isPlaceholder = true
                            )
                        }
                    }
                    !live.installed -> SelectedAppDisplay(pkg, pkg, icon, false, false)
                    else -> {
                        val label = live.label?.takeIf { it.isNotBlank() }
                            ?: cached?.label
                            ?: pkg
                        SelectedAppDisplay(
                            packageName = pkg,
                            label = label,
                            icon = icon,
                            isAvailable = live.enabled,
                            launchable = live.enabled && live.launchable
                        )
                    }
                }
                out.add(display)
            }
            return out.sortedWith(
                compareBy({ it.isPlaceholder }, { it.label.lowercase() }, { it.packageName })
            )
        }

        /**
         * Pure logic for handling uninstalled/disabled without needing PM.
         * Used for unit tests.
         */
        fun resolveSelectedApps(
            selectedPackages: Set<String>,
            availableAppsMap: Map<String, AppInfo>,
            launchableCheck: (String) -> Boolean
        ): List<SelectedAppDisplay> {
            return selectedPackages.map { pkg ->
                val known = availableAppsMap[pkg]
                if (known != null) {
                    SelectedAppDisplay(
                        packageName = pkg,
                        label = known.label,
                        icon = known.icon,
                        isAvailable = true,
                        launchable = launchableCheck(pkg)
                    )
                } else {
                    SelectedAppDisplay(
                        packageName = pkg,
                        label = pkg,
                        icon = null,
                        isAvailable = false,
                        launchable = false
                    )
                }
            }
        }
    }
}
