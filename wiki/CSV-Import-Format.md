# CSV Import Format

Decks can be bulk-populated by importing a CSV file (`CsvImporter`). Two header layouts are supported and auto-detected based on which columns are present — you don't need to declare which one you're using.

## Required columns

Every row needs, at minimum:

| Column | Aliases | Notes |
|---|---|---|
| `front` | `prompt` | The question text. |
| `back` | `answer` | The correct answer. |

If neither `front`/`prompt` or `back`/`answer` is found in the header, the whole import is rejected with an error. Rows where `front` or `back` is blank are silently skipped.

## Optional columns

| Column | Notes |
|---|---|
| `type` | One of `FLASHCARD`, `MATH`, `MCQ`, `LANGUAGE` (case-insensitive). Defaults to `FLASHCARD` if missing or unrecognized. |
| `hint` | Free text, shown to the user when hints are allowed by the deck's failure policy. |
| `id`, `category`, `image` | Accepted (Format A) but currently ignored on import. |

## Distractors (wrong-answer options for MCQ)

Distractors can be supplied in **either** of two shapes — pick whichever is easier to generate:

### Format A — single JSON-array column

```
id,front,back,type,category,distractors,hint,image
1,"What is the capital of France?","Paris",MCQ,geography,"[""London"",""Berlin"",""Madrid""]",,
```

- Column name: `distractors`
- Value: a JSON array of strings, e.g. `["London","Berlin","Madrid"]`
- Must start with `[` to be recognized as JSON — otherwise it's ignored.

### Format B — individual columns

```
front,back,type,hint,distractor1,distractor2,distractor3
What is the capital of France?,Paris,MCQ,Starts with P,London,Berlin,Madrid
```

- Columns: `distractor1`, `distractor2`, `distractor3`
- Blank distractor cells are dropped (you can supply fewer than 3).

If neither shape is present, the question is imported with an empty distractor list (fine for `FLASHCARD` type).

## Minimal example (flashcards only)

```
front,back
What is the powerhouse of the cell?,Mitochondria
2 + 2,4
```

`type` defaults to `FLASHCARD` and no distractors are needed for plain flashcards.

## Import behavior

- The first row is always treated as the header; header names are matched case-insensitively after trimming whitespace.
- Rows with fewer columns than required (missing `front`/`back` cells) are skipped and reported in `errors`.
- Each imported question gets a freshly generated ID — any `id` column in the CSV is not reused.
- The importer returns a count of successfully imported questions, a `skipped` count, and a list of per-line error messages, so a bad row won't abort the whole import.

## Encoding

Files are read as UTF-8. Save your CSV as UTF-8 (not UTF-16 or a legacy codepage) to avoid mangled characters, especially for non-English content.
