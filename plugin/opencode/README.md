# plugin-opencode

Settings plugin that wraps the OpenCode CLI as Gradle tasks.

## What it does

Registers Gradle tasks that map 1:1 to the `opencode` CLI. Every flag, subcommand, and option is exposed as a typed Gradle task property. Tasks run OpenCode in non-interactive mode (`opencode run`), with stdin from `/dev/null`. Usable from CI pipelines, build scripts, and agent orchestration.

## Why it exists

Lets you invoke OpenCode from any Gradle build without shell scripts. The typed task properties provide IDE autocomplete, validation, and composability with other Gradle tasks. Because it is a Settings plugin, it applies once at the root and is available in all subprojects and included builds.

## Plugin ID

```
zone.clanker.opencode
```

**CLI_VERSION:** `1.3.13`

## Usage

```kotlin
// settings.gradle.kts
plugins {
    id("zone.clanker.opencode") version "<version>"
}
```

```bash
./gradlew opencode-run -Pprompt="Refactor the auth module" -PopencodeModel=claude-sonnet
```

## Tasks

### opencode-run

Run OpenCode with a message. This is the primary task with full CLI flag coverage.

| Flag | Type | Gradle Property | CLI Equivalent |
|---|---|---|---|
| `prompt` | `String` (required) | `-Pprompt=...` | `opencode run <prompt>` |
| `opencodeModel` | `String` | `-PopencodeModel=...` | `--model` |
| `agent` | `String` | `-Pagent=...` | `--agent` |
| `printLogs` | `Boolean` | `-PprintLogs` | `--print-logs` |
| `logLevel` | `String` | `-PlogLevel=...` | `--log-level` |
| `pure` | `Boolean` | `-Ppure` | `--pure` |
| `port` | `Int` | `-Pport=...` | `--port` |
| `hostname` | `String` | `-Phostname=...` | `--hostname` |
| `mdns` | `Boolean` | `-Pmdns` | `--mdns` |
| `mdnsDomain` | `String` | `-PmdnsDomain=...` | `--mdns-domain` |
| `cors` | `List<String>` | -- | `--cors` (repeatable) |
| `continueSession` | `Boolean` | `-PcontinueSession` | `--continue` |
| `session` | `String` | `-Psession=...` | `--session` |
| `fork` | `Boolean` | `-Pfork` | `--fork` |
| `opencodePrompt` | `String` | `-PopencodePrompt=...` | `--prompt` |

### Other tasks

| Task | Description | Key Flags |
|---|---|---|
| `opencode-resume` | Resume an opencode conversation | `-Psession=...` (omit for `--continue`), `-Pfork` |
| `opencode-pr` | Fetch and checkout a GitHub PR branch | `-Ppr=<number>` (required) |
| `opencode-attach` | Attach to a running opencode server | `-Purl=<url>` (required) |
| `opencode-upgrade` | Upgrade to latest or specific version | `-Ptarget=...` |
| `opencode-models` | List all available models | `-Pprovider=...` |
| `opencode-export` | Export session data as JSON | `-PsessionId=...` |
| `opencode-import` | Import session data from JSON file or URL | `-PimportFile=...` (required) |
| `opencode-plugin` | Install plugin and update config | `-Pmodule=...` (required) |
| `opencode-serve` | Start a headless opencode server | `-Pport=...`, `-Phostname=...`, `-Pmdns`, `-PmdnsDomain=...` |
| `opencode-web` | Start server and open web interface | `-Pport=...`, `-Phostname=...` |
| `opencode-completion` | Generate shell completion script | -- |
| `opencode-acp` | Start ACP (Agent Client Protocol) server | -- |
| `opencode-mcp` | Manage MCP servers | -- |
| `opencode-debug` | Debugging and troubleshooting tools | -- |
| `opencode-providers` | Manage AI providers and credentials | -- |
| `opencode-agent` | Manage agents | -- |
| `opencode-uninstall` | Uninstall opencode and remove all files | -- |
| `opencode-stats` | Show token usage and cost statistics | -- |
| `opencode-github` | Manage GitHub agent | -- |
| `opencode-session` | Manage sessions | -- |
| `opencode-db` | Database tools | -- |
| `opencode-version` | Show opencode version | -- |

## Key Classes

| Class | Description |
|---|---|
| `OpencodePlugin` | Settings plugin entry point. Registers all tasks on the root project. |
| `OpencodeBaseTask` | Abstract base. Extends `Exec` with stdin from `/dev/null`. |
| `OpencodeRunTask` | Extends `OpencodeBaseTask`. Builds the `opencode run` command from typed properties. |
| `OpencodeResumeTask` | Extends `OpencodeBaseTask`. Runs `opencode --continue` or `--session`. |
| `OpencodePrTask` | Extends `OpencodeBaseTask`. Runs `opencode pr <number>`. |
| `OpencodeAttachTask` | Extends `OpencodeBaseTask`. Runs `opencode attach <url>`. |
| `OpencodeUpgradeTask` | Extends `OpencodeBaseTask`. Runs `opencode upgrade [target]`. |
| `OpencodeModelsTask` | Extends `OpencodeBaseTask`. Runs `opencode models [provider]`. |
| `OpencodeExportTask` | Extends `OpencodeBaseTask`. Runs `opencode export [sessionID]`. |
| `OpencodeImportTask` | Extends `OpencodeBaseTask`. Runs `opencode import <file>`. |
| `OpencodePluginInstallTask` | Extends `OpencodeBaseTask`. Runs `opencode plugin <module>`. |
| `OpencodeServeTask` | Extends `OpencodeBaseTask`. Runs `opencode serve` with server options. |
| `OpencodeWebTask` | Extends `OpencodeBaseTask`. Runs `opencode web` with server options. |
| `OpencodeCompletionTask` | Extends `OpencodeBaseTask`. Runs `opencode completion`. |
| `OpencodeAcpTask` | Extends `OpencodeBaseTask`. Runs `opencode acp`. |
| `OpencodeMcpTask` | Extends `OpencodeBaseTask`. Runs `opencode mcp`. |
| `OpencodeDebugTask` | Extends `OpencodeBaseTask`. Runs `opencode debug`. |
| `OpencodeProvidersTask` | Extends `OpencodeBaseTask`. Runs `opencode providers`. |
| `OpencodeAgentTask` | Extends `OpencodeBaseTask`. Runs `opencode agent`. |
| `OpencodeUninstallTask` | Extends `OpencodeBaseTask`. Runs `opencode uninstall`. |
| `OpencodeStatsTask` | Extends `OpencodeBaseTask`. Runs `opencode stats`. |
| `OpencodeGithubTask` | Extends `OpencodeBaseTask`. Runs `opencode github`. |
| `OpencodeSessionTask` | Extends `OpencodeBaseTask`. Runs `opencode session`. |
| `OpencodeDbTask` | Extends `OpencodeBaseTask`. Runs `opencode db`. |
| `OpencodeVersionTask` | Extends `OpencodeBaseTask`. Runs `opencode --version`. |

## Dependencies

- `compileOnly(gradleApi())`

No runtime dependencies beyond Gradle itself. Requires the `opencode` CLI to be installed and on PATH.
