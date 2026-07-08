# LockedInLearning

An Android home screen replacement (launcher) that makes you answer a study question correctly before you can get to your apps. Built on an AOSP Launcher3-style core, with a spaced-review deck system layered on top as the "gate" between you and your phone.

## How it works

- LockedInLearning replaces your default launcher. Every time you return to the home screen, a gate screen intercepts you.
- Answer a question from one of your decks (multiple choice or flashcard-style) to unlock the home screen.
- Wrong answers trigger a configurable penalty/lockout policy; a daily streak tracks how consistently you're studying.
- Decks and questions are managed on-device (create/edit questions, import via CSV) and stored locally in a Room database.

## Features

- Custom launcher (all-apps grid, drag-and-drop, hotseat/dock, notification badges)
- Deck manager and question editor
- CSV import for bulk-adding questions
- Configurable gate/failure policy and streak tracking

## Building

This is a standard Gradle-based Android project.

```bash
./gradlew assembleDebug
```

Requirements:
- Android Studio (Koala or newer recommended)
- JDK 17
- Android SDK with `compileSdk 34` / `minSdk 29`

Open the project in Android Studio and let Gradle sync, or build from the command line with the wrapper above.

## Tech stack

Kotlin, Jetpack Compose + traditional Views (for the launcher core), Room, DataStore, Hilt, WorkManager, Kotlin Coroutines/Serialization.

## License

MIT — see [LICENSE](LICENSE).

## Support

If this project is useful to you, consider supporting its development:

- Buy Me a Coffee / Ko-fi: https://ko-fi.com/kanequah

Donations are optional and appreciated, not required to use or modify this software.
