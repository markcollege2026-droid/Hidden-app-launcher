package com.hidden.launcher

import android.content.ClipData
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for open folder view: scrollable grid with full-sized icons and labels
 * Long-press to remove uses same drag interaction as grouping — folder shrinks to icon on drag start
 */
class FolderContentAdapter(
    private val onAppClicked: (SelectedAppDisplay) -> Unit,
    private val onAppLongClicked: ((SelectedAppDisplay) -> Unit)? = null,
    private val onDragStarted: (() -> Unit)? = null
) : RecyclerView.Adapter<FolderContentAdapter.ViewHolder>() {

    private var items: List<SelectedAppDisplay> = emptyList()
    private var currentGroupId: String? = null

    fun submitList(newItems: List<SelectedAppDisplay>, groupId: String? = null) {
        items = newItems
        currentGroupId = groupId
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

            itemView.setOnLongClickListener { v ->
                val gid = currentGroupId
                if (gid != null) {
                    val clip = ClipData.newPlainText("group_member", "remove:$gid:${item.packageName}")
                    val shadow = View.DragShadowBuilder(v)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        v.startDragAndDrop(clip, shadow, null, 0)
                    } else {
                        @Suppress("DEPRECATION")
                        v.startDrag(clip, shadow, null, 0)
                    }
                    // Notify activity to shrink folder back to icon so home slots are visible
                    onDragStarted?.invoke()
                    true
                } else {
                    if (onAppLongClicked != null) {
                        onAppLongClicked.invoke(item)
                        true
                    } else false
                }
            }
        }
    }
}
