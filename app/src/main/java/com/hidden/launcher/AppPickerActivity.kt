package com.hidden.launcher

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Settings / app selection screen.
 * Lists all launchable installed apps with checkboxes.
 * Allows selecting/deselecting and persists via SharedPreferences.
 */
class AppPickerActivity : AppCompatActivity() {

    private lateinit var prefsHelper: PrefsHelper
    private lateinit var appRepository: AppRepository

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEdit: EditText
    private lateinit var saveButton: Button
    private lateinit var countText: TextView
    private lateinit var toolbar: Toolbar

    private lateinit var adapter: PickerAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()
    private var selectedSet: MutableSet<String> = mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_picker)

        prefsHelper = PrefsHelper(this)
        appRepository = AppRepository(packageManager)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        recyclerView = findViewById(R.id.recyclerView)
        searchEdit = findViewById(R.id.searchEdit)
        saveButton = findViewById(R.id.saveButton)
        countText = findViewById(R.id.countText)

        selectedSet = prefsHelper.getSelectedPackages().toMutableSet()

        adapter = PickerAdapter(
            onCheckedChanged = { pkg, isChecked ->
                if (isChecked) selectedSet.add(pkg) else selectedSet.remove(pkg)
                updateCount()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        saveButton.setOnClickListener { saveAndFinish() }

        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Long-press hint: toolbar long press not needed
        loadApps()
        updateCount()
    }

    private fun loadApps() {
        // Show loading state
        countText.text = getString(R.string.loading)

        Thread {
            val apps = try {
                appRepository.getLaunchableApps()
            } catch (e: Exception) {
                emptyList()
            }

            runOnUiThread {
                allApps = apps
                filteredApps = apps
                adapter.submitList(filteredApps, selectedSet)
                updateCount()
                if (apps.isEmpty()) {
                    Toast.makeText(this, getString(R.string.no_apps_found), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun filterApps(query: String) {
        filteredApps = if (query.isBlank()) {
            allApps
        } else {
            val q = query.lowercase().trim()
            allApps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
        adapter.submitList(filteredApps, selectedSet)
        updateCount()
    }

    private fun updateCount() {
        val total = allApps.size
        val filtered = filteredApps.size
        val selected = selectedSet.size
        countText.text = getString(R.string.selection_count, selected, total, filtered)
    }

    private fun saveAndFinish() {
        prefsHelper.setSelectedPackages(selectedSet)
        Toast.makeText(this, getString(R.string.selection_saved, selectedSet.size), Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_picker, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_save -> {
                saveAndFinish()
                true
            }
            R.id.action_clear -> {
                selectedSet.clear()
                adapter.submitList(filteredApps, selectedSet)
                updateCount()
                true
            }
            R.id.action_select_all_filtered -> {
                // Select all currently filtered
                filteredApps.forEach { selectedSet.add(it.packageName) }
                adapter.submitList(filteredApps, selectedSet)
                updateCount()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
