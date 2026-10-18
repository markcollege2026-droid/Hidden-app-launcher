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
 * Display model for main grid (selected apps only)
 */
data class SelectedAppDisplay(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isAvailable: Boolean,
    val launchable: Boolean
)
