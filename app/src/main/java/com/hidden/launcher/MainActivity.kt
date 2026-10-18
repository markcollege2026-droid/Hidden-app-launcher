package com.hidden.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewAnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.hypot

/**
 * Main launcher screen.
 * Displays ONLY selected apps in a compact grid, plus groups.
 * Supports drag-and-drop grouping: long-press an app and drag onto another to create a named group.
 * Groups show closed folder with mini-preview, tap expands via animated overlay (dimmed/blurred background)
 * to scrollable grid (3x3/4x4) on customizable panel.
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

    // Folder overlay views
    private lateinit var folderOverlay: View
    private lateinit var folderDim: View
    private lateinit var folderCardPanel: View
    private lateinit var folderTitle: TextView
    private lateinit var folderClose: View
    private lateinit var folderRecycler: RecyclerView
    private lateinit var folderContentAdapter: FolderContentAdapter

    private var currentItems: List<MainItem> = emptyList()
    private var currentOpenGroup: AppGroup? = null
    private var anchorViewForAnimation: View? = null

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

        folderOverlay = findViewById(R.id.folderOverlay)
        folderDim = findViewById(R.id.folderDim)
        folderCardPanel = findViewById(R.id.folderCardPanel)
        folderTitle = findViewById(R.id.folderTitle)
        folderClose = findViewById(R.id.folderClose)
        folderRecycler = findViewById(R.id.folderRecycler)

        folderContentAdapter = FolderContentAdapter { display ->
            launchApp(display)
        }
        folderRecycler.layoutManager = GridLayoutManager(this, calculateFolderSpanCount())
        folderRecycler.adapter = folderContentAdapter

        folderDim.setOnClickListener { closeFolderOverlay() }
        folderClose.setOnClickListener { closeFolderOverlay() }
        folderCardPanel.setOnClickListener { /* consume */ }

        adapter = MainAdapter(
            onAppClicked = { display -> launchApp(display) },
            onGroupClicked = { group, members, anchor -> openFolderOverlay(group, members, anchor) },
            onGroupLongClicked = { group -> showGroupOptions(group) },
            onAppDroppedOnto = { dragged, target -> handleDrop(dragged, target) }
        )

        recyclerView.layoutManager = GridLayoutManager(this, calculateSpanCount())
        recyclerView.adapter = adapter

        emptyButton.setOnClickListener { openSettings() }

        findViewById<View>(R.id.rootView).setOnLongClickListener {
            if (folderOverlay.visibility == View.VISIBLE) {
                closeFolderOverlay()
                true
            } else {
                openSettings()
                true
            }
        }
        emptyView.setOnLongClickListener {
            openSettings()
            true
        }

        recyclerView.setOnLongClickListener {
            if (currentItems.isEmpty()) {
                openSettings()
                true
            } else false
        }

        hintText.text = getString(R.string.hint_long_press_settings) + " • " + getString(R.string.drag_to_group_hint)
    }

    override fun onResume() {
        super.onResume()
        refreshApps()
    }

    override fun onBackPressed() {
        if (folderOverlay.visibility == View.VISIBLE) {
            closeFolderOverlay()
            return
        }
        super.onBackPressed()
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

    private fun calculateFolderSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        return when {
            screenWidthDp >= 600 -> 4
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

            val groups = prefsHelper.pruneGroupsOnSelectionChange(selected)

            val displays = selected.map { pkg ->
                appRepository.getAppForPackage(pkg, allAppsMap)
            }

            val mainItems = PrefsHelper.buildDisplayItems(displays, groups)

            runOnUiThread {
                if (mainItems.isEmpty()) {
                    showEmptyState()
                } else {
                    showGrid(mainItems)
                }
                currentOpenGroup?.let { openGroup ->
                    val updatedGroup = groups.find { it.id == openGroup.id }
                    if (updatedGroup != null) {
                        val memberDisplays = updatedGroup.packageNames.mapNotNull { pkg -> displays.find { it.packageName == pkg } }
                        folderTitle.text = getString(R.string.group_contents_title, updatedGroup.name, memberDisplays.size)
                        folderContentAdapter.submitList(memberDisplays)
                    } else {
                        closeFolderOverlay()
                    }
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

    // ---- Folder overlay (open folder view) ----

    private fun openFolderOverlay(group: AppGroup, members: List<SelectedAppDisplay>, anchor: View) {
        currentOpenGroup = group
        anchorViewForAnimation = anchor
        folderTitle.text = getString(R.string.group_contents_title, group.name, members.size)
        folderContentAdapter.submitList(members)

        folderOverlay.visibility = View.VISIBLE
        folderOverlay.alpha = 0f
        folderCardPanel.alpha = 0f
        folderCardPanel.scaleX = 0.85f
        folderCardPanel.scaleY = 0.85f
        folderOverlay.alpha = 1f

        folderOverlay.post {
            val overlayLoc = IntArray(2)
            folderOverlay.getLocationOnScreen(overlayLoc)
            val anchorLoc = IntArray(2)
            anchor.getLocationOnScreen(anchorLoc)
            val anchorCenterX = anchorLoc[0] + anchor.width / 2 - overlayLoc[0]
            val anchorCenterY = anchorLoc[1] + anchor.height / 2 - overlayLoc[1]

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val finalRadius = hypot(folderOverlay.width.toDouble(), folderOverlay.height.toDouble()).toFloat()
                val anim = ViewAnimationUtils.createCircularReveal(
                    folderOverlay,
                    anchorCenterX, anchorCenterY,
                    0f, finalRadius
                )
                anim.duration = 320
                anim.start()
            } else {
                folderOverlay.animate().alpha(1f).setDuration(200).start()
            }

            folderCardPanel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280)
                .start()
        }
    }

    private fun closeFolderOverlay() {
        if (folderOverlay.visibility != View.VISIBLE) return
        val anchor = anchorViewForAnimation
        if (anchor != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val overlayLoc = IntArray(2)
            folderOverlay.getLocationOnScreen(overlayLoc)
            val anchorLoc = IntArray(2)
            anchor.getLocationOnScreen(anchorLoc)
            val anchorCenterX = anchorLoc[0] + anchor.width / 2 - overlayLoc[0]
            val anchorCenterY = anchorLoc[1] + anchor.height / 2 - overlayLoc[1]
            val startRadius = hypot(folderOverlay.width.toDouble(), folderOverlay.height.toDouble()).toFloat()
            val anim = ViewAnimationUtils.createCircularReveal(
                folderOverlay,
                anchorCenterX, anchorCenterY,
                startRadius, 0f
            )
            anim.duration = 260
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    folderOverlay.visibility = View.GONE
                    currentOpenGroup = null
                    anchorViewForAnimation = null
                }
            })
            anim.start()
            folderCardPanel.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(220).start()
        } else {
            folderOverlay.animate().alpha(0f).setDuration(180).withEndAction {
                folderOverlay.visibility = View.GONE
                currentOpenGroup = null
                anchorViewForAnimation = null
            }.start()
        }
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
                createGroupFromTwoApps(dragged, target)
            }
            dragged is MainItem.App && target is MainItem.Group -> {
                if (dragged.display.packageName !in target.group.packageNames) {
                    prefsHelper.addToGroup(target.group.id, dragged.display.packageName)
                    Toast.makeText(this, getString(R.string.added_to_group, target.group.name), Toast.LENGTH_SHORT).show()
                    refreshApps()
                } else {
                    Toast.makeText(this, "Already in group", Toast.LENGTH_SHORT).show()
                }
            }
            dragged is MainItem.Group && target is MainItem.App -> {
                if (target.display.packageName !in dragged.group.packageNames) {
                    prefsHelper.addToGroup(dragged.group.id, target.display.packageName)
                    Toast.makeText(this, getString(R.string.added_to_group, dragged.group.name), Toast.LENGTH_SHORT).show()
                    refreshApps()
                }
            }
            dragged is MainItem.Group && target is MainItem.Group -> {
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

    private fun showGroupOptions(group: AppGroup) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.group_options_title, group.name))
            .setMessage(getString(R.string.group_options_message))
            .setItems(arrayOf(getString(R.string.rename), getString(R.string.delete), getString(R.string.ungroup))) { _, which ->
                when (which) {
                    0 -> showRenameDialog(group)
                    1, 2 -> {
                        prefsHelper.deleteGroup(group.id)
                        if (currentOpenGroup?.id == group.id) closeFolderOverlay()
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
