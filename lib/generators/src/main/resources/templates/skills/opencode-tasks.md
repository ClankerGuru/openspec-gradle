Run opencode headlessly via Gradle wrapper tasks.

## Tasks

| Task | What it does |
|------|-------------|
| `opencode-run` | Run opencode with a message (non-interactive) |
| `opencode-serve` | Start a headless HTTP/WebSocket server |

## Key Flags

| Flag | Description |
|------|-------------|
| `-Pprompt="..."` | **Required** for `opencode-run`. The message to send. |
| `-PopencodeModel=model` | Override the model |
| `-Pagent=name` | Use a specific agent |
| `-Psession=id` | Resume a specific session |
| `-PcontinueSession=true` | Continue the most recent session |
| `-Pfork=true` | Fork a session instead of continuing it |
| `-Pport=8080` | Port for `opencode-serve` |
| `-Phostname=0.0.0.0` | Hostname for `opencode-serve` |

## Examples

```bash
./gradlew opencode-run -Pprompt="Add error handling to the API layer"
./gradlew opencode-run -Pprompt="Continue work" -PcontinueSession=true
./gradlew opencode-serve -Pport=9090
```
