package com.hidden.launcher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Lightweight persistence for selected package names and groups using SharedPreferences.
 * Stores a StringSet under key "selected_packages" and JSON string for groups.
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

    // --- Groups ---

    fun getGroups(): List<AppGroup> {
        val json = prefs.getString(KEY_GROUPS, null) ?: return emptyList()
        if (json.isBlank()) return emptyList()
        return try {
            parseGroupsJson(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setGroups(groups: List<AppGroup>) {
        val json = groupsToJson(groups)
        prefs.edit().putString(KEY_GROUPS, json).apply()
    }

    /**
     * Create a new group with given name and packages.
     * Generates UUID, persists, and returns created group.
     */
    fun createGroup(name: String, packageNames: List<String>): AppGroup {
        val groups = getGroups().toMutableList()
        val group = AppGroup(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Group ${groups.size + 1}" },
            packageNames = packageNames.distinct()
        )
        groups.add(group)
        setGroups(groups)
        return group
    }

    fun deleteGroup(groupId: String) {
        val groups = getGroups().filterNot { it.id == groupId }
        setGroups(groups)
    }

    fun renameGroup(groupId: String, newName: String) {
        val groups = getGroups().map {
            if (it.id == groupId) it.copy(name = newName.ifBlank { it.name }) else it
        }
        setGroups(groups)
    }

    fun addToGroup(groupId: String, packageName: String) {
        val groups = getGroups().map { g ->
            if (g.id == groupId && packageName !in g.packageNames) {
                g.copy(packageNames = g.packageNames + packageName)
            } else g
        }
        setGroups(groups)
    }

    fun removeFromGroup(groupId: String, packageName: String) {
        val groups = getGroups().mapNotNull { g ->
            if (g.id == groupId) {
                val newList = g.packageNames.filterNot { it == packageName }
                if (newList.isEmpty()) null else g.copy(packageNames = newList)
            } else g
        }
        setGroups(groups)
    }

    /**
     * Merge source group into target group (all packages from source added to target, source deleted)
     */
    fun mergeGroups(targetId: String, sourceId: String) {
        val groups = getGroups()
        val target = groups.find { it.id == targetId } ?: return
        val source = groups.find { it.id == sourceId } ?: return
        val mergedPackages = (target.packageNames + source.packageNames).distinct()
        val newGroups = groups.filterNot { it.id == sourceId }.map {
            if (it.id == targetId) it.copy(packageNames = mergedPackages) else it
        }
        setGroups(newGroups)
    }

    /**
     * Clean up groups after selection changes:
     * - Remove packages from groups if they are no longer selected
     * - Remove empty groups or groups with single member (optional: keep single? but we dissolve <2)
     * Returns updated groups.
     */
    fun pruneGroupsOnSelectionChange(selected: Set<String>): List<AppGroup> {
        val groups = getGroups()
        val pruned = groups.mapNotNull { g ->
            val remaining = g.packageNames.filter { it in selected }
            when {
                remaining.isEmpty() -> null
                remaining.size == 1 -> null // dissolve single-member groups - show as individual
                else -> g.copy(packageNames = remaining)
            }
        }
        // Only persist if changed
        if (pruned != groups) setGroups(pruned)
        return pruned
    }

    // Instance delegates (for getGroups/setGroups) - keep simple direct implementation
    fun groupsToJson(groups: List<AppGroup>): String {
        val arr = JSONArray()
        for (g in groups) {
            val obj = JSONObject()
            obj.put("id", g.id)
            obj.put("name", g.name)
            val pkgArr = JSONArray()
            for (pkg in g.packageNames) pkgArr.put(pkg)
            obj.put("pkgs", pkgArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun parseGroupsJson(json: String): List<AppGroup> {
        val arr = JSONArray(json)
        val out = mutableListOf<AppGroup>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val id = obj.getString("id")
            val name = obj.getString("name")
            val pkgsArr = obj.getJSONArray("pkgs")
            val pkgs = mutableListOf<String>()
            for (j in 0 until pkgsArr.length()) pkgs.add(pkgsArr.getString(j))
            out.add(AppGroup(id, name, pkgs))
        }
        return out
    }

    companion object {
        const val PREFS_NAME = "hidden_launcher_prefs"
        const val KEY_SELECTED = "selected_packages"
        const val KEY_GROUPS = "app_groups"

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

        /**
         * Build display items for main grid: groups + remaining individual apps
         * Pure logic for testing.
         */
        fun buildDisplayItems(
            selectedDisplays: List<SelectedAppDisplay>,
            groups: List<AppGroup>
        ): List<MainItem> {
            val displayMap = selectedDisplays.associateBy { it.packageName }
            val groupedPackages = groups.flatMap { it.packageNames }.toSet()
            val result = mutableListOf<MainItem>()

            // Add groups first (sorted by name)
            val sortedGroups = groups.sortedBy { it.name.lowercase() }
            for (g in sortedGroups) {
                val members = g.packageNames.mapNotNull { displayMap[it] }
                // Only show group if it has at least 2 members still available
                if (members.isNotEmpty()) {
                    result.add(MainItem.Group(g, members))
                }
            }

            // Add remaining individual apps not in any group
            val remaining = selectedDisplays.filter { it.packageName !in groupedPackages }
                .sortedBy { it.label.lowercase() }
            for (r in remaining) {
                result.add(MainItem.App(r))
            }

            // Sort overall? Keep groups first then apps? Or intermix alphabetically?
            // Current: groups sorted, then apps sorted - keeps groups prominent at top
            // Alternative: sort all by label case-insensitive mixing.
            // We'll keep groups first for clarity but could be sorted intermix if desired.
            return result
        }

        /**
         * Decide grouping action when dragging source onto target.
         * Returns description for testing.
         */
        fun groupingAction(source: MainItem, target: MainItem): String {
            return when {
                source is MainItem.App && target is MainItem.App -> "create_group"
                source is MainItem.App && target is MainItem.Group -> "add_to_group"
                source is MainItem.Group && target is MainItem.App -> "add_to_group"
                source is MainItem.Group && target is MainItem.Group -> "merge_groups"
                else -> "none"
            }
        }

        /**
         * Generate default group name from member displays.
         */
        fun generateGroupName(members: List<SelectedAppDisplay>, fallbackIndex: Int = 1): String {
            if (members.isEmpty()) return "Group $fallbackIndex"
            if (members.size == 2) return "${members[0].label} + ${members[1].label}"
            return members.take(2).joinToString(" + ") { it.label } + if (members.size > 2) " +${members.size - 2}" else ""
        }

        // Static JSON helpers for unit tests (no Context needed)
        fun groupsToJson(groups: List<AppGroup>): String {
            val arr = JSONArray()
            for (g in groups) {
                val obj = JSONObject()
                obj.put("id", g.id)
                obj.put("name", g.name)
                val pkgArr = JSONArray()
                for (pkg in g.packageNames) pkgArr.put(pkg)
                obj.put("pkgs", pkgArr)
                arr.put(obj)
            }
            return arr.toString()
        }

        fun parseGroupsJson(json: String): List<AppGroup> {
            val arr = JSONArray(json)
            val out = mutableListOf<AppGroup>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val pkgsArr = obj.getJSONArray("pkgs")
                val pkgs = mutableListOf<String>()
                for (j in 0 until pkgsArr.length()) pkgs.add(pkgsArr.getString(j))
                out.add(AppGroup(id, name, pkgs))
            }
            return out
        }
    }
}
