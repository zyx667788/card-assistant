# Card Assistant

An Android card-game assistant that reads the current table from the screen, reconstructs the game state, validates legal actions locally, and asks an LLM to choose among those legal actions.

The first supported game is Hunan Paohuzi. Additional card games are intended to be added as independent modules.

## Current Capabilities

- Analyze one frame when the floating ball is tapped; no automatic tapping or auto-play.
- Paohuzi tiles, melds, winning hands, waits, and chow/pung/kong checks.
- Calibration for hand, melds, opponents' melds, discards, action prompts, and remaining tiles.
- OCR text is converted into a structured game state before any model call.
- A local rules engine validates legal actions before the LLM is asked for advice.
- The result card shows recognized state, recommended action, reason, and alternatives.
- Re-run analysis or start a new game. Advice history is persisted per module.
- New card games can plug in without rewriting capture, OCR, overlay, or advice transport.

## Supported Games

| Game | Status | Notes |
| --- | --- | --- |
| Hunan Paohuzi | MVP | OCR-assisted recognition, rules validation, and discard/chow/pung advice |

## Usage

1. Start the Android capture service.
2. Long-press the floating ball and choose game-zone calibration.
3. Calibrate every required zone for the current game module.
4. Switch the floating ball to Card Assistant.
5. Enter a game and tap the ball when a decision is needed.
6. Review the recognized state and the suggested action, reason, and alternatives.
7. Tap New Game when starting a new round to clear the current module history.

Calibration is required before the first analysis. The assistant reports a missing calibration instead of guessing from default coordinates.

## Workflow

```text
Tap floating ball
  -> capture screen
  -> OCR text and positions
  -> crop and parse game zones
  -> build structured GameState
  -> compute legal actions locally
  -> LLM selects among legal actions
  -> show advice card
```

## Modules

```text
game/core/        game module contracts, state, and advice engine interface
game/paohuzi/     Paohuzi tiles, rules, OCR parser, and prompt policy
game/advice/      OpenAI-compatible advice request and response parsing
game/integration/ Android OCR-to-domain adapter
game/session/     game session, persistent history, analysis orchestration
game/ui/          advice overlay card
```

To add a game:

1. Create a `game/<game>/` package.
2. Implement `GameModule`, `BoardRecognizer`, and `PromptPolicy`.
3. Register the module in `di/GameModuleBindings.kt`.
4. Add editable regional rules under `app/src/main/assets/game/<game>/rules.md`.

See [docs/game-assistant/README.md](docs/game-assistant/README.md) for details.

## Build

Requirements:

- JDK 17
- Android SDK 35
- Gradle Wrapper 8.10.2

```bash
git submodule update --init --recursive
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```

Windows notes are documented in [docs/game-assistant/LOCAL-DEV.md](docs/game-assistant/LOCAL-DEV.md).

The current Windows setup cannot build the full APK because of the `llama-android` Visual Studio/Vulkan requirement. Complete APKs are built by GitHub Actions.

## Design Boundaries

- Read the screen and provide advice only. The app never clicks or plays for the user.
- Only structural rules shared across regions are hard-coded.
- Regional rules such as minimum score, scoring mode, and whether chow is allowed live in the editable rules Markdown.
- Insufficient OCR data produces a verification request instead of invented tiles.
- Model advice must stay inside the locally validated legal-action set.

## Rules and Compliance

Paohuzi rules vary by region. Structural validation belongs to `PaohuziRules`; regional guidance is editable content.

This project is intended for learning, research, and local game review. Users are responsible for complying with local laws, game terms, and platform rules.

## Origin and License

This project continues from the Android capture, OCR, overlay, and model-transport foundation of [ciddwd/overlay-translator](https://github.com/ciddwd/overlay-translator).

Licensed under [Apache-2.0](LICENSE). Third-party components and models retain their original licenses.
