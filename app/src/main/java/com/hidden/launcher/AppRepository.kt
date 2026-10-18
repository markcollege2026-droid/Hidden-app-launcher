package com.hidden.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache

/**
 * Centralizes PackageManager queries with caching and error handling.
 * Handles Android 11+ package visibility via <queries> declaration.
 */
class AppRepository(private val packageManager: PackageManager) {

    // Simple memory cache for icons to avoid redundant loads
    private val iconCache = LruCache<String, Drawable>(100)

    /**
     * Returns all launchable installed apps (those with MAIN/LAUNCHER intent).
     * Result is sorted alphabetically by label.
     * Runs PackageManager query - should be called off UI thread or cached.
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
                // Defer icon loading for speed; use loadIcon lazily or cache
                val icon = try {
                    // Try cache first
                    iconCache.get(packageName) ?: run {
                        val d = ri.loadIcon(packageManager)
                        if (d != null) iconCache.put(packageName, d)
                        d
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
                icon = cached.icon ?: getAppIcon(packageName),
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
            val icon = getAppIcon(packageName)
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

    private fun getAppIcon(packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }
        return try {
            val icon = packageManager.getApplicationIcon(packageName)
            iconCache.put(packageName, icon)
            icon
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache() {
        iconCache.evictAll()
    }

    companion object {
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
