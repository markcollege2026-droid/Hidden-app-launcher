package com.hidden.launcher

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class MainAdapter(
    private val onAppClicked: (SelectedAppDisplay) -> Unit,
    private val onGroupClicked: (AppGroup, List<SelectedAppDisplay>, View) -> Unit,
    private val onGroupLongClicked: (AppGroup) -> Unit,
    private val onAppDroppedOnto: (dragged: MainItem, target: MainItem) -> Unit,
    private val onRemoveFromGroupDropped: (groupId: String, packageName: String, target: MainItem?) -> Unit = { _, _, _ -> }
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
        holder.itemView.setOnDragListener { v, event ->
            handleDragEvent(v, event, item)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun handleDragEvent(view: View, event: DragEvent, targetItem: MainItem): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
            }
            DragEvent.ACTION_DRAG_ENTERED -> {
                view.alpha = 0.5f
                view.setBackgroundColor(Color.parseColor("#3300BCD4"))
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
                    // Handle removal from group via drag-out (same interaction as creation)
                    if (draggedId.startsWith("remove:")) {
                        val parts = draggedId.split(":", limit = 3)
                        if (parts.size == 3) {
                            val gid = parts[1]
                            val pkg = parts[2]
                            // If dropped back onto same group, treat as place back (no removal)
                            val targetIsSameGroup = targetItem is MainItem.Group && targetItem.group.id == gid
                            if (!targetIsSameGroup) {
                                onRemoveFromGroupDropped(gid, pkg, targetItem)
                            } // else place back — do nothing
                        }
                    } else {
                        val draggedItem = items.find { it.identifier() == draggedId }
                        if (draggedItem != null && draggedItem.identifier() != targetItem.identifier()) {
                            onAppDroppedOnto(draggedItem, targetItem)
                        }
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
                v.alpha = 0.7f
                true
            }
        }
    }

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val labelView: TextView = itemView.findViewById(R.id.groupLabel)
        private val folderCard: MaterialCardView = itemView.findViewById(R.id.folderCard)
        private val previewGrid: GridLayout = itemView.findViewById(R.id.previewGrid)

        fun bind(item: MainItem.Group) {
            val group = item.group
            val members = item.memberDisplays
            labelView.text = group.name
            buildPreview(members)
            // Make entire grouped item tappable anywhere — folder icon, preview, label
            val openClick = View.OnClickListener {
                onGroupClicked(group, members, folderCard)
            }
            itemView.setOnClickListener(openClick)
            folderCard.setOnClickListener(openClick)
            previewGrid.setOnClickListener(openClick)
            labelView.setOnClickListener(openClick)

            itemView.setOnLongClickListener {
                onGroupLongClicked(group)
                true
            }
            // Also allow long-press on label to trigger options
            labelView.setOnLongClickListener {
                onGroupLongClicked(group)
                true
            }
            // Preview grid also supports long-press for options (if not dragging)
            previewGrid.setOnLongClickListener {
                onGroupLongClicked(group)
                true
            }
            folderCard.setOnLongClickListener {
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

        private fun buildPreview(members: List<SelectedAppDisplay>) {
            previewGrid.removeAllViews()
            val count = members.size
            val showCount = when {
                count <= 4 -> count
                count <= 9 -> count.coerceAtMost(9)
                else -> 9
            }
            val showMembers = members.take(showCount)
            val (cols, rows) = when (showCount) {
                2 -> 2 to 1
                3 -> 2 to 2
                4 -> 2 to 2
                5, 6 -> 3 to 2
                else -> 3 to 3
            }
            previewGrid.columnCount = cols
            previewGrid.rowCount = rows
            val iconSizeDp = when {
                showCount <= 4 -> 18
                showCount <= 6 -> 14
                else -> 12
            }
            val iconSizePx = dpToPx(iconSizeDp)
            val marginPx = dpToPx(2)
            for (i in showMembers.indices) {
                val iv = ImageView(itemView.context).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = iconSizePx
                        height = iconSizePx
                        setMargins(marginPx, marginPx, marginPx, marginPx)
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                val member = showMembers[i]
                if (member.icon != null) {
                    iv.setImageDrawable(member.icon)
                } else {
                    iv.setImageResource(android.R.drawable.sym_def_app_icon)
                }
                if (!member.isAvailable || !member.launchable) {
                    iv.alpha = 0.4f
                }
                previewGrid.addView(iv)
            }
            if (showCount == 3) {
                val placeholder = View(itemView.context).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = iconSizePx
                        height = iconSizePx
                        setMargins(marginPx, marginPx, marginPx, marginPx)
                    }
                    visibility = View.INVISIBLE
                }
                previewGrid.addView(placeholder)
            }
        }

        private fun dpToPx(dp: Int): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp.toFloat(),
                itemView.resources.displayMetrics
            ).toInt()
        }
    }
}
