package com.hidden.launcher

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Main launcher screen.
 * Displays ONLY selected apps in a compact grid.
 * If no apps selected, shows empty state with button to settings.
 * Provides discreet settings access via toolbar menu and long-press.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefsHelper: PrefsHelper
    private lateinit var appRepository: AppRepository

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var emptyText: TextView
    private lateinit var emptyButton: Button
    private lateinit var toolbar: Toolbar

    private lateinit var adapter: MainAdapter

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

        adapter = MainAdapter(
            onAppClicked = { display -> launchApp(display) },
            onAppLongClicked = { openSettings() }
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

        // Also long-press on recycler background if empty space?
        recyclerView.setOnLongClickListener {
            openSettings()
            true
        }
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

        // Load selected apps info
        // Try to get all launchable for labels (cached off UI thread ideally)
        // For minimal app, do on UI thread but with caching - acceptable for <100 apps
        // To avoid ANR, we do simple background thread for larger lists
        Thread {
            val allAppsMap = try {
                appRepository.getLaunchableApps().associateBy { it.packageName }
            } catch (e: Exception) {
                emptyMap()
            }

            val displays = selected.map { pkg ->
                appRepository.getAppForPackage(pkg, allAppsMap)
            }.sortedBy { it.label.lowercase() }

            runOnUiThread {
                if (displays.isEmpty()) {
                    showEmptyState()
                } else {
                    showGrid(displays)
                }
            }
        }.start()
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
        emptyText.text = getString(R.string.empty_state_message)
    }

    private fun showGrid(displays: List<SelectedAppDisplay>) {
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        adapter.submitList(displays)
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
