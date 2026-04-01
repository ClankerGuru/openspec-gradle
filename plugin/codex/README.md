# plugin-codex

Settings plugin that wraps the OpenAI Codex CLI as Gradle tasks.

## What it does

Registers Gradle tasks that map 1:1 to the `codex` CLI. Every flag, subcommand, and option is exposed as a typed Gradle task property. Tasks run Codex in non-interactive exec mode (`codex exec`), with stdin from `/dev/null`. Usable from CI pipelines, build scripts, and agent orchestration.

## Why it exists

Lets you invoke OpenAI Codex from any Gradle build without shell scripts. The typed task properties provide IDE autocomplete, validation, and composability with other Gradle tasks. Because it is a Settings plugin, it applies once at the root and is available in all subprojects and included builds.

## Plugin ID

```
zone.clanker.codex
```

**CLI_VERSION:** `0.117.0`

## Usage

```kotlin
// settings.gradle.kts
plugins {
    id("zone.clanker.codex") version "<version>"
}
```

```bash
./gradlew codex-exec -Pprompt="Refactor the auth module" -PcodexModel=o4-mini
```

## Tasks

### codex-exec

Run Codex CLI in non-interactive exec mode. This is the primary task with full CLI flag coverage.

| Flag | Type | Gradle Property | CLI Equivalent |
|---|---|---|---|
| `prompt` | `String` (required) | `-Pprompt=...` | `codex exec <prompt>` |
| `codexModel` | `String` | `-PcodexModel=...` | `--model` |
| `config` | `List<String>` | -- | `--config` (repeatable) |
| `enable` | `List<String>` | -- | `--enable` (repeatable) |
| `disable` | `List<String>` | -- | `--disable` (repeatable) |
| `remote` | `String` | `-Premote=...` | `--remote` |
| `remoteAuthTokenEnv` | `String` | `-PremoteAuthTokenEnv=...` | `--remote-auth-token-env` |
| `image` | `List<String>` | -- | `--image` (repeatable) |
| `oss` | `Boolean` | `-Poss` | `--oss` |
| `localProvider` | `String` | `-PlocalProvider=...` | `--local-provider` |
| `profile` | `String` | `-Pprofile=...` | `--profile` |
| `sandbox` | `String` | `-Psandbox=...` | `--sandbox` |
| `askForApproval` | `String` | `-PaskForApproval=...` | `--ask-for-approval` |
| `fullAuto` | `Boolean` | `-PfullAuto` | `--full-auto` |
| `dangerouslyBypassApprovalsAndSandbox` | `Boolean` | `-PdangerouslyBypassApprovalsAndSandbox` | `--dangerously-bypass-approvals-and-sandbox` |
| `cd` | `String` | `-Pcd=...` | `--cd` |
| `search` | `Boolean` | `-Psearch` | `--search` |
| `addDir` | `List<String>` | -- | `--add-dir` (repeatable) |

Note: `dangerouslyBypassApprovalsAndSandbox` takes precedence over `sandbox`, `askForApproval`, and `fullAuto`.

### Other tasks

| Task | Description | Key Flags |
|---|---|---|
| `codex-review` | Run a non-interactive code review | -- |
| `codex-resume` | Resume a previous interactive session | `-Plast` |
| `codex-fork` | Fork a previous interactive session | `-Plast` |
| `codex-apply` | Apply the latest diff as `git apply` | -- |
| `codex-login` | Manage Codex login | -- |
| `codex-logout` | Remove stored auth credentials | -- |
| `codex-mcp` | Manage external MCP servers | -- |
| `codex-mcp-server` | Start Codex as an MCP server (stdio) | -- |
| `codex-app` | Launch the Codex desktop app | -- |
| `codex-app-server` | Run the Codex app server | -- |
| `codex-completion` | Generate shell completion scripts | `-Pshell=...` |
| `codex-sandbox` | Run commands within a Codex sandbox | -- |
| `codex-debug` | Codex debugging tools | -- |
| `codex-cloud` | Browse tasks from Codex Cloud, apply locally | -- |
| `codex-features` | Inspect Codex feature flags | -- |
| `codex-version` | Show Codex CLI version | -- |

## Key Classes

| Class | Description |
|---|---|
| `CodexPlugin` | Settings plugin entry point. Registers all tasks on the root project. |
| `CodexBaseTask` | Abstract base. Extends `Exec` with stdin from `/dev/null`. |
| `CodexExecTask` | Extends `CodexBaseTask`. Builds the `codex exec` command from typed properties. |
| `CodexReviewTask` | Extends `CodexBaseTask`. Runs `codex review`. |
| `CodexResumeTask` | Extends `CodexBaseTask`. Runs `codex resume [--last]`. |
| `CodexForkTask` | Extends `CodexBaseTask`. Runs `codex fork [--last]`. |
| `CodexApplyTask` | Extends `CodexBaseTask`. Runs `codex apply`. |
| `CodexLoginTask` | Extends `CodexBaseTask`. Runs `codex login`. |
| `CodexLogoutTask` | Extends `CodexBaseTask`. Runs `codex logout`. |
| `CodexMcpTask` | Extends `CodexBaseTask`. Runs `codex mcp`. |
| `CodexMcpServerTask` | Extends `CodexBaseTask`. Runs `codex mcp-server`. |
| `CodexAppTask` | Extends `CodexBaseTask`. Runs `codex app`. |
| `CodexAppServerTask` | Extends `CodexBaseTask`. Runs `codex app-server`. |
| `CodexCompletionTask` | Extends `CodexBaseTask`. Runs `codex completion [shell]`. |
| `CodexSandboxTask` | Extends `CodexBaseTask`. Runs `codex sandbox`. |
| `CodexDebugTask` | Extends `CodexBaseTask`. Runs `codex debug`. |
| `CodexCloudTask` | Extends `CodexBaseTask`. Runs `codex cloud`. |
| `CodexFeaturesTask` | Extends `CodexBaseTask`. Runs `codex features`. |
| `CodexVersionTask` | Extends `CodexBaseTask`. Runs `codex --version`. |

## Dependencies

- `compileOnly(gradleApi())`

No runtime dependencies beyond Gradle itself. Requires the `codex` CLI to be installed and on PATH.
