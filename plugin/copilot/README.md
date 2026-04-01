# plugin-copilot

Settings plugin that wraps the GitHub Copilot CLI as Gradle tasks.

## What it does

Registers Gradle tasks that map 1:1 to the `copilot` CLI. Every flag, subcommand, and option is exposed as a typed Gradle task property. Tasks run GitHub Copilot in non-interactive print mode (`copilot -p`), with stdin closed and live output streaming. Usable from CI pipelines, build scripts, and agent orchestration.

## Why it exists

Lets you invoke GitHub Copilot from any Gradle build without shell scripts. The typed task properties provide IDE autocomplete, validation, and composability with other Gradle tasks. Because it is a Settings plugin, it applies once at the root and is available in all subprojects and included builds.

## Plugin ID

```
zone.clanker.copilot
```

**CLI_VERSION:** `1.0.14`

## Usage

```kotlin
// settings.gradle.kts
plugins {
    id("zone.clanker.copilot") version "<version>"
}
```

```bash
./gradlew copilot-run -Pprompt="Refactor the auth module" -PcopilotModel=gpt-4o
```

## Tasks

### copilot-run

Run GitHub Copilot in non-interactive print mode. This is the primary task with full CLI flag coverage.

**String/value flags:**

| Flag | Type | Gradle Property | CLI Equivalent |
|---|---|---|---|
| `prompt` | `String` (required) | `-Pprompt=...` | `copilot -p <prompt>` |
| `copilotModel` | `String` | `-PcopilotModel=...` | `--model` |
| `effort` | `String` | `-Peffort=...` | `--reasoning-effort` |
| `agent` | `String` | `-Pagent=...` | `--agent` |
| `outputFormat` | `String` | `-PoutputFormat=...` | `--output-format` |
| `stream` | `String` | `-Pstream=...` | `--stream` |
| `logLevel` | `String` | `-PlogLevel=...` | `--log-level` |
| `logDir` | `String` | `-PlogDir=...` | `--log-dir` |
| `configDir` | `String` | `-PconfigDir=...` | `--config-dir` |
| `maxAutopilotContinues` | `String` | `-PmaxAutopilotContinues=...` | `--max-autopilot-continues` |
| `bashEnv` | `String` | `-PbashEnv=...` | `--bash-env` |
| `mouse` | `String` | `-Pmouse=...` | `--mouse` |
| `share` | `String` | `-Pshare=...` | `--share` |
| `interactive` | `String` | `-Pinteractive=...` | `-i` |

**Boolean flags:**

| Flag | Gradle Property | CLI Equivalent |
|---|---|---|
| `silent` | `-Psilent` | `-s` |
| `noAskUser` | `-PnoAskUser` | `--no-ask-user` |
| `autopilot` | `-Pautopilot` | `--autopilot` |
| `allowAll` | `-PallowAll` | `--allow-all` |
| `allowAllTools` | `-PallowAllTools` | `--allow-all-tools` |
| `allowAllPaths` | `-PallowAllPaths` | `--allow-all-paths` |
| `allowAllUrls` | `-PallowAllUrls` | `--allow-all-urls` |
| `yolo` | `-Pyolo` | `--yolo` |
| `acp` | `-Pacp` | `--acp` |
| `banner` | `-Pbanner` | `--banner` |
| `experimental` | `-Pexperimental` | `--experimental` |
| `noExperimental` | `-PnoExperimental` | `--no-experimental` |
| `noAutoUpdate` | `-PnoAutoUpdate` | `--no-auto-update` |
| `noColor` | `-PnoColor` | `--no-color` |
| `noCustomInstructions` | `-PnoCustomInstructions` | `--no-custom-instructions` |
| `noBashEnv` | `-PnoBashEnv` | `--no-bash-env` |
| `noMouse` | `-PnoMouse` | `--no-mouse` |
| `plainDiff` | `-PplainDiff` | `--plain-diff` |
| `screenReader` | `-PscreenReader` | `--screen-reader` |
| `shareGist` | `-PshareGist` | `--share-gist` |
| `disableBuiltinMcps` | `-PdisableBuiltinMcps` | `--disable-builtin-mcps` |
| `disallowTempDir` | `-PdisallowTempDir` | `--disallow-temp-dir` |
| `enableAllGithubMcpTools` | `-PenableAllGithubMcpTools` | `--enable-all-github-mcp-tools` |
| `enableReasoningSummaries` | `-PenableReasoningSummaries` | `--enable-reasoning-summaries` |

**List flags (repeatable):**

| Flag | CLI Equivalent |
|---|---|
| `addDir` | `--add-dir` |
| `addGithubMcpTool` | `--add-github-mcp-tool` |
| `addGithubMcpToolset` | `--add-github-mcp-toolset` |
| `additionalMcpConfig` | `--additional-mcp-config` |
| `allowTool` | `--allow-tool` |
| `allowUrl` | `--allow-url` |
| `availableTools` | `--available-tools` |
| `denyTool` | `--deny-tool` |
| `denyUrl` | `--deny-url` |
| `disableMcpServer` | `--disable-mcp-server` |
| `excludedTools` | `--excluded-tools` |
| `pluginDir` | `--plugin-dir` |
| `secretEnvVars` | `--secret-env-vars` |

Note: `yolo` takes precedence over `allowAll`, which takes precedence over the individual `allowAllTools`/`allowAllPaths`/`allowAllUrls` flags.

### Other tasks

| Task | Description | Key Flags |
|---|---|---|
| `copilot-resume` | Resume a Copilot conversation | `-PsessionId=...` (omit for `--continue`) |
| `copilot-login` | Authenticate with GitHub Copilot | -- |
| `copilot-version` | Show CLI version | -- |
| `copilot-update` | Download latest CLI version | -- |
| `copilot-init` | Initialize Copilot instructions for a repo | -- |
| `copilot-plugin` | Manage Copilot plugins | -- |
| `copilot-help` | Display help information | `-Ptopic=...` |

## Key Classes

| Class | Description |
|---|---|
| `CopilotPlugin` | Settings plugin entry point. Registers all tasks on the root project. |
| `CopilotBaseTask` | Abstract base. Extends `Exec` with stdin closed and live output streaming. |
| `CopilotRunTask` | Extends `CopilotBaseTask`. Builds the `copilot -p` command from typed properties. |
| `CopilotResumeTask` | Extends `CopilotBaseTask`. Runs `copilot --resume` or `copilot --continue`. |
| `CopilotLoginTask` | Extends `CopilotBaseTask`. Runs `copilot login`. |
| `CopilotVersionTask` | Extends `CopilotBaseTask`. Runs `copilot --version`. |
| `CopilotUpdateTask` | Extends `CopilotBaseTask`. Runs `copilot update`. |
| `CopilotInitTask` | Extends `CopilotBaseTask`. Runs `copilot init`. |
| `CopilotPluginManageTask` | Extends `CopilotBaseTask`. Runs `copilot plugin`. |
| `CopilotHelpTask` | Extends `CopilotBaseTask`. Runs `copilot help [topic]`. |

## Dependencies

- `compileOnly(gradleApi())`

No runtime dependencies beyond Gradle itself. Requires the `copilot` CLI to be installed and on PATH.
