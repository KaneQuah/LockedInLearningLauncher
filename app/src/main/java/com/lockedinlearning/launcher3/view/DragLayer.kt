package com.lockedinlearning.launcher3.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.lockedinlearning.launcher3.allapps.RevealGestureDetector
import com.lockedinlearning.launcher3.dragndrop.DragController

/**
 * The outermost view of the launcher — mirrors AOSP Launcher3's DragLayer. Owns touch
 * interception once a drag is in flight (so move/up events reach the drag machinery instead of
 * whichever child originally received the down event) and draws the floating drag shadow / the
 * dimmed "ghost slot" drop-preview on top of everything else.
 */
class DragLayer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var dragController: DragController? = null
    var revealGestureDetector: RevealGestureDetector? = null

    /** False while the Gate is covering the launcher — the reveal gesture / drag machinery must
     *  not steal touches meant for the Gate's own content (e.g. a flashcard's scrollable answer). */
    var isLauncherInteractive = true

    /** True while a folder's contents lightbox is showing on top of the DragLayer. The scrim/card
     *  is a plain child view with no touch interception of its own, so without this flag an upward
     *  swipe starting over the open folder is still claimed by [revealGestureDetector] and slides
     *  the All Apps drawer up underneath it. */
    var isFolderOpen = false

    private var dragBitmap: Bitmap? = null
    private var dragX = 0f
    private var dragY = 0f
    private var dragScale = 1f

    private var ghostRect: RectF? = null
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 100 }

    init {
        setWillNotDraw(false)
    }

    fun setDragVisual(bitmap: Bitmap?, rawX: Float, rawY: Float, scale: Float) {
        dragBitmap = bitmap
        dragX = rawX
        dragY = rawY
        dragScale = scale
        invalidate()
    }

    fun clearDragVisual() {
        dragBitmap = null
        invalidate()
    }

    fun setGhostSlot(rawLeft: Float, rawTop: Float, width: Float, height: Float) {
        val loc = IntArray(2).also { getLocationOnScreen(it) }
        ghostRect = RectF(rawLeft - loc[0], rawTop - loc[1], rawLeft - loc[0] + width, rawTop - loc[1] + height)
        invalidate()
    }

    fun clearGhostSlot() {
        ghostRect = null
        invalidate()
    }

    private var removeHintView: TextView? = null

    private fun ensureRemoveHintView(): TextView = removeHintView ?: TextView(context).apply {
        text = "Release to remove"
        // M3 reserves the error color role for destructive affordances like this one.
        setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnError))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        val padH = dp(16f)
        val padV = dp(8f)
        setPadding(padH, padV, padH, padV)
        background = GradientDrawable().apply {
            cornerRadius = 999f
            setColor(resolveThemeColor(com.google.android.material.R.attr.colorError))
        }
        visibility = View.GONE
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(32f)
        }
        addView(this, lp)
        removeHintView = this
    }

    private fun resolveThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    /** [showing] toggles visibility for the duration of a drag; [active] highlights it once the
     *  drag position is actually within the remove zone (vs. just being mid-drag elsewhere). */
    fun setRemoveHintActive(showing: Boolean, active: Boolean) {
        val hint = ensureRemoveHintView()
        hint.visibility = if (showing) View.VISIBLE else View.GONE
        hint.alpha = if (active) 1f else 0.55f
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isLauncherInteractive || isFolderOpen) return false
        if (dragController?.isDragging == true) return true
        return revealGestureDetector?.onInterceptTouchEvent(ev) == true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isLauncherInteractive || isFolderOpen) return false
        val controller = dragController
        if (controller?.isDragging == true) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> controller.onTouchMove(event.rawX, event.rawY)
                MotionEvent.ACTION_UP -> controller.onTouchUp()
                MotionEvent.ACTION_CANCEL -> controller.onTouchCancel()
            }
            return true
        }
        return revealGestureDetector?.onTouchEvent(event) == true
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        ghostRect?.let { canvas.drawRoundRect(it, 16f, 16f, ghostPaint) }
        dragBitmap?.let { bitmap ->
            val loc = IntArray(2).also { getLocationOnScreen(it) }
            val localX = dragX - loc[0]
            val localY = dragY - loc[1]
            val w = bitmap.width * dragScale
            val h = bitmap.height * dragScale
            val left = localX - w / 2f
            val top = localY - h / 2f
            canvas.save()
            canvas.translate(left, top)
            canvas.scale(dragScale, dragScale)
            canvas.drawBitmap(bitmap, 8f, 12f, shadowPaint) // offset copy = simple drop shadow
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            canvas.restore()
        }
    }
}
