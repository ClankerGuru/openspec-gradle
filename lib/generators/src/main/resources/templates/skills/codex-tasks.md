Run OpenAI Codex headlessly via Gradle wrapper tasks.

## Tasks

| Task | What it does |
|------|-------------|
| `codex-exec` | Run Codex in exec mode (non-interactive) |
| `codex-review` | Run a code review non-interactively |

## Key Flags

| Flag | Description |
|------|-------------|
| `-Pprompt="..."` | **Required** for `codex-exec` |
| `-PcodexModel=model` | Override the model |
| `-PfullAuto=true` | Run without approval prompts |
| `-Psandbox=docker` | Sandbox mode: `docker`, `none` |
| `-Pprofile=name` | Configuration profile |

## Examples

```bash
./gradlew codex-exec -Pprompt="Migrate the database schema"
./gradlew codex-exec -Pprompt="Fix lint errors" -PfullAuto=true
./gradlew codex-review
```
