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
| `-Pprompt="..."` | **Required** for `claude-run` |
| `-PclaudeModel=model` | Override the model |
| `-PmaxBudgetUsd=5.00` | Spending cap |
| `-PsessionId=id` | Resume a specific session |
| `-PpermissionMode=plan` | `plan`, `auto`, `default` |
| `-PsystemPrompt="..."` | Override system prompt |
| `-PappendSystemPrompt="..."` | Append to system prompt |

## Examples

```bash
./gradlew claude-run -Pprompt="Fix the failing test in UserService"
./gradlew claude-run -Pprompt="Refactor auth module" -PmaxBudgetUsd=2.00
./gradlew claude-resume -PsessionId=abc123
```
