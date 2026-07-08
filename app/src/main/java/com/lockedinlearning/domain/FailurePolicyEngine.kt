package com.lockedinlearning.domain

import com.lockedinlearning.domain.model.FailureMode
import com.lockedinlearning.domain.model.FailurePolicy
import com.lockedinlearning.domain.model.GateResult
import javax.inject.Inject

class FailurePolicyEngine @Inject constructor() {

    /**
     * Given the policy and how many attempts have been used,
     * produce the appropriate failure result.
     */
    fun evaluate(policy: FailurePolicy, attemptsUsed: Int): GateResult {
        return when (policy.mode) {
            FailureMode.RETRY -> {
                GateResult.FailRetry(attemptsUsed, Int.MAX_VALUE)
            }
            FailureMode.MAX_ATTEMPTS -> {
                if (attemptsUsed < policy.maxAttempts) {
                    GateResult.FailRetry(attemptsUsed, policy.maxAttempts)
                } else {
                    GateResult.FailBypass(policy.bypassMessage)
                }
            }
            FailureMode.TIME_PENALTY -> {
                GateResult.FailPenalty(policy.penaltySeconds)
            }
            FailureMode.HARD_LOCK -> {
                if (attemptsUsed < policy.maxAttempts) {
                    GateResult.FailRetry(attemptsUsed, policy.maxAttempts)
                } else {
                    val lockedUntil = System.currentTimeMillis() + policy.lockoutMinutes * 60_000L
                    GateResult.FailLockout(lockedUntil)
                }
            }
        }
    }
}
