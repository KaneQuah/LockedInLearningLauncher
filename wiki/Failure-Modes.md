# Failure Modes for Question Answering

Every deck has a `FailurePolicy` that decides what happens when you answer the gate question wrong. The policy's `mode` (a `FailureMode`) picks the strategy; `FailurePolicyEngine.evaluate()` turns a wrong answer + attempt count into a `GateResult` that the gate UI renders.

## The four modes

### `RETRY`
Answer wrong, try again — indefinitely. No attempt cap, no penalty, no lockout.

- Result: `FailRetry(attemptsUsed, Int.MAX_VALUE)` every time.
- UI: `gate_state_wrong` — shows the wrong-answer state and lets you retry immediately.
- Use case: lowest-friction mode; good for casual review decks. This is the `Easy` onboarding preset.

### `MAX_ATTEMPTS`
Gives you a limited number of tries, then lets you through anyway (with a shame message) rather than locking you out.

- While `attemptsUsed < maxAttempts`: `FailRetry(attemptsUsed, maxAttempts)` — same wrong-answer/retry UI, now showing an attempts-remaining counter.
- Once attempts are exhausted: `FailBypass(bypassMessage)` — the gate lets you through, displaying `bypassMessage` (default: "Better luck next time 👀") via `gate_state_bypass`.
- Use case: keeps the gate from becoming a hard blocker if you genuinely don't know an answer. This is the `Normal` onboarding preset (`maxAttempts = 3`, `penaltySeconds = 15` — note `penaltySeconds` is unused in this mode).

### `TIME_PENALTY`
Every wrong answer costs you a fixed cooldown, no matter how many attempts you've made.

- Result: `FailPenalty(penaltySeconds)` on every failure — there's no attempt counting.
- UI: `gate_state_penalty` — presumably shows a countdown of `penaltySeconds` before you can try again.
- Use case: discourages guessing/spamming without ever letting you bypass entirely. This is the `Hard` onboarding preset (`penaltySeconds = 60`, hints disabled).

### `HARD_LOCK`
The strictest mode: limited attempts, then a real lockout — no bypass.

- While `attemptsUsed < maxAttempts`: `FailRetry(attemptsUsed, maxAttempts)`.
- Once attempts are exhausted: `FailLockout(lockedUntilEpoch)`, where `lockedUntilEpoch = now + lockoutMinutes * 60_000`. Rendered via `gate_state_lockout`.
- There is no bypass path — you're locked out of the home screen until the lockout timer expires.
- Use case: the "no escape" option for decks you actually want to force mastery of.

## Policy fields reference

| Field | Used by | Meaning |
|---|---|---|
| `mode` | all | Which of the four modes above applies. |
| `maxAttempts` | `MAX_ATTEMPTS`, `HARD_LOCK` | Number of wrong answers allowed before bypass/lockout triggers. Ignored by `RETRY` and `TIME_PENALTY`. |
| `penaltySeconds` | `TIME_PENALTY` | Cooldown length (seconds) after each wrong answer. |
| `lockoutMinutes` | `HARD_LOCK` | Lockout length (minutes) once `maxAttempts` is exhausted. |
| `bypassMessage` | `MAX_ATTEMPTS` | Message shown when the user is let through after exhausting attempts. |
| `hintAllowed` | all (via `GateController.hintAllowed()`) | Whether the gate screen offers a hint for the current question. |

## Built-in presets (`FailurePolicyPresets`, shown during onboarding)

| Preset | mode | maxAttempts | penaltySeconds | hintAllowed |
|---|---|---|---|---|
| `Easy` | `RETRY` | — | 30 (default, unused) | true |
| `Normal` | `MAX_ATTEMPTS` | 3 | 15 (unused) | true (default) |
| `Hard` | `TIME_PENALTY` | — (default) | 60 | false |
| `Custom` | `FailurePolicy()` defaults | — | — | — (starting point for user-defined policies) |

## What happens on a correct answer (all modes)

Independent of `mode`, a correct answer always resolves to `GateResult.Pass` and advances the question's mastery streak (`consecutiveCorrect`): 2+ in a row → "learning" (level 1), 5+ in a row → "mastered" (level 2). Any wrong answer resets `consecutiveCorrect` to 0 and caps mastery at "learning" (level 1) if it was higher.

## Attempt/session lifecycle

Attempt counting (`attemptsUsed`) is per gate session — it resets to 0 each time `GateController.loadQuestion()` picks a new question, not per-app-launch. A `NoQuestion` result (no active deck, or the deck is empty) is a separate case handled upstream in `shouldShowGate()`, not part of the failure-mode logic.
