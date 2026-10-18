package com.hidden.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MainAdapter(
    private val onAppClicked: (SelectedAppDisplay) -> Unit,
    private val onAppLongClicked: () -> Unit
) : RecyclerView.Adapter<MainAdapter.ViewHolder>() {

    private var items: List<SelectedAppDisplay> = emptyList()

    fun submitList(newItems: List<SelectedAppDisplay>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        private val labelView: TextView = itemView.findViewById(R.id.appLabel)

        fun bind(item: SelectedAppDisplay) {
            labelView.text = item.label
            if (item.icon != null) {
                iconView.setImageDrawable(item.icon)
            } else {
                iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            // Grey out unavailable
            if (!item.isAvailable || !item.launchable) {
                itemView.alpha = 0.4f
                labelView.text = if (!item.isAvailable) {
                    "${item.label}\n(${itemView.context.getString(R.string.unavailable)})"
                } else item.label
            } else {
                itemView.alpha = 1.0f
            }

            itemView.setOnClickListener {
                onAppClicked(item)
            }
            itemView.setOnLongClickListener {
                onAppLongClicked()
                true
            }
        }
    }
}
