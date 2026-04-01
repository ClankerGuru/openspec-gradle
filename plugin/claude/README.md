# plugin-claude

Settings plugin that wraps the Claude Code CLI as Gradle tasks.

## What it does

Registers Gradle tasks that map 1:1 to the `claude` CLI. Every flag, subcommand, and option is exposed as a typed Gradle task property. Tasks run Claude Code in non-interactive print mode (`claude -p`), making it usable from CI pipelines, build scripts, and agent orchestration.

## Why it exists

Lets you invoke Claude Code from any Gradle build without shell scripts. The typed task properties provide IDE autocomplete, validation, and composability with other Gradle tasks. Because it is a Settings plugin, it applies once at the root and is available in all subprojects and included builds.

## Plugin ID

```
zone.clanker.claude
```

**CLI_VERSION:** `2.1.81`

## Usage

```kotlin
// settings.gradle.kts
plugins {
    id("zone.clanker.claude") version "<version>"
}
```

```bash
./gradlew claude-run -Pprompt="Refactor the auth module" -PclaudeModel=sonnet
```

## Tasks

### claude-run

Run Claude Code in non-interactive print mode. This is the primary task with full CLI flag coverage.

| Flag | Type | Gradle Property | CLI Equivalent |
|---|---|---|---|
| `prompt` | `String` (required) | `-Pprompt=...` | `claude -p <prompt>` |
| `claudeModel` | `String` | `-PclaudeModel=...` | `--model` |
| `effort` | `String` | `-Peffort=...` | `--effort` |
| `fallbackModel` | `String` | `-PfallbackModel=...` | `--fallback-model` |
| `permissionMode` | `String` | `-PpermissionMode=...` | `--permission-mode` |
| `dangerouslySkipPermissions` | `Boolean` | `-PdangerouslySkipPermissions` | `--dangerously-skip-permissions` |
| `allowedTools` | `String` | `-PallowedTools=...` | `--allowed-tools` |
| `disallowedTools` | `String` | `-PdisallowedTools=...` | `--disallowed-tools` |
| `tools` | `String` | `-Ptools=...` | `--tools` |
| `outputFormat` | `String` | `-PoutputFormat=...` | `--output-format` |
| `jsonSchema` | `String` | `-PjsonSchema=...` | `--json-schema` |
| `includePartialMessages` | `Boolean` | `-PincludePartialMessages` | `--include-partial-messages` |
| `verbose` | `Boolean` | `-Pverbose` | `--verbose` |
| `systemPrompt` | `String` | `-PsystemPrompt=...` | `--system-prompt` |
| `appendSystemPrompt` | `String` | `-PappendSystemPrompt=...` | `--append-system-prompt` |
| `sessionId` | `String` | `-PsessionId=...` | `--session-id` |
| `sessionName` | `String` | `-PsessionName=...` | `--name` |
| `noSessionPersistence` | `Boolean` | `-PnoSessionPersistence` | `--no-session-persistence` |
| `maxBudgetUsd` | `String` | `-PmaxBudgetUsd=...` | `--max-budget-usd` |
| `agent` | `String` | `-Pagent=...` | `--agent` |
| `agents` | `String` | `-Pagents=...` | `--agents` |
| `addDir` | `List<String>` | -- | `--add-dir` (repeatable) |
| `mcpConfig` | `List<String>` | -- | `--mcp-config` (repeatable) |
| `pluginDir` | `List<String>` | -- | `--plugin-dir` (repeatable) |
| `settingSources` | `String` | `-PsettingSources=...` | `--setting-sources` |
| `settings` | `String` | `-Psettings=...` | `--settings` |
| `bare` | `Boolean` | `-Pbare` | `--bare` |
| `brief` | `Boolean` | `-Pbrief` | `--brief` |
| `disableSlashCommands` | `Boolean` | `-PdisableSlashCommands` | `--disable-slash-commands` |
| `strictMcpConfig` | `Boolean` | -- | `--strict-mcp-config` |
| `worktree` | `String` | `-Pworktree=...` | `--worktree` |
| `tmux` | `Boolean` | `-Ptmux` | `--tmux` |
| `inputFormat` | `String` | `-PinputFormat=...` | `--input-format` |
| `file` | `List<String>` | -- | `--file` (repeatable) |
| `betas` | `List<String>` | -- | `--betas` (repeatable) |
| `debug` | `String` | `-Pdebug=...` | `--debug` |
| `debugFile` | `String` | `-PdebugFile=...` | `--debug-file` |

### Other tasks

| Task | Description | Key Flags |
|---|---|---|
| `claude-resume` | Resume a Claude Code conversation | `-PsessionId=...` (omit for `--continue`), `-PforkSession` |
| `claude-from-pr` | Resume a session linked to a PR | `-Ppr=<number>` (required) |
| `claude-auth` | Manage Claude Code authentication | -- |
| `claude-version` | Show Claude Code version | -- |
| `claude-doctor` | Check auto-updater health | -- |
| `claude-mcp` | Configure and manage MCP servers | -- |
| `claude-agents` | List configured agents | -- |
| `claude-update` | Check for updates | -- |
| `claude-setup-token` | Set up a long-lived auth token | -- |
| `claude-auto-mode` | Inspect auto mode classifier config | -- |
| `claude-install` | Install Claude Code native build | `-Ptarget=...` |
| `claude-plugins` | Manage Claude Code plugins | -- |

## Key Classes

| Class | Description |
|---|---|
| `ClaudePlugin` | Settings plugin entry point. Registers all tasks on the root project. |
| `ClaudeRunTask` | Extends `Exec`. Builds the `claude -p` command from typed properties. |
| `ClaudeResumeTask` | Extends `Exec`. Runs `claude --resume` or `claude --continue`. |
| `ClaudeFromPrTask` | Extends `Exec`. Runs `claude --from-pr <number>`. |
| `ClaudeAuthTask` | Extends `Exec`. Runs `claude auth`. |
| `ClaudeVersionTask` | Extends `Exec`. Runs `claude --version`. |
| `ClaudeDoctorTask` | Extends `Exec`. Runs `claude doctor`. |
| `ClaudeMcpTask` | Extends `Exec`. Runs `claude mcp`. |
| `ClaudeAgentsTask` | Extends `Exec`. Runs `claude agents`. |
| `ClaudeUpdateTask` | Extends `Exec`. Runs `claude update`. |
| `ClaudeSetupTokenTask` | Extends `Exec`. Runs `claude setup-token`. |
| `ClaudeAutoModeTask` | Extends `Exec`. Runs `claude auto-mode`. |
| `ClaudeInstallTask` | Extends `Exec`. Runs `claude install [target]`. |
| `ClaudePluginsTask` | Extends `Exec`. Runs `claude plugins`. |

## Dependencies

- `compileOnly(gradleApi())`

No runtime dependencies beyond Gradle itself. Requires the `claude` CLI to be installed and on PATH.
