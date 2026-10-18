package com.hidden.launcher

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Main launcher screen.
 * Displays ONLY selected apps in a compact grid, plus groups.
 * Supports drag-and-drop grouping: long-press an app and drag onto another to create a named group.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefsHelper: PrefsHelper
    private lateinit var appRepository: AppRepository

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var emptyText: TextView
    private lateinit var emptyButton: Button
    private lateinit var toolbar: Toolbar
    private lateinit var hintText: TextView

    private lateinit var adapter: MainAdapter

    // Cache of display items for drag handling
    private var currentItems: List<MainItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefsHelper = PrefsHelper(this)
        appRepository = AppRepository(packageManager)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        emptyText = findViewById(R.id.emptyText)
        emptyButton = findViewById(R.id.emptyButton)
        hintText = findViewById(R.id.hintText)

        adapter = MainAdapter(
            onAppClicked = { display -> launchApp(display) },
            onGroupClicked = { group, members -> showGroupContents(group, members) },
            onGroupLongClicked = { group -> showGroupOptions(group) },
            onAppDroppedOnto = { dragged, target -> handleDrop(dragged, target) }
        )

        recyclerView.layoutManager = GridLayoutManager(this, calculateSpanCount())
        recyclerView.adapter = adapter

        emptyButton.setOnClickListener { openSettings() }

        // Long-press on empty area or background to open settings (discreet)
        findViewById<View>(R.id.rootView).setOnLongClickListener {
            openSettings()
            true
        }
        emptyView.setOnLongClickListener {
            openSettings()
            true
        }

        // Recycler background long-press still opens settings if not dragging
        recyclerView.setOnLongClickListener {
            // Only if no items, otherwise drag handles it
            if (currentItems.isEmpty()) {
                openSettings()
                true
            } else false
        }

        // Update hint to include drag tip
        hintText.text = getString(R.string.hint_long_press_settings) + " • " + getString(R.string.drag_to_group_hint)
    }

    override fun onResume() {
        super.onResume()
        refreshApps()
    }

    private fun calculateSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        return when {
            screenWidthDp >= 600 -> 5
            screenWidthDp >= 400 -> 4
            else -> 3
        }
    }

    private fun refreshApps() {
        val selected = prefsHelper.getSelectedPackages()

        if (selected.isEmpty()) {
            showEmptyState()
            return
        }

        Thread {
            val allAppsMap = try {
                appRepository.getLaunchableApps().associateBy { it.packageName }
            } catch (e: Exception) {
                emptyMap()
            }

            // Prune groups that reference now-deselected apps
            val groups = prefsHelper.pruneGroupsOnSelectionChange(selected)

            val displays = selected.map { pkg ->
                appRepository.getAppForPackage(pkg, allAppsMap)
            }

            // Build MainItems: groups + remaining individuals
            val mainItems = PrefsHelper.buildDisplayItems(displays, groups)

            runOnUiThread {
                if (mainItems.isEmpty()) {
                    showEmptyState()
                } else {
                    showGrid(mainItems)
                }
            }
        }.start()
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
        emptyText.text = getString(R.string.empty_state_message)
    }

    private fun showGrid(items: List<MainItem>) {
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        currentItems = items
        adapter.submitList(items)
    }

    private fun launchApp(display: SelectedAppDisplay) {
        if (!display.isAvailable || !display.launchable) {
            val msg = when {
                !display.isAvailable -> getString(R.string.app_unavailable, display.packageName)
                else -> getString(R.string.app_not_launchable, display.label)
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = appRepository.getLaunchIntent(display.packageName)
        if (intent == null) {
            Toast.makeText(this, getString(R.string.app_no_intent, display.label), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.launch_security_error), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.launch_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleDrop(dragged: MainItem, target: MainItem) {
        when {
            dragged is MainItem.App && target is MainItem.App -> {
                // Create new group with both apps
                createGroupFromTwoApps(dragged, target)
            }
            dragged is MainItem.App && target is MainItem.Group -> {
                // Add dragged app to target group
                if (dragged.display.packageName !in target.group.packageNames) {
                    prefsHelper.addToGroup(target.group.id, dragged.display.packageName)
                    Toast.makeText(this, getString(R.string.added_to_group, target.group.name), Toast.LENGTH_SHORT).show()
                    refreshApps()
                } else {
                    Toast.makeText(this, "Already in group", Toast.LENGTH_SHORT).show()
                }
            }
            dragged is MainItem.Group && target is MainItem.App -> {
                // Add target app to dragged group (group onto app)
                if (target.display.packageName !in dragged.group.packageNames) {
                    prefsHelper.addToGroup(dragged.group.id, target.display.packageName)
                    Toast.makeText(this, getString(R.string.added_to_group, dragged.group.name), Toast.LENGTH_SHORT).show()
                    refreshApps()
                }
            }
            dragged is MainItem.Group && target is MainItem.Group -> {
                // Merge groups: dragged into target
                if (dragged.group.id != target.group.id) {
                    prefsHelper.mergeGroups(target.group.id, dragged.group.id)
                    Toast.makeText(this, getString(R.string.group_merged, target.group.name), Toast.LENGTH_SHORT).show()
                    refreshApps()
                }
            }
        }
    }

    private fun createGroupFromTwoApps(a: MainItem.App, b: MainItem.App) {
        val members = listOf(a.display, b.display)
        val defaultName = PrefsHelper.generateGroupName(members, prefsHelper.getGroups().size + 1)
        showCreateGroupDialog(defaultName, members.map { it.packageName })
    }

    private fun showCreateGroupDialog(defaultName: String, packageNames: List<String>) {
        val edit = EditText(this).apply {
            hint = getString(R.string.group_hint)
            setText(defaultName)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.create_group_title))
            .setMessage(getString(R.string.enter_group_name))
            .setView(edit)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val name = edit.text.toString().trim().ifBlank { defaultName }
                prefsHelper.createGroup(name, packageNames)
                Toast.makeText(this, getString(R.string.group_created, name, packageNames.size), Toast.LENGTH_SHORT).show()
                refreshApps()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showGroupContents(group: AppGroup, members: List<SelectedAppDisplay>) {
        // Build dialog listing member apps to launch
        val labels = members.map { it.label + if (!it.isAvailable || !it.launchable) " (${getString(R.string.unavailable)})" else "" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.group_contents_title, group.name, members.size))
            .setItems(labels) { _, which ->
                val selected = members[which]
                launchApp(selected)
            }
            .setPositiveButton(getString(R.string.rename)) { _, _ -> showRenameDialog(group) }
            .setNeutralButton(getString(R.string.ungroup)) { _, _ ->
                prefsHelper.deleteGroup(group.id)
                Toast.makeText(this, getString(R.string.ungrouped), Toast.LENGTH_SHORT).show()
                refreshApps()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showGroupOptions(group: AppGroup) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.group_options_title, group.name))
            .setMessage(getString(R.string.group_options_message))
            .setItems(arrayOf(getString(R.string.rename), getString(R.string.delete), getString(R.string.ungroup))) { _, which ->
                when (which) {
                    0 -> showRenameDialog(group)
                    1, 2 -> {
                        prefsHelper.deleteGroup(group.id)
                        Toast.makeText(this, getString(R.string.group_deleted), Toast.LENGTH_SHORT).show()
                        refreshApps()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showRenameDialog(group: AppGroup) {
        val edit = EditText(this).apply {
            hint = getString(R.string.new_name_hint)
            setText(group.name)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rename_group_title))
            .setView(edit)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val newName = edit.text.toString().trim()
                if (newName.isNotBlank() && newName != group.name) {
                    prefsHelper.renameGroup(group.id, newName)
                    Toast.makeText(this, getString(R.string.group_renamed, newName), Toast.LENGTH_SHORT).show()
                    refreshApps()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openSettings() {
        val intent = Intent(this, AppPickerActivity::class.java)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                openSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
