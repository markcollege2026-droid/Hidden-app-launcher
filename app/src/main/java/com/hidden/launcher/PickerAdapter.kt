package com.hidden.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PickerAdapter(
    private val onCheckedChanged: (String, Boolean) -> Unit
) : RecyclerView.Adapter<PickerAdapter.ViewHolder>() {

    private var apps: List<AppInfo> = emptyList()
    private var selected: Set<String> = emptySet()

    fun submitList(newApps: List<AppInfo>, newSelected: Set<String>) {
        apps = newApps
        selected = newSelected.toSet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_picker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(apps[position], selected.contains(apps[position].packageName))
    }

    override fun getItemCount(): Int = apps.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        private val labelView: TextView = itemView.findViewById(R.id.appLabel)
        private val packageView: TextView = itemView.findViewById(R.id.appPackage)
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)

        fun bind(app: AppInfo, isSelected: Boolean) {
            labelView.text = app.label
            packageView.text = app.packageName
            if (app.icon != null) {
                iconView.setImageDrawable(app.icon)
            } else {
                iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            // Avoid firing listener during bind
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = isSelected
            checkBox.setOnCheckedChangeListener { _, checked ->
                onCheckedChanged(app.packageName, checked)
            }

            itemView.setOnClickListener {
                val newChecked = !checkBox.isChecked
                checkBox.isChecked = newChecked
                // Listener will fire
            }
        }
    }
}
