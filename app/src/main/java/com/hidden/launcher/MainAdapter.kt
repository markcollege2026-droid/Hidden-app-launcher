package com.hidden.launcher

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.os.Build
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MainAdapter(
    private val onAppClicked: (SelectedAppDisplay) -> Unit,
    private val onGroupClicked: (AppGroup, List<SelectedAppDisplay>) -> Unit,
    private val onGroupLongClicked: (AppGroup) -> Unit,
    private val onAppDroppedOnto: (dragged: MainItem, target: MainItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<MainItem> = emptyList()

    companion object {
        private const val TYPE_APP = 0
        private const val TYPE_GROUP = 1
    }

    fun submitList(newItems: List<MainItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getItemAt(position: Int): MainItem? = items.getOrNull(position)

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is MainItem.App -> TYPE_APP
            is MainItem.Group -> TYPE_GROUP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_GROUP -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group, parent, false)
                GroupViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
                AppViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is AppViewHolder -> holder.bind(item as MainItem.App)
            is GroupViewHolder -> holder.bind(item as MainItem.Group)
        }
        // Setup drag listener for drop target
        holder.itemView.setOnDragListener { v, event ->
            handleDragEvent(v, event, item)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun handleDragEvent(view: View, event: DragEvent, targetItem: MainItem): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                // Accept only plain text clip
                return event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
            }
            DragEvent.ACTION_DRAG_ENTERED -> {
                view.alpha = 0.5f
                view.setBackgroundColor(Color.parseColor("#3300BCD4")) // light cyan highlight
                return true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                view.alpha = 1.0f
                view.setBackgroundColor(Color.TRANSPARENT)
                return true
            }
            DragEvent.ACTION_DROP -> {
                view.alpha = 1.0f
                view.setBackgroundColor(Color.TRANSPARENT)
                val clipData = event.clipData
                if (clipData != null && clipData.itemCount > 0) {
                    val draggedId = clipData.getItemAt(0).text.toString()
                    // Find dragged item by identifier
                    val draggedItem = items.find { it.identifier() == draggedId }
                    if (draggedItem != null && draggedItem.identifier() != targetItem.identifier()) {
                        onAppDroppedOnto(draggedItem, targetItem)
                    }
                }
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                view.alpha = 1.0f
                view.setBackgroundColor(Color.TRANSPARENT)
                return true
            }
        }
        return false
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        private val labelView: TextView = itemView.findViewById(R.id.appLabel)

        fun bind(item: MainItem.App) {
            val app = item.display
            labelView.text = app.label
            if (app.icon != null) {
                iconView.setImageDrawable(app.icon)
            } else {
                iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            if (!app.isAvailable || !app.launchable) {
                itemView.alpha = 0.4f
                labelView.text = if (!app.isAvailable) {
                    "${app.label}\n(${itemView.context.getString(R.string.unavailable)})"
                } else app.label
            } else {
                itemView.alpha = 1.0f
            }

            itemView.setOnClickListener {
                onAppClicked(app)
            }

            // Long-press starts drag (to create group)
            itemView.setOnLongClickListener { v ->
                val identifier = item.identifier()
                val clip = ClipData.newPlainText("app", identifier)
                val shadow = View.DragShadowBuilder(v)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    v.startDragAndDrop(clip, shadow, null, 0)
                } else {
                    @Suppress("DEPRECATION")
                    v.startDrag(clip, shadow, null, 0)
                }
                // Visual feedback: elevate
                v.alpha = 0.7f
                true
            }
        }
    }

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.groupIcon)
        private val labelView: TextView = itemView.findViewById(R.id.groupLabel)
        private val countView: TextView = itemView.findViewById(R.id.groupCount)

        fun bind(item: MainItem.Group) {
            val group = item.group
            val members = item.memberDisplays
            labelView.text = group.name
            countView.text = "${members.size} apps"
            // Use folder icon - if custom drawable exists use it else fallback
            try {
                iconView.setImageResource(R.drawable.ic_folder)
            } catch (e: Exception) {
                iconView.setImageResource(android.R.drawable.ic_menu_agenda)
            }

            // Show member icons as overlay? For simplicity just folder

            itemView.alpha = 1.0f
            itemView.setOnClickListener {
                onGroupClicked(group, members)
            }

            itemView.setOnLongClickListener { v ->
                // Two options: drag group, or show group options.
                // For drag, start drag with group identifier
                // But also allow long-press menu - we handle via callback for rename/delete
                // To distinguish, we start drag immediately; user can also access menu via separate UI
                // For now drag takes precedence; menu accessible via long-press on group still triggers drag
                // Alternative: show options dialog if drag not intended. We'll implement drag + also options on long-press after drag.
                // To allow both, we use long-press to show options menu, but drag requires explicit drag handle?
                // For spec: long-press + drag creates group -> that is for apps. For groups, long-press should show edit options.
                // So for group items, long-press shows options instead of drag.
                onGroupLongClicked(group)
                true
                // If we want group dragging, we could add drag handle. For now groups not draggable, but we support if needed:
                // To enable drag for groups, uncomment below and remove callback above
                /*
                val identifier = item.identifier()
                val clip = ClipData.newPlainText("group", identifier)
                val shadow = View.DragShadowBuilder(v)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    v.startDragAndDrop(clip, shadow, null, 0)
                } else {
                    @Suppress("DEPRECATION")
                    v.startDrag(clip, shadow, null, 0)
                }
                true
                */
            }
            // Additionally allow group to be drop target (already via outer listener) and also enable group dragging via separate handle if needed
            // For now support dragging group by attaching drag listener to icon long-press separately
            // Enable icon long-press for dragging group
            iconView.setOnLongClickListener { v ->
                val identifier = item.identifier()
                val clip = ClipData.newPlainText("group", identifier)
                val shadow = View.DragShadowBuilder(itemView)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    itemView.startDragAndDrop(clip, shadow, null, 0)
                } else {
                    @Suppress("DEPRECATION")
                    itemView.startDrag(clip, shadow, null, 0)
                }
                true
            }
        }
    }
}
