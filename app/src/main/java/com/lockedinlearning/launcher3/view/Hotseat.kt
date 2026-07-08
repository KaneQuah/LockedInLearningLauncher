package com.lockedinlearning.launcher3.view

import android.content.Context
import android.util.AttributeSet
import com.lockedinlearning.launcher3.LauncherSettings

/**
 * The persistent bottom dock — a 1-row, [LauncherSettings.HOTSEAT_SIZE]-slot [CellLayout],
 * entirely separate from the Workspace grid (its own container in the Favorites schema).
 * Icons here are the same size as Workspace icons (real hotseat convention — no labels,
 * not a different icon size), just arranged in a single row.
 */
class Hotseat @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CellLayout(context, attrs) {
    init {
        columns = LauncherSettings.HOTSEAT_SIZE
        rows = 1
    }
}
