Run opencode headlessly via Gradle wrapper tasks.

## Tasks

| Task | What it does |
|------|-------------|
| `opencode-run` | Run opencode with a message (non-interactive) |
| `opencode-serve` | Start a headless HTTP/WebSocket server |

## Key Flags

| Flag | Description |
|------|-------------|
| `-Pprompt="..."` | **Required** for `opencode-run` |
| `-PopencodeModel=model` | Override the model |
| `-Pagent=name` | Use a specific agent |
| `-PcontinueSession=true` | Continue most recent session |
| `-Pport=8080` | Port for `opencode-serve` |

## Passthrough

For flags not yet mapped to Gradle properties:
```bash
./gradlew opencode-run -Pprompt="..." -PextraArgs="--new-flag,--other=val"
```

## Examples

```bash
./gradlew opencode-run -Pprompt="Add error handling to the API layer"
./gradlew opencode-run -Pprompt="Continue work" -PcontinueSession=true
./gradlew opencode-serve -Pport=9090
```
