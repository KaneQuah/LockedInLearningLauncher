package com.lockedinlearning.launcher3.model

import com.lockedinlearning.launcher3.LauncherSettings

/**
 * Pure occupancy-grid math shared by [com.lockedinlearning.ui.home.HomeViewModel] (placement on
 * add) and `launcher3.view.CellLayout`/`launcher3.dragndrop.DragController` (collision detection
 * during drag) — mirrors AOSP `CellLayout`'s `mOccupied` boolean-grid approach, just computed
 * on-demand from the current item list instead of maintained as mutable View state.
 */
object GridOccupancy {

    /** True if a [spanX]x[spanY] item at ([cellX],[cellY]) would overlap any item in [existing] or run off the grid. */
    fun collides(
        cellX: Int, cellY: Int, spanX: Int, spanY: Int,
        existing: List<FavoriteItemEntity>,
        gridSize: Int = LauncherSettings.GRID_SIZE,
        ignoreId: Long? = null
    ): Boolean {
        if (cellX < 0 || cellY < 0 || cellX + spanX > gridSize || cellY + spanY > gridSize) return true
        return existing.any { other ->
            if (other.id == ignoreId) return@any false
            val xOverlap = cellX < other.cellX + other.spanX && other.cellX < cellX + spanX
            val yOverlap = cellY < other.cellY + other.spanY && other.cellY < cellY + spanY
            xOverlap && yOverlap
        }
    }

    /** First free top-left cell (row-major scan) that fits a [spanX]x[spanY] item, or null if the grid is full. */
    fun findEmptyCell(
        existing: List<FavoriteItemEntity>,
        spanX: Int = 1, spanY: Int = 1,
        gridSize: Int = LauncherSettings.GRID_SIZE
    ): Pair<Int, Int>? {
        for (y in 0 until gridSize) {
            for (x in 0 until gridSize) {
                if (!collides(x, y, spanX, spanY, existing, gridSize)) return x to y
            }
        }
        return null
    }
}
