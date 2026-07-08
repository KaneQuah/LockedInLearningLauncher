# LockedInLearning Wiki

LockedInLearning is a home-screen replacement (launcher) that makes you answer a study question correctly before it lets you back into your apps. Instead of a passive habit tracker, it puts a hard checkpoint between you and your phone.

## Pages

- [CSV Import Format](CSV-Import-Format.md) — how to format a CSV to bulk-import questions into a deck.
- [Suggested CSV Generation Method](Suggested-CSV-Generation-Method.md) — using an AI chat assistant to generate a question bank CSV.
- [Failure Modes](Failure-Modes.md) — what happens when you answer wrong, and how to configure it per deck.

## Why use it

Most study/habit apps are opt-in: you have to remember to open them, and it's just as easy to not. LockedInLearning flips that — the friction is on the path back to distraction, not on the path to studying. Every home-screen return becomes a tiny, unavoidable review rep, which is a much stronger forcing function for spaced repetition than a notification you can swipe away.

## Best practices

- **Start with a small, high-quality deck.** A deck of 20-50 well-written questions you actually want to drill (vocab, formulas, facts) works far better than dumping an entire textbook in — every gate interruption should feel worth answering.
- **Match the failure mode to the deck's content**, not just to how strict you want to feel — see [Failure Modes](Failure-Modes.md) for the full breakdown:
  - **MCQ and short factual answers** — safe with any mode, including `HARD_LOCK`, since there's always a definite right answer to converge on.
  - **⚠️ Do not use `RETRY` (unlimited attempts) with open-ended question banks.** `RETRY` never bypasses — if a `FLASHCARD`/free-text question's grading doesn't line up with how you naturally phrase the answer (it only tolerates small typos via a Levenshtein distance of ≤2, not rephrasing or partial credit), you can get stuck retrying an unanswerable question forever with no way through. Use `MAX_ATTEMPTS` (which lets you bypass after a few tries) or `TIME_PENALTY` for open-ended/flashcard-style decks instead.
  - Reserve `HARD_LOCK` for decks where you're confident every question is unambiguous — it has no bypass path at all once attempts run out.
- **Keep hints on** for decks in early "learning" stages, and consider turning them off only once a deck is mostly "mastered" (see mastery levels in [Failure Modes](Failure-Modes.md)).
- **Set a sensible cooldown.** The gate won't reappear for a while after a correct answer — tune this so you're not answering the same question every 10 seconds, but still often enough to matter.

## Limitations

- **Backgrounded apps can bypass the gate.** The gate only intercepts you when you return to the *home screen*. If you already have an app open and switch to it via Android's Recents/Overview screen (rather than going home first), you go straight back into that app — the launcher doesn't (and can't, without accessibility-service-level hooks) intercept task switching directly.
- **This is a launcher, not a device-wide blocker.** It governs what happens on your home screen; it isn't a parental-control or app-blocking tool, and a determined user (i.e., yourself) can always switch the default launcher back in system settings.
- **Grading has no semantic understanding — it's case-insensitive exact matching, with light typo tolerance only for flashcards.** `MCQ` and `LANGUAGE` questions require a case-insensitive (and for `LANGUAGE`, accent-insensitive) exact match to the stored answer; `MATH` requires an exact numeric match; only `FLASHCARD` free-text answers get any fuzziness (Levenshtein distance ≤2, so small typos pass but rephrasing or partial answers don't).
