package com.hidden.launcher

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight persistence for selected package names using SharedPreferences.
 * Stores a StringSet under key "selected_packages".
 * No database needed.
 */
class PrefsHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedPackages(): Set<String> {
        // Must copy the set because SharedPreferences returns a reference that should not be mutated
        return prefs.getStringSet(KEY_SELECTED, emptySet())?.toSet() ?: emptySet()
    }

    fun setSelectedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_SELECTED, packages.toSet()).apply()
    }

    fun isSelected(packageName: String): Boolean {
        return getSelectedPackages().contains(packageName)
    }

    fun addPackage(packageName: String) {
        val current = getSelectedPackages().toMutableSet()
        current.add(packageName)
        setSelectedPackages(current)
    }

    fun removePackage(packageName: String) {
        val current = getSelectedPackages().toMutableSet()
        current.remove(packageName)
        setSelectedPackages(current)
    }

    companion object {
        const val PREFS_NAME = "hidden_launcher_prefs"
        const val KEY_SELECTED = "selected_packages"

        /**
         * Pure logic helpers that can be unit tested without Android.
         */

        // Filter list to only selected packages - never expose unselected
        fun filterToSelected(allApps: List<AppInfo>, selected: Set<String>): List<AppInfo> {
            if (selected.isEmpty()) return emptyList()
            return allApps.filter { it.packageName in selected }
        }

        // Determine if empty state should be shown
        fun shouldShowEmptyState(selected: Set<String>): Boolean {
            return selected.isEmpty()
        }

        // Sanitize persisted set: remove null/blank entries
        fun sanitizeSelectedSet(raw: Set<String?>): Set<String> {
            return raw.filterNotNull().filter { it.isNotBlank() }.toSet()
        }
    }
}
