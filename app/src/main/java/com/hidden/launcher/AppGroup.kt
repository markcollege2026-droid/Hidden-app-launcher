package com.hidden.launcher

import android.graphics.drawable.Drawable

/**
 * Represents a named group of apps created via drag-and-drop.
 * @param id unique ID (UUID)
 * @param name user-visible name
 * @param packageNames list of package names in this group
 */
data class AppGroup(
    val id: String,
    val name: String,
    val packageNames: List<String>
)

/**
 * Sealed class for items shown in main grid.
 * Either a single app or a group.
 */
sealed class MainItem {
    data class App(val display: SelectedAppDisplay) : MainItem()
    data class Group(val group: AppGroup, val memberDisplays: List<SelectedAppDisplay>) : MainItem()
    // Helper to get identifier for drag-and-drop
    fun identifier(): String = when (this) {
        is App -> "app:${display.packageName}"
        is Group -> "group:${group.id}"
    }
    fun label(): String = when (this) {
        is App -> display.label
        is Group -> group.name
    }
}
