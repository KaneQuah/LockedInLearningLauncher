package com.lockedinlearning.launcher3.allapps

import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lockedinlearning.launcher3.model.AppInfo
import com.lockedinlearning.launcher3.view.IconView
import com.lockedinlearning.ui.home.IconAppearance

class AppsAdapter(
    private val packageManager: PackageManager,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppsAdapter.ViewHolder>() {

    var appearance: IconAppearance = IconAppearance()

    private var items: List<AppInfo> = emptyList()

    fun submitList(newItems: List<AppInfo>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos].packageName == newItems[newPos].packageName
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = items[oldPos] == newItems[newPos]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    fun indexOfFirstWithLetter(letter: Char): Int = items.indexOfFirst {
        val first = it.label.firstOrNull()?.uppercaseChar()
        (first?.takeIf { c -> c in 'A'..'Z' } ?: '#') == letter
    }

    inner class ViewHolder(val iconView: IconView) : RecyclerView.ViewHolder(iconView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(IconView(parent.context))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = items[position]
        val icon = runCatching { packageManager.getApplicationIcon(app.packageName) }.getOrNull()
        holder.iconView.bind(
            label = app.label,
            icon = icon,
            appearance = appearance
        )
        holder.iconView.setOnClickListener { onClick(app) }
        holder.iconView.setOnLongClickListener { onLongClick(app); true }
    }

    override fun getItemCount(): Int = items.size
}
