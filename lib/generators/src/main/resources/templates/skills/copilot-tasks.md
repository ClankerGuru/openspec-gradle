Run GitHub Copilot headlessly via Gradle wrapper tasks.

## Tasks

| Task | What it does |
|------|-------------|
| `copilot-run` | Run Copilot in print mode (non-interactive) |
| `copilot-resume` | Resume an existing conversation |

## Key Flags

| Flag | Description |
|------|-------------|
| `-Pprompt="..."` | **Required** for `copilot-run` |
| `-PcopilotModel=model` | Override the model |
| `-Pagent=name` | Use a specific agent |
| `-Peffort=high` | Set effort level |
| `-Pautopilot=true` | Run without interaction |
| `-PsessionId=id` | Resume a specific session |
| `-Pyolo=true` | Skip all confirmations |

## Passthrough

For flags not yet mapped to Gradle properties:
```bash
./gradlew copilot-run -Pprompt="..." -PextraArgs="--new-flag,--other=val"
```

## Examples

```bash
./gradlew copilot-run -Pprompt="Add unit tests for the parser"
./gradlew copilot-run -Pprompt="Explain this codebase" -Pautopilot=true
./gradlew copilot-resume -PsessionId=abc123
```
