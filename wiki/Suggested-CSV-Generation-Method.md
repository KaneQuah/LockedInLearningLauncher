# Suggested CSV Generation Method

Writing a 50-question deck by hand is tedious. A faster path: describe the deck you want to a general-purpose AI chat assistant (Claude, ChatGPT, Gemini, or similar) and have it output a CSV in one of the two formats described in [CSV Import Format](CSV-Import-Format.md).

This is a manual copy/paste workflow — the app has no built-in AI integration. You generate the CSV in the chat tool, save it as a `.csv` file, then import it via **Manage Decks → Import CSV**.

## Prompt template

Paste something like this, filling in your own topic and question count:

```
Generate a CSV file for a flashcard/quiz app. Output ONLY the CSV, no commentary.

Columns: front,back,type,hint,distractor1,distractor2,distractor3

Rules:
- type must be one of: FLASHCARD, MCQ, MATH, LANGUAGE
- For MCQ questions, fill in distractor1-3 with plausible wrong answers
- For FLASHCARD/MATH/LANGUAGE questions, leave distractor columns empty
- hint is optional — leave empty if none
- Escape any commas inside a field with double quotes
- Generate 30 questions about: <YOUR TOPIC HERE>
```

This targets Format B from [CSV Import Format](CSV-Import-Format.md) (individual `distractor1/2/3` columns), which is easier for a chat assistant to produce reliably than the single JSON-array `distractors` column in Format A.

## Example topic-specific prompt

```
Generate a CSV file for a flashcard/quiz app. Output ONLY the CSV, no commentary.

Columns: front,back,type,hint,distractor1,distractor2,distractor3

Rules:
- type must be one of: FLASHCARD, MCQ, MATH, LANGUAGE
- For MCQ questions, fill in distractor1-3 with plausible wrong answers
- For FLASHCARD/MATH/LANGUAGE questions, leave distractor columns empty
- Escape any commas inside a field with double quotes

Generate 20 MCQ questions testing basic Spanish vocabulary for household objects,
and 10 MATH questions on multiplying two-digit numbers.
```

## After generating

- **Proofread before importing**, especially anything factual — AI assistants can produce confidently wrong answers (dates, formulas, translations, capitals). This matters more here than in a normal chat, since a wrong `correctAnswer` becomes something the gate will mark you wrong for even when you answer correctly.
- **Check the header row matches exactly** — `front`, `back`, `type`, `hint`, `distractor1`, `distractor2`, `distractor3` (case-insensitive, but the importer looks for these exact names or their aliases; see [CSV Import Format](CSV-Import-Format.md)).
- **Split large decks into multiple requests.** Asking for hundreds of questions in one go risks the assistant truncating output or drifting from the format partway through. 20-40 questions per request is a safe batch size; import each batch as a separate CSV into the same deck.
- **Open the file in a text editor (or spreadsheet app) before importing** to sanity-check it's well-formed CSV — chat assistants occasionally emit markdown code fences (` ```csv `) around the output, which you'll need to strip, or malformed quoting on fields containing commas.

## A note on source material and copyright

If you're basing questions on someone else's copyrighted work (a textbook, a paid course, etc.), don't paste large verbatim excerpts into a third-party AI service to have it "convert" them into a CSV — that can run into both the copyright of the original work and the AI provider's own terms of service, and is on you, not this app. Safer approach: describe the *concepts* you want tested in your own words and let the assistant generate original questions from that description, rather than feeding it copyrighted text to reproduce.
