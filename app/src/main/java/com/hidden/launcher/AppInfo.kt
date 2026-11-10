package com.hidden.launcher

import android.graphics.drawable.Drawable

/**
 * Represents a launchable app.
 * @param packageName package name
 * @param label human-readable label
 * @param icon drawable icon (may be null if loading failed)
 * @param isAvailable true if app is installed and launchable
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
    val isAvailable: Boolean = true
)

/**
 * Display model for main grid (selected apps only).
 *
 * @param isPlaceholder true while app information is still being resolved; the home
 *   screen renders a lightweight skeleton tile instead of a real app tile. Placeholder
 *   tiles are never persisted and never block an availability check.
 */
data class SelectedAppDisplay(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isAvailable: Boolean,
    val launchable: Boolean,
    val isPlaceholder: Boolean = false
)

/**
 * Small persistent metadata record so the home screen can paint real labels
 * immediately on startup without touching PackageManager on the UI thread.
 *
 * Only a label, an enabled flag and version/update stamps are stored (no icons, no
 * bitmaps) so the cache stays tiny. It is never treated as an availability source:
 * every refresh re-queries PackageManager for the selected packages.
 */
data class CachedAppMeta(
    val packageName: String,
    val label: String,
    val enabled: Boolean,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val cachedAt: Long
)

/**
 * Result of a live PackageManager availability check for a single package.
 */
data class AppAvailability(
    val packageName: String,
    val installed: Boolean,
    val enabled: Boolean,
    val launchable: Boolean,
    val label: String? = null,
    val versionCode: Long = -1L,
    val lastUpdateTime: Long = -1L
) {
    val isUsable: Boolean get() = installed && enabled

    companion object {
        fun missing(packageName: String): AppAvailability =
            AppAvailability(packageName = packageName, installed = false, enabled = false, launchable = false)
    }
}
