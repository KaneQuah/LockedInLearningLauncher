package com.lockedinlearning.launcher3.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import com.lockedinlearning.launcher3.LauncherSettings
import com.lockedinlearning.launcher3.dragndrop.DragController
import com.lockedinlearning.launcher3.dragndrop.DraggableItem
import kotlin.math.hypot

/**
 * Mirrors AOSP Launcher3's `CellLayout` — a fixed grid ViewGroup where children are placed by
 * explicit (cellX, cellY, spanX, spanY) rather than flowing automatically. Generalized to
 * columns/rows independently (rather than assuming square NxN) so [Hotseat] can subclass it as
 * a 1-row strip while [Workspace] uses the full 5x5 square.
 */
open class CellLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    var columns: Int = LauncherSettings.GRID_SIZE
    var rows: Int = LauncherSettings.GRID_SIZE

    var cellWidthPx: Int = 0
        private set
    var cellHeightPx: Int = 0
        private set

    /** Set by HomeActivity once the shared DragController exists. Long-press detection lives
     *  here (at the container level) rather than on each child's own OnLongClickListener, since
     *  a widget's inflated RemoteViews content often has its own clickable children that would
     *  otherwise consume the touch before a per-child listener ever saw it. */
    var dragController: DragController? = null

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var pendingLongPress: Runnable? = null
    private var downRawX = 0f
    private var downRawY = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = ev.rawX
                downRawY = ev.rawY
                val (cellX, cellY) = cellForPosition(ev.x, ev.y)
                val child = findChildAt(cellX, cellY)
                if (child != null) {
                    val runnable = Runnable { startDragFor(child) }
                    pendingLongPress = runnable
                    longPressHandler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot((ev.rawX - downRawX).toDouble(), (ev.rawY - downRawY).toDouble()) > TOUCH_SLOP) {
                    cancelPendingLongPress()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelPendingLongPress()
        }
        // Never actually claim the gesture ourselves — DragLayer (our ancestor) already claims
        // every subsequent event once dragController.isDragging flips true from startDragFor().
        return false
    }

    private fun startDragFor(child: View) {
        pendingLongPress = null
        val lp = child.layoutParams as? LayoutParams ?: return
        val itemId = child.tag as? Long ?: return
        dragController?.startDrag(child, DraggableItem(itemId, lp.spanX, lp.spanY), downRawX, downRawY)
    }

    private fun cancelPendingLongPress() {
        pendingLongPress?.let { longPressHandler.removeCallbacks(it) }
        pendingLongPress = null
    }

    private fun findChildAt(cellX: Int, cellY: Int): View? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as? LayoutParams ?: continue
            if (cellX >= lp.cellX && cellX < lp.cellX + lp.spanX && cellY >= lp.cellY && cellY < lp.cellY + lp.spanY) {
                return child
            }
        }
        return null
    }

    class LayoutParams(
        var cellX: Int,
        var cellY: Int,
        var spanX: Int = 1,
        var spanY: Int = 1
    ) : ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        cellWidthPx = if (columns > 0) width / columns else width
        cellHeightPx = if (heightMode == MeasureSpec.EXACTLY && rows > 0) heightSize / rows else cellWidthPx

        val measuredHeight = if (heightMode == MeasureSpec.EXACTLY) heightSize else cellHeightPx * rows
        setMeasuredDimension(width, measuredHeight)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as? LayoutParams ?: continue
            val spanX = lp.spanX.coerceIn(1, columns.coerceAtLeast(1))
            val spanY = lp.spanY.coerceIn(1, rows.coerceAtLeast(1))
            child.measure(
                MeasureSpec.makeMeasureSpec(cellWidthPx * spanX, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cellHeightPx * spanY, MeasureSpec.EXACTLY)
            )
        }
    }

    /** Defensively clamps cellX/cellY/spanX/spanY within the grid regardless of how they were
     *  computed upstream — a child (especially a widget whose span is derived from its own
     *  minWidth/minHeight) must never be laid out past this ViewGroup's own bounds. */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as? LayoutParams ?: continue
            val spanX = lp.spanX.coerceIn(1, columns.coerceAtLeast(1))
            val spanY = lp.spanY.coerceIn(1, rows.coerceAtLeast(1))
            val cellX = lp.cellX.coerceIn(0, (columns - spanX).coerceAtLeast(0))
            val cellY = lp.cellY.coerceIn(0, (rows - spanY).coerceAtLeast(0))
            val left = cellX * cellWidthPx
            val top = cellY * cellHeightPx
            child.layout(left, top, left + cellWidthPx * spanX, top + cellHeightPx * spanY)
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams = LayoutParams(0, 0)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams = LayoutParams(0, 0)
    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(0, 0)
    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams

    /** Removes any existing child tagged with [tag] before adding — callers re-bind the whole grid on every state emission. */
    fun addViewToCell(view: View, cellX: Int, cellY: Int, spanX: Int = 1, spanY: Int = 1, tag: Any? = null) {
        view.tag = tag
        addView(view, LayoutParams(cellX, cellY, spanX, spanY))
    }

    /** Maps a raw touch position (in this view's local coordinates) to the cell under it — used by DragController in Phase D. */
    fun cellForPosition(x: Float, y: Float): Pair<Int, Int> {
        val cx = (x / cellWidthPx.coerceAtLeast(1)).toInt().coerceIn(0, (columns - 1).coerceAtLeast(0))
        val cy = (y / cellHeightPx.coerceAtLeast(1)).toInt().coerceIn(0, (rows - 1).coerceAtLeast(0))
        return cx to cy
    }

    companion object {
        private const val TOUCH_SLOP = 24f
    }
}
