package com.lockedinlearning.launcher3.view

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.lockedinlearning.R

/**
 * M3 anchored popup menu (surfaceContainer color role, 16dp corner radius) — replaces the
 * centered AlertDialog long-press menus from the Compose version, matching AOSP Launcher3's
 * own PopupContainerWithArrow. Anchored next to the pointer instead of floating in the middle
 * of the screen.
 */
object PopupContainerWithArrow {

    fun show(anchor: View, items: List<Pair<String, () -> Unit>>) {
        val context = anchor.context
        val surfaceContainer = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainer, Color.DKGRAY)
        val onSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)

        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val card = MaterialCardView(context).apply {
            setCardBackgroundColor(surfaceContainer)
            radius = context.resources.getDimension(R.dimen.popup_corner_radius)
            cardElevation = 6f
            addView(
                column,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }

        val popup = PopupWindow(card, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = 8f
        }

        items.forEach { (label, action) ->
            val row = TextView(context).apply {
                text = label
                setTextColor(onSurface)
                setPadding(48, 32, 48, 32)
                setOnClickListener {
                    popup.dismiss()
                    action()
                }
            }
            column.addView(row)
        }

        popup.showAsDropDown(anchor, 0, -anchor.height / 2, Gravity.START)
    }
}
