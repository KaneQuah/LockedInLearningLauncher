package com.lockedinlearning.ui.home

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.button.MaterialButton
import com.lockedinlearning.R

/**
 * S-09 top row: streak · today-correct · Do More · settings — the one piece of chrome that
 * makes this a "LockedInLearning" launcher rather than a stock one. Preserved exactly from the
 * Compose version; only the "Do More" button changes visually, since MDC's MaterialButton
 * (app:icon/app:iconGravity/app:iconPadding) centers icon+text as one measured unit, fixing the
 * old hand-rolled Row+Icon+Spacer+Text baseline mismatch.
 */
class TopStatusRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    private val streakPill: TextView
    private val todayCorrectPill: TextView
    private val doMoreButton: MaterialButton
    private val settingsButton: ImageButton

    var onProgressClick: (() -> Unit)? = null
    var onDoMoreClick: (() -> Unit)? = null
    var onSettingsClick: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_top_status_row, this, true)
        streakPill = findViewById(R.id.pill_streak)
        todayCorrectPill = findViewById(R.id.pill_today_correct)
        doMoreButton = findViewById(R.id.btn_do_more)
        settingsButton = findViewById(R.id.btn_settings)

        streakPill.setOnClickListener { onProgressClick?.invoke() }
        todayCorrectPill.setOnClickListener { onProgressClick?.invoke() }
        doMoreButton.setOnClickListener { onDoMoreClick?.invoke() }
        settingsButton.setOnClickListener { onSettingsClick?.invoke() }
    }

    fun bind(streak: Int, todayCorrect: Int) {
        streakPill.text = "🔥 $streak"
        todayCorrectPill.text = "✅ $todayCorrect"
    }
}
