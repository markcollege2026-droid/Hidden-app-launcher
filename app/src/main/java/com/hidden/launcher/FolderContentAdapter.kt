package com.hidden.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for open folder view: scrollable grid with full-sized icons and labels
 * Supports 3x3, 4x4, 5x5 per page via GridLayoutManager span count
 */
class FolderContentAdapter(
    private val onAppClicked: (SelectedAppDisplay) -> Unit
) : RecyclerView.Adapter<FolderContentAdapter.ViewHolder>() {

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
        holder.bind(items[position])
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
            if (!item.isAvailable || !item.launchable) {
                itemView.alpha = 0.4f
                labelView.text = "${item.label}\n(${itemView.context.getString(R.string.unavailable)})"
            } else {
                itemView.alpha = 1.0f
            }
            itemView.setOnClickListener {
                onAppClicked(item)
            }
        }
    }
}
