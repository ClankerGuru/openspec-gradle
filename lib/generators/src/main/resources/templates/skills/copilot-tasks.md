Run GitHub Copilot headlessly via Gradle wrapper tasks.

## Tasks

| Task | What it does |
|------|-------------|
| `copilot-run` | Run Copilot in print mode (non-interactive) |
| `copilot-resume` | Resume an existing conversation |

## Key Flags

| Flag | Description |
|------|-------------|
| `-Pprompt="..."` | **Required** for `copilot-run`. The prompt to send. |
| `-PcopilotModel=model` | Override the model |
| `-Pagent=name` | Use a specific agent |
| `-Peffort=high` | Set effort level |
| `-Pautopilot=true` | Run without user interaction |
| `-PsessionId=id` | Resume a specific session (`copilot-resume`) |
| `-PmaxAutopilotContinues=10` | Max continuation rounds in autopilot |
| `-Pyolo=true` | Skip all confirmations |

## Examples

```bash
./gradlew copilot-run -Pprompt="Add unit tests for the parser"
./gradlew copilot-run -Pprompt="Explain this codebase" -Pautopilot=true
./gradlew copilot-resume -PsessionId=abc123
```
