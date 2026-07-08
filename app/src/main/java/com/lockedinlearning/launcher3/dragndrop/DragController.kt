package com.lockedinlearning.launcher3.dragndrop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.HapticFeedbackConstants
import android.view.View
import kotlin.math.hypot
import com.lockedinlearning.launcher3.LauncherSettings
import com.lockedinlearning.launcher3.model.FavoriteItemEntity
import com.lockedinlearning.launcher3.model.GridOccupancy
import com.lockedinlearning.launcher3.view.DragLayer
import com.lockedinlearning.launcher3.view.Hotseat
import com.lockedinlearning.launcher3.view.Workspace

/** The item currently being dragged — just enough geometry to resolve a drop target. */
data class DraggableItem(
    val itemId: Long,
    val spanX: Int,
    val spanY: Int
)

interface DragListener {
    fun onDesktopDrop(itemId: Long, cellX: Int, cellY: Int)
    fun onHotseatDrop(itemId: Long, slot: Int)
    fun onFolderFusion(anchorItemId: Long, draggedItemId: Long)
    fun onRemove(itemId: Long)
    /** A long-press fired (the icon lifted) but the finger never moved past tap slop before
     *  release — stock Android shows the icon's context menu (Remove/App info, or for a folder:
     *  Rename/Ungroup/Delete) in this case rather than treating it as a drag. */
    fun onLongPressWithoutDrag(itemId: Long, anchorView: View)
    /** A freshly-picked, not-yet-placed widget (see [DragController.startDragForNewWidget]) was
     *  dropped on a valid empty desktop cell. */
    fun onNewWidgetPlaced(widgetId: Int, spanX: Int, spanY: Int, cellX: Int, cellY: Int)
    /** The same widget was dropped somewhere invalid (remove zone, collision, hotseat) — there's
     *  nothing to snap back to since it was never placed, so the caller should just free it. */
    fun onNewWidgetCancelled(widgetId: Int)
    fun currentDesktopItems(): List<FavoriteItemEntity>
    fun currentHotseatItems(): List<FavoriteItemEntity>
}

/**
 * Mirrors AOSP Launcher3's DragController: on long-press lift, fires haptic feedback, scales
 * the dragged icon 1.1x with a drop shadow, and follows the finger continuously. On release,
 * resolves the drop target (Workspace cell / Hotseat slot / folder fusion) using the same
 * [GridOccupancy] collision math the ViewModel uses when placing items directly.
 *
 * The Hotseat's 5-slot cap falls out of this naturally rather than needing separate overflow
 * bookkeeping: [Hotseat] only has 5 physical slots, so a drop always resolves to one of those —
 * landing on an empty slot places it, landing on an occupied slot either fuses into a folder
 * (if the occupant is a plain app) or is rejected/snapped-back (anything else, e.g. a folder).
 */
class DragController(
    private val dragLayer: DragLayer,
    private val workspace: Workspace,
    private val hotseat: Hotseat,
    private val listener: DragListener
) {
    private var draggedView: View? = null
    private var draggedItem: DraggableItem? = null
    private var dragBitmap: Bitmap? = null
    private var touchX = 0f
    private var touchY = 0f
    private var startTouchX = 0f
    private var startTouchY = 0f

    // Set only for a picked-but-not-yet-placed widget (see startDragForNewWidget) — has no real
    // Favorites row, so it's tracked separately from draggedItem rather than needing a fake id.
    private var pendingNewWidgetId: Int? = null
    private var pendingSpanX = 1
    private var pendingSpanY = 1

    val isDragging: Boolean get() = draggedView != null

    fun startDrag(view: View, item: DraggableItem, rawX: Float, rawY: Float) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        draggedView = view
        draggedItem = item
        touchX = rawX
        touchY = rawY
        startTouchX = rawX
        startTouchY = rawY
        dragBitmap = captureBitmap(view)
        view.visibility = View.INVISIBLE
        dragLayer.setDragVisual(dragBitmap, touchX, touchY, GHOST_SCALE)
        dragLayer.setRemoveHintActive(showing = true, active = false)
        updateGhostSlot()
    }

    /** Starts a drag for a widget the user just picked from the "Add widget" flow — it isn't a
     *  placed child of any CellLayout and has no Favorites row yet, so it skips the tap-vs-drag
     *  slop check entirely (a hand-off from the picker is already mid-drag, never a tap) and
     *  resolves to [DragListener.onNewWidgetPlaced]/[DragListener.onNewWidgetCancelled] instead
     *  of the existing item callbacks. */
    fun startDragForNewWidget(view: View, widgetId: Int, spanX: Int, spanY: Int, rawX: Float, rawY: Float) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        draggedView = view
        pendingNewWidgetId = widgetId
        pendingSpanX = spanX
        pendingSpanY = spanY
        touchX = rawX
        touchY = rawY
        startTouchX = rawX
        startTouchY = rawY
        dragBitmap = captureBitmap(view)
        view.visibility = View.INVISIBLE
        dragLayer.setDragVisual(dragBitmap, touchX, touchY, GHOST_SCALE)
        dragLayer.setRemoveHintActive(showing = true, active = false)
        updateGhostSlot()
    }

    fun onTouchMove(rawX: Float, rawY: Float) {
        if (!isDragging) return
        touchX = rawX
        touchY = rawY
        dragLayer.setDragVisual(dragBitmap, touchX, touchY, GHOST_SCALE)
        updateGhostSlot()
        dragLayer.setRemoveHintActive(showing = true, active = isInRemoveZone(rawY))
    }

    fun onTouchUp() {
        val pendingWidgetId = pendingNewWidgetId
        if (pendingWidgetId != null) {
            // Hotseat/Fusion targets don't apply to a brand-new widget — anything but a clean
            // desktop cell just cancels it (there's no "original position" to snap back to).
            val target = resolveDropTarget(touchX, touchY, DraggableItem(NEW_ITEM_SENTINEL_ID, pendingSpanX, pendingSpanY))
            if (target is DropTarget.Desktop) {
                listener.onNewWidgetPlaced(pendingWidgetId, pendingSpanX, pendingSpanY, target.cellX, target.cellY)
            } else {
                listener.onNewWidgetCancelled(pendingWidgetId)
            }
            endDrag()
            return
        }
        val item = draggedItem
        val view = draggedView
        if (item != null && view != null) {
            val moved = hypot((touchX - startTouchX).toDouble(), (touchY - startTouchY).toDouble())
            if (moved < TAP_SLOP_PX) {
                // Restore the icon to normal (visible, no drag shadow) *before* handing off to the
                // context-menu callback, so the popup anchors against a settled icon rather than
                // one still mid-drag-visual.
                endDrag()
                listener.onLongPressWithoutDrag(item.itemId, view)
                return
            }
            when (val target = resolveDropTarget(touchX, touchY, item)) {
                is DropTarget.Desktop -> listener.onDesktopDrop(item.itemId, target.cellX, target.cellY)
                is DropTarget.HotseatSlot -> listener.onHotseatDrop(item.itemId, target.slot)
                is DropTarget.Fusion -> listener.onFolderFusion(target.anchorId, item.itemId)
                DropTarget.Remove -> listener.onRemove(item.itemId)
                DropTarget.Invalid -> Unit // snap back: state unchanged, next render restores the original position
            }
        }
        endDrag()
    }

    fun onTouchCancel() = endDrag()

    private fun endDrag() {
        draggedView?.visibility = View.VISIBLE
        dragLayer.clearDragVisual()
        dragLayer.clearGhostSlot()
        dragLayer.setRemoveHintActive(showing = false, active = false)
        draggedView = null
        draggedItem = null
        dragBitmap = null
        pendingNewWidgetId = null
    }

    private sealed class DropTarget {
        data class Desktop(val cellX: Int, val cellY: Int) : DropTarget()
        data class HotseatSlot(val slot: Int) : DropTarget()
        data class Fusion(val anchorId: Long) : DropTarget()
        data object Remove : DropTarget()
        data object Invalid : DropTarget()
    }

    /** Dragging an icon up off the grid (into/above the top status row) removes it — the same
     *  "drag to top to delete" affordance most launchers use, needing no extra chrome of its own. */
    private fun isInRemoveZone(rawY: Float): Boolean {
        val wsLoc = IntArray(2).also { workspace.getLocationOnScreen(it) }
        return rawY < wsLoc[1]
    }

    /** Where a dragged item's top-left cell should land so that its span is centered under the
     *  finger — matching [DragLayer]'s drag-shadow bitmap, which is already drawn centered on the
     *  touch point via [DragLayer.dispatchDraw]. Without this, [Workspace.cellForPosition] treats
     *  the touch point as the span's top-left corner instead, so a wide/tall widget's ghost-slot
     *  preview and actual drop cell end up offset down-and-right of where the visual shadow (and
     *  therefore the user's expectation) actually is — barely noticeable for a 1x1 icon, but very
     *  visible for a multi-cell widget. */
    private fun spanTopLeftCell(localX: Float, localY: Float, spanX: Int, spanY: Int): Pair<Int, Int> {
        val cellWidthPx = workspace.cellWidthPx.coerceAtLeast(1)
        val cellHeightPx = workspace.cellHeightPx.coerceAtLeast(1)
        val leftPx = localX - (spanX * cellWidthPx) / 2f
        val topPx = localY - (spanY * cellHeightPx) / 2f
        val cellX = Math.round(leftPx / cellWidthPx).coerceIn(0, (workspace.columns - spanX).coerceAtLeast(0))
        val cellY = Math.round(topPx / cellHeightPx).coerceIn(0, (workspace.rows - spanY).coerceAtLeast(0))
        return cellX to cellY
    }

    private fun resolveDropTarget(rawX: Float, rawY: Float, item: DraggableItem): DropTarget {
        if (isInRemoveZone(rawY)) return DropTarget.Remove

        val hotseatLoc = IntArray(2).also { hotseat.getLocationOnScreen(it) }
        if (rawY >= hotseatLoc[1] && rawY <= hotseatLoc[1] + hotseat.height) {
            val (slot, _) = hotseat.cellForPosition(rawX - hotseatLoc[0], 0f)
            val occupant = listener.currentHotseatItems().find { it.cellX == slot && it.id != item.itemId }
            return when {
                occupant == null -> DropTarget.HotseatSlot(slot)
                occupant.itemType == LauncherSettings.ITEM_TYPE_APPLICATION && item.spanX == 1 && item.spanY == 1 ->
                    DropTarget.Fusion(occupant.id)
                else -> DropTarget.Invalid
            }
        }

        val wsLoc = IntArray(2).also { workspace.getLocationOnScreen(it) }
        if (rawX >= wsLoc[0] && rawX <= wsLoc[0] + workspace.width &&
            rawY >= wsLoc[1] && rawY <= wsLoc[1] + workspace.height
        ) {
            val (cellX, cellY) = spanTopLeftCell(rawX - wsLoc[0], rawY - wsLoc[1], item.spanX, item.spanY)
            val desktopItems = listener.currentDesktopItems()
            val occupant = desktopItems.find { other ->
                other.id != item.itemId &&
                    cellX >= other.cellX && cellX < other.cellX + other.spanX &&
                    cellY >= other.cellY && cellY < other.cellY + other.spanY
            }
            return when {
                occupant == null ->
                    if (GridOccupancy.collides(cellX, cellY, item.spanX, item.spanY, desktopItems, ignoreId = item.itemId)) {
                        DropTarget.Invalid
                    } else {
                        DropTarget.Desktop(cellX, cellY)
                    }
                occupant.itemType == LauncherSettings.ITEM_TYPE_APPLICATION && item.spanX == 1 && item.spanY == 1 ->
                    DropTarget.Fusion(occupant.id)
                else -> DropTarget.Invalid
            }
        }

        return DropTarget.Invalid
    }

    private fun updateGhostSlot() {
        val (spanX, spanY) = draggedItem?.let { it.spanX to it.spanY }
            ?: pendingNewWidgetId?.let { pendingSpanX to pendingSpanY }
            ?: return
        val wsLoc = IntArray(2).also { workspace.getLocationOnScreen(it) }
        if (touchY < wsLoc[1] || touchY > wsLoc[1] + workspace.height) {
            dragLayer.clearGhostSlot()
            return
        }
        val (cellX, cellY) = spanTopLeftCell(touchX - wsLoc[0], touchY - wsLoc[1], spanX, spanY)
        val left = wsLoc[0] + cellX * workspace.cellWidthPx
        val top = wsLoc[1] + cellY * workspace.cellHeightPx
        dragLayer.setGhostSlot(
            left.toFloat(), top.toFloat(),
            (workspace.cellWidthPx * spanX).toFloat(),
            (workspace.cellHeightPx * spanY).toFloat()
        )
    }

    private fun captureBitmap(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    companion object {
        private const val GHOST_SCALE = 1.1f
        private const val TAP_SLOP_PX = 24f
        // Room autoGenerate ids start at 1, so this never collides with a real Favorites row —
        // used only to satisfy resolveDropTarget's "ignore self" collision checks for an item
        // that has no row of its own yet.
        private const val NEW_ITEM_SENTINEL_ID = -1L
    }
}
