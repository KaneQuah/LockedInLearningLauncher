package com.lockedinlearning.launcher3.allapps

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Static, always-visible A-Z + '#' fast-scroll rail — ports DrawerScreen.kt's Compose
 * `AlphabetIndexBar` adaptive-sizing algorithm verbatim (each row gets an equal share of
 * whatever height this view is given, font scales with it) rather than the Pixel/AOSP
 * Launcher3-style thin draggable thumb. This matches the real vivo launcher's own drawer,
 * confirmed against an actual device this project targets, and keeps the overflow fix that
 * was needed when this was still a Compose composable.
 */
class AlphabetIndexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val alphabet: List<Char> = ('A'..'Z').toList() + '#'
    private var availableLetters: Set<Char> = emptySet()
    private var onLetterSelected: ((Char) -> Unit)? = null

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(217, 255, 255, 255) // 0.85 alpha
        textAlign = Paint.Align.CENTER
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(64, 255, 255, 255) // 0.25 alpha
        textAlign = Paint.Align.CENTER
    }

    fun setAvailableLetters(letters: Set<Char>) {
        availableLetters = letters
        invalidate()
    }

    fun setOnLetterSelectedListener(listener: (Char) -> Unit) {
        onLetterSelected = listener
    }

    /** Usable span excludes padding — callers add bottom padding for the system nav bar inset
     *  plus a visual buffer so the last row ('#') isn't flush against the screen edge. */
    private val usableHeight: Float get() = (height - paddingTop - paddingBottom).toFloat().coerceAtLeast(1f)
    private val rowHeight: Float get() = usableHeight / alphabet.size

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val fontSize = (rowHeight * 0.5f).coerceIn(dp(6f), dp(11f))
        activePaint.textSize = fontSize
        dimPaint.textSize = fontSize
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val fontSize = (rowHeight * 0.5f).coerceIn(dp(6f), dp(11f))
        activePaint.textSize = fontSize
        dimPaint.textSize = fontSize
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rh = rowHeight
        alphabet.forEachIndexed { index, letter ->
            val paint = if (letter in availableLetters) activePaint else dimPaint
            val baselineY = paddingTop + index * rh + rh / 2f - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(letter.toString(), width / 2f, baselineY, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                onLetterSelected?.invoke(letterAt(event.y))
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun letterAt(y: Float): Char {
        val fraction = ((y - paddingTop) / usableHeight).coerceIn(0f, 0.999f)
        return alphabet[(fraction * alphabet.size).toInt()]
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
