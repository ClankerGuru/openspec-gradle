Run Claude Code headlessly via Gradle wrapper tasks.

## Tasks

| Task | What it does |
|------|-------------|
| `claude-run` | Run Claude in print mode (non-interactive) |
| `claude-resume` | Resume an existing conversation |
| `claude-auth` | Manage authentication |
| `claude-agents` | List available agents |
| `claude-doctor` | Diagnose installation issues |

## Key Flags

| Flag | Description |
|------|-------------|
| `-Pprompt="..."` | **Required** for `claude-run`. The prompt to send. |
| `-PclaudeModel=model` | Override the model (e.g., `claude-sonnet-4-20250514`) |
| `-PmaxBudgetUsd=5.00` | Set a spending cap for the session |
| `-PsessionId=id` | Resume a specific session (`claude-resume`) |
| `-PpermissionMode=plan` | Permission mode: `plan`, `auto`, `default` |
| `-PsystemPrompt="..."` | Override the system prompt |
| `-PappendSystemPrompt="..."` | Append to the default system prompt |
| `-Pverbose=true` | Enable verbose output |

## Examples

```bash
./gradlew claude-run -Pprompt="Fix the failing test in UserService"
./gradlew claude-run -Pprompt="Refactor auth module" -PmaxBudgetUsd=2.00
./gradlew claude-resume -PsessionId=abc123
./gradlew claude-auth
```
