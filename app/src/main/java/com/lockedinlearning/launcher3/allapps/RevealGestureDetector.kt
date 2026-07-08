package com.lockedinlearning.launcher3.allapps

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import kotlin.math.abs

/**
 * Continuous, velocity-following swipe-up-from-Home reveal for the All Apps drawer — mirrors
 * AOSP Launcher3's AllAppsTransitionController. [revealTarget]'s translationY tracks the finger
 * directly (0 = fully open, [revealTarget]'s own height = fully closed/off-screen below) rather
 * than snapping via a binary visibility toggle; release settles to whichever state the gesture's
 * final position + fling velocity implies.
 *
 * DragLayer calls [onInterceptTouchEvent] and [onTouchEvent] from its own identically-named
 * overrides — but Android only re-invokes a ViewGroup's onInterceptTouchEvent while a *child* is
 * still being considered for the gesture. Over empty Workspace/Hotseat space there is no such
 * child, so DragLayer's own onTouchEvent ends up claiming the initial DOWN directly, and every
 * subsequent MOVE is delivered straight to onTouchEvent — onInterceptTouchEvent is never called
 * again for the rest of that gesture. So the touch-slop/"has this become a drag yet" detection
 * must live in a path both entry points share, not solely in onInterceptTouchEvent.
 */
class RevealGestureDetector(
    private val revealTarget: View,
    private val onOpenStateChanged: (Boolean) -> Unit,
    /** True when the drawer's own scrollable content (the apps grid) has nothing left to scroll
     *  upward into — i.e. a further downward drag should close the drawer rather than being
     *  consumed as list scrolling. Defaults to true for reveal targets with no inner scroller. */
    private val isContentScrolledToTop: () -> Boolean = { true }
) {
    private var startY = 0f
    private var startTranslation = 0f
    private var dragging = false
    private var velocityTracker: VelocityTracker? = null
    private var isOpen = false

    private val targetHeight: Float get() = revealTarget.height.takeIf { it > 0 }?.toFloat() ?: 1f

    fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        handle(event)
        return dragging
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            handle(event)
            return true // claim the stream even before slop is crossed, or MOVE/UP never arrive
        }
        handle(event)
        return dragging
    }

    private fun handle(event: MotionEvent) {
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.rawY
                startTranslation = revealTarget.translationY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - startY
                // Closed: only an upward swipe opens. Open: only a downward swipe closes, and only
                // once the inner list has nothing left to scroll up into — otherwise this drag is
                // ordinary list scrolling and must be left alone.
                val shouldClaim = if (isOpen) dy > 0 && isContentScrolledToTop() else dy < 0
                if (!dragging && abs(dy) > TOUCH_SLOP && shouldClaim) {
                    dragging = true
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                }
                if (dragging) {
                    revealTarget.translationY = (startTranslation + dy).coerceIn(0f, targetHeight)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return
                velocityTracker?.computeCurrentVelocity(1000)
                val velocityY = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null
                dragging = false
                val targetIsOpen = when {
                    velocityY < -FLING_VELOCITY -> true
                    velocityY > FLING_VELOCITY -> false
                    else -> revealTarget.translationY < targetHeight / 2f
                }
                settleTo(targetIsOpen)
            }
        }
    }

    fun open() = settleTo(true)
    fun close() = settleTo(false)

    private fun settleTo(open: Boolean) {
        revealTarget.animate()
            .translationY(if (open) 0f else targetHeight)
            .setDuration(200)
            .withEndAction {
                isOpen = open
                onOpenStateChanged(open)
            }
            .start()
    }

    companion object {
        private const val TOUCH_SLOP = 16
        private const val FLING_VELOCITY = 800
    }
}
