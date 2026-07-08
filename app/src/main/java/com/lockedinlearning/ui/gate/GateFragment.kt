package com.lockedinlearning.ui.gate

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.lockedinlearning.R
import com.lockedinlearning.domain.model.QuestionType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Ports GateScreen.kt's 8-state Compose UI to a ViewFlipper-driven View hierarchy. GateViewModel
 * (and everything beneath it — GateController/FailurePolicyEngine/QuestionSelector/AnswerEvaluator)
 * is completely unchanged by this migration; the only difference from the Compose version is
 * how state is collected (repeatOnLifecycle instead of collectAsStateWithLifecycle) and how each
 * state's UI is populated (direct View binding instead of Composable recomposition).
 */
@AndroidEntryPoint
class GateFragment : Fragment() {

    /** Must share HomeActivity's own GateViewModel instance (not a separate Fragment-scoped one)
     *  — HomeActivity observes gate state to decide when to hide this very fragment, so if each
     *  held its own instance, answering correctly here would never be seen by that observer. */
    private val gateViewModel: GateViewModel by activityViewModels()

    private lateinit var flipper: ViewFlipper
    private var shakeAnimator: ObjectAnimator? = null
    private var mcqShuffleForQuestionId: String? = null
    private var mcqShuffledOptions: List<String> = emptyList()

    /** Flashcard "revealed" is purely local UI state (mirrors GateScreen.kt's `remember(q.id) { revealed }`)
     *  — never round-trips through GateViewModel, since seeing the answer isn't a gate decision. */
    private var flashcardRevealedForId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_gate, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        flipper = view.findViewById(R.id.gate_flipper)

        val root = view.findViewById<View>(R.id.gate_root)
        val flipperInitialPadding = flipper.paddingTop to flipper.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            flipper.updatePadding(top = flipperInitialPadding.first + bars.top, bottom = flipperInitialPadding.second + bars.bottom)
            insets
        }

        view.findViewById<FloatingActionButton>(R.id.fab_emergency_call).setOnClickListener {
            runCatching { startActivity(Intent(Intent.ACTION_DIAL)) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                gateViewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: GateUiState) {
        when (state) {
            is GateUiState.Loading -> flipper.displayedChild = 0
            is GateUiState.Skip, is GateUiState.NoQuestion -> Unit // HomeActivity hides this fragment entirely
            is GateUiState.Question -> renderQuestion(state)
            is GateUiState.Correct -> renderCorrect(state)
            is GateUiState.Wrong -> renderWrong(state)
            is GateUiState.Penalty -> renderPenalty(state)
            is GateUiState.Bypass -> renderBypass(state)
            is GateUiState.Lockout -> renderLockout(state)
        }
    }

    private fun renderQuestion(state: GateUiState.Question) {
        if (state.q.type == QuestionType.FLASHCARD) {
            renderFlashcard(state)
            return
        }
        flipper.displayedChild = 1
        val root = flipper.getChildAt(1)

        root.findViewById<TextView>(R.id.question_type_pill).text = when (state.q.type) {
            QuestionType.MATH -> "Math"
            QuestionType.MCQ -> "Multiple Choice"
            QuestionType.LANGUAGE -> "Language"
            QuestionType.FLASHCARD -> "" // unreachable, handled above
        }
        root.findViewById<TextView>(R.id.question_prompt).text = state.q.prompt

        val hintView = root.findViewById<TextView>(R.id.question_hint)
        hintView.isVisible = state.showHint && state.q.hint != null
        hintView.text = "Hint: ${state.q.hint}"

        val mcqContainer = root.findViewById<LinearLayout>(R.id.mcq_container)
        val textInputContainer = root.findViewById<LinearLayout>(R.id.text_input_container)
        val isMcq = state.q.type == QuestionType.MCQ
        mcqContainer.isVisible = isMcq
        textInputContainer.isVisible = !isMcq

        if (isMcq) {
            if (mcqShuffleForQuestionId != state.q.id) {
                mcqShuffleForQuestionId = state.q.id
                mcqShuffledOptions = (state.q.distractors + state.q.correctAnswer).shuffled()
            }
            mcqContainer.removeAllViews()
            mcqShuffledOptions.forEach { option ->
                val button = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = option
                    minHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min) * 2
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 10.dp }
                    cornerRadius = 12.dp
                    setOnClickListener { gateViewModel.selectMcqOption(option) }
                }
                mcqContainer.addView(button)
            }
        } else {
            val input = root.findViewById<TextInputEditText>(R.id.answer_input)
            root.findViewById<MaterialButton>(R.id.btn_submit).setOnClickListener {
                gateViewModel.submitAnswer(input.text?.toString().orEmpty())
            }
        }

        root.findViewById<MaterialButton>(R.id.btn_hint).apply {
            isVisible = state.hintAllowed && state.q.hint != null && !state.showHint
            setOnClickListener { gateViewModel.revealHint() }
        }
    }

    private fun renderFlashcard(state: GateUiState.Question) {
        flipper.displayedChild = 2
        val root = flipper.getChildAt(2)

        root.findViewById<TextView>(R.id.flashcard_prompt).text = state.q.prompt
        val revealGroup = root.findViewById<LinearLayout>(R.id.flashcard_reveal_group)
        val showAnswerBtn = root.findViewById<MaterialButton>(R.id.btn_show_answer)
        val didYouGetIt = root.findViewById<TextView>(R.id.flashcard_did_you_get_it)
        val gradeRow = root.findViewById<LinearLayout>(R.id.flashcard_grade_row)

        fun applyRevealed(revealed: Boolean) {
            revealGroup.isVisible = revealed
            showAnswerBtn.isVisible = !revealed
            didYouGetIt.isVisible = revealed
            gradeRow.isVisible = revealed
            if (revealed) {
                root.findViewById<TextView>(R.id.flashcard_answer).text = state.q.correctAnswer
                val hintView = root.findViewById<TextView>(R.id.flashcard_hint)
                hintView.isVisible = state.q.hint != null
                hintView.text = state.q.hint
            }
        }

        applyRevealed(flashcardRevealedForId == state.q.id)
        showAnswerBtn.setOnClickListener {
            flashcardRevealedForId = state.q.id
            applyRevealed(true)
        }
        root.findViewById<MaterialButton>(R.id.btn_missed).setOnClickListener { gateViewModel.gradeFlashcard(false) }
        root.findViewById<MaterialButton>(R.id.btn_got_it).setOnClickListener { gateViewModel.gradeFlashcard(true) }
    }

    private fun renderCorrect(state: GateUiState.Correct) {
        flipper.displayedChild = 3
        val root = flipper.getChildAt(3)
        val streakView = root.findViewById<TextView>(R.id.correct_streak_text)
        streakView.isVisible = state.streak > 0
        streakView.text = "🔥 Streak: ${state.streak} days"
    }

    private fun renderWrong(state: GateUiState.Wrong) {
        flipper.displayedChild = 4
        val root = flipper.getChildAt(4)
        root.findViewById<TextView>(R.id.wrong_attempt_text).text =
            if (state.maxAttempts == Int.MAX_VALUE) "Retry until correct — keep going!"
            else "Attempt ${state.attemptsUsed} of ${state.maxAttempts}"

        val shakeTarget = root.findViewById<View>(R.id.wrong_root)
        shakeAnimator?.cancel()
        shakeAnimator = ObjectAnimator.ofFloat(shakeTarget, "translationX", -8.dp.toFloat(), 8.dp.toFloat()).apply {
            duration = 80
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun renderPenalty(state: GateUiState.Penalty) {
        flipper.displayedChild = 5
        cancelShake()
        val root = flipper.getChildAt(5)
        root.findViewById<CircularProgressIndicator>(R.id.penalty_progress)
            .setProgressCompat((state.secondsRemaining * 100 / 60).coerceIn(0, 100), true)
        root.findViewById<TextView>(R.id.penalty_seconds_text).text = "${state.secondsRemaining}s"
    }

    private fun renderBypass(state: GateUiState.Bypass) {
        flipper.displayedChild = 6
        cancelShake()
        val root = flipper.getChildAt(6)
        root.findViewById<TextView>(R.id.bypass_question).text = state.question
        root.findViewById<TextView>(R.id.bypass_correct_answer).text = "Correct answer: ${state.correctAnswer}"
        root.findViewById<TextView>(R.id.bypass_message).text = state.message
        root.findViewById<MaterialButton>(R.id.btn_open_anyway).setOnClickListener { gateViewModel.bypass() }
    }

    private fun renderLockout(state: GateUiState.Lockout) {
        flipper.displayedChild = 7
        cancelShake()
        val root = flipper.getChildAt(7)
        val remainingMs = (state.lockedUntilEpoch - System.currentTimeMillis()).coerceAtLeast(0)
        val totalSeconds = remainingMs / 1000
        root.findViewById<TextView>(R.id.lockout_countdown).text =
            String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private fun cancelShake() {
        shakeAnimator?.cancel()
        shakeAnimator = null
    }

    override fun onDestroyView() {
        cancelShake()
        super.onDestroyView()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
