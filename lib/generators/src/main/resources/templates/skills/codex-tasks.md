Run OpenAI Codex headlessly via Gradle wrapper tasks.

## Tasks

| Task | What it does |
|------|-------------|
| `codex-exec` | Run Codex in exec mode (non-interactive) |
| `codex-review` | Run a code review non-interactively |

## Key Flags

| Flag | Description |
|------|-------------|
| `-Pprompt="..."` | **Required** for `codex-exec`. The prompt to send. |
| `-PcodexModel=model` | Override the model |
| `-PfullAuto=true` | Run without approval prompts |
| `-Psandbox=docker` | Sandbox mode: `docker`, `none` |
| `-PaskForApproval=always` | Approval mode: `always`, `never`, `auto` |
| `-Pprofile=name` | Use a named configuration profile |
| `-Poss=true` | Use open-source models only |

## Examples

```bash
./gradlew codex-exec -Pprompt="Migrate the database schema"
./gradlew codex-exec -Pprompt="Fix lint errors" -PfullAuto=true
./gradlew codex-review
```
