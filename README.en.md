# Card Assistant

An Android card-game assistant powered by screen capture and a cloud vision-language model. The app sends the current table screenshot to an OpenAI-compatible vision model, reconstructs the visible game state, validates Paohuzi actions locally, and asks a text LLM for advice.

Supported modes:

- Hunan Paohuzi: VLM recognition, local rules validation, and LLM advice
- Dou Dizhu: prompt-only mode with VLM observation and LLM advice

## Workflow

```text
Tap the floating ball
  -> capture the screen
  -> encode a JPEG
  -> VLM recognizes the game state
  -> build a structured state
  -> Paohuzi validates legal actions locally
  -> text LLM gives advice
  -> overlay shows the result
```

The app never clicks, plays, or operates the game automatically.

## Usage

1. Start the app and grant overlay permission.
2. Configure an OpenAI-compatible Base URL, API key, and vision-capable model.
3. Start the capture service.
4. Enter a game and tap the floating ball.
5. Review the recognized state and advice in the result card.
6. Tap New Game when starting a new round.

## Model Configuration

The VLM and text decision model share the same OpenAI-compatible configuration:

- Base URL
- API key
- Model name

The selected model must support image input. A text-only model cannot perform screen recognition.

## Modules

```text
game/core/        module contracts, state, and advice engine interface
game/paohuzi/     Paohuzi VLM parsing, tiles, rules, and prompts
game/advice/      OpenAI-compatible advice request and response parsing
game/session/     session history, persistence, and orchestration
game/ui/          advice overlay card
game/vlm/         generic prompt-only VLM module
```

## Build

Requirements:

- JDK 17
- Android SDK 35
- Gradle Wrapper 8.10.2

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## Boundaries

- Screen reading and advice only; no automatic game control.
- Paohuzi actions are checked by the local rules engine before model advice.
- Missing or uncertain VLM output must be reported instead of invented.
- Dou Dizhu is currently prompt-only and has no local rules engine.
- Screenshots are sent to the configured cloud model service.

## Compliance

This project is intended for learning, research, and local game review. Users are responsible for complying with local laws, game terms, and platform rules.

## License

Licensed under [Apache-2.0](LICENSE). Third-party components and models retain their original licenses.
