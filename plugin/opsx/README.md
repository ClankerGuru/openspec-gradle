# plugin-opsx

Settings plugin that provides the workflow engine: proposals, task execution, agent sync, and dashboard.

## What it does

Registers the `opsx-*` workflow and execution tasks on the root project. Manages the proposal lifecycle (propose, apply, archive, status), generates agent instruction and skill files for all configured agents, and provides `opsx-exec` -- an execution engine that spawns AI agents with prompts, verifies output, retries on failure, and supports parallel DAG-based task chains.

On startup, it scans `opsx/changes/` for proposals and dynamically registers a Gradle task for each task code found in `tasks.md` files (e.g., `opsx-aua-1`, `opsx-aua-2`).

## Why it exists

Turns Gradle into an orchestration layer for AI coding agents. Instead of manually copying prompts into a CLI, the plugin drives agents end-to-end: prompt construction, agent spawning, build verification, retry with error context, and task status tracking. The sync system generates agent-specific instruction files so each agent knows how to work with the project.

## Plugin ID

```
zone.clanker.opsx
```

**PLUGIN_VERSION:** Read at runtime from `VersionInfo.PLUGIN_VERSION` (stamped from git tag at build time).

## Usage

```kotlin
// settings.gradle.kts
plugins {
    id("zone.clanker.opsx") version "<version>"
}
```

```bash
./gradlew opsx                    # list all available tasks
./gradlew opsx-sync               # generate agent files
./gradlew opsx-propose            # create a new proposal
./gradlew opsx-exec -Pprompt="Fix the auth bug"
```

## Configuration

```properties
# gradle.properties
zone.clanker.opsx.agents=claude          # which agents to generate files for
zone.clanker.opsx.verifyCommand=build    # build gate for task completion
```

The `agents` property accepts: `claude`, `github` (alias for `github-copilot`), `codex`, `opencode`. Combine with commas: `github,claude`. Defaults to `github` if unset.

## Tasks

### Workflow

| Task | Description | Key Flags |
|---|---|---|
| `opsx-propose` | Create a new change proposal with task scaffolding | -- |
| `opsx-apply` | Mark a proposal ready to implement | -- |
| `opsx-archive` | Archive a completed proposal | -- |
| `opsx-status` | Dashboard with progress bars for active proposals | Output: `.opsx/status.md` |

### Execution

| Task | Description |
|---|---|
| `opsx-exec` | Execute an AI agent with prompt, verify, retry |
| `opsx-dashboard` | Show live execution dashboard grouped by proposal |

#### `opsx-exec` flags

| Flag | Default | Description |
|------|---------|-------------|
| `-Pprompt="..."` | *(required unless -Pspec or -Ptask)* | Inline prompt to send to the agent |
| `-Pspec=path/to/task.md` | -- | Path to a task spec file (or directory of `task-*.md` files for batch mode) |
| `-Ptask=code1,code2` | -- | Comma-separated task codes from proposals (e.g., `fvm-1,fvm-2`). Resolves dependencies via DAG scheduler. |
| `-Pagent=claude` | First from `zone.clanker.opsx.agents`, or auto-detected from PATH | Which agent CLI to use. Values: `claude`, `github`, `github-copilot`, `codex`, `opencode` |
| `-PmaxRetries=3` | `3` | How many times to retry on failure. Each retry includes error context from the previous attempt. |
| `-Pverify=true` | `true` | Whether to run the build gate after the agent completes. Set `false` to skip verification. |
| `-Popsx.verify=build` | `build` | Which verification mode to use. Values: `build` (full build), `compile` (compileKotlin only), `off` (no verification) |
| `-PexecTimeout=600` | `600` (10 min) | Timeout in seconds for each agent invocation. Agent is killed after this. |
| `-PsyncBefore=true` | `true` | Whether to run `opsx-sync` before each attempt to give the agent fresh context |
| `-Pparallel=true` | `false` | Enable parallel execution of independent tasks within the same dependency level |
| `-PparallelThreads=4` | `4` | Thread pool size for parallel execution |

**Examples:**

```bash
# Inline prompt
./gradlew opsx-exec -Pprompt="Fix the auth bug" -Pagent=claude

# Task chain from a proposal (parallel, 120s timeout)
./gradlew opsx-exec -Ptask=fvm-1,fvm-3,fvm-4,fvm-5,fvm-6 -Pparallel=true -PexecTimeout=120

# Batch mode from spec files
./gradlew opsx-exec -Pspec=opsx/changes/my-change/

# No verification, no sync
./gradlew opsx-exec -Pprompt="Add a README" -Pverify=false -PsyncBefore=false
```

### Sync and Lifecycle

| Task | Description | Key Flags |
|---|---|---|
| `opsx` | List all OpenSpec tasks (the AI tool catalog) | -- |
| `opsx-sync` | Generate all agent files (instructions, skills, task commands) | Depends on: `srcx-context`, `srcx-tree`, `srcx-deps`, `srcx-modules`, `srcx-devloop`, `srcx-arch` |
| `opsx-clean` | Remove all generated agent files | -- |
| `opsx-install` | Install globally via init script at `~/.gradle/init.d/` | -- |
| `opsx-link` | Link the current project for composite build use | -- |

### Dynamic Task Items

For each task code in `opsx/changes/*/tasks.md`, a task is registered as `opsx-<code>`:

| Flag | Description |
|---|---|
| `--set=todo\|progress\|done\|blocked` | Update task status |
| `--run` | Execute this task via the exec engine |
| `--force` | Skip build verification gate (interactive only) |

Example: `./gradlew opsx-aua-1 --set=done`

## Exec Pipeline

The `opsx-exec` task follows this pipeline:

1. **Resolve** -- Parse the prompt (inline via `-Pprompt` or from a spec file via `-Pspec`) or task codes (via `-Ptask`)
2. **Schedule** -- For task chains, `LevelScheduler` builds a DAG from task dependencies and groups into execution levels
3. **Spawn agent** -- `AgentRunner` spawns the resolved agent CLI process with the constructed prompt
4. **Verify** -- After the agent completes, `BuildVerifier` runs the configured verification command (`build`, `compileKotlin`, or `srcx-verify`)
5. **Retry** -- On failure, the prompt is augmented with error context from the previous attempt. `CycleDetector` halts if the same error repeats
6. **Status** -- Throughout execution, status is written to `.opsx/exec/status.json` for the dashboard

Parallel execution uses a thread pool (`-Pparallel=true -PparallelThreads=4`). Tasks within the same dependency level run concurrently; levels execute sequentially.

## Registered Adapters

On class initialization, the plugin registers four tool adapters with `ToolAdapterRegistry`:

- `ClaudeAdapter` -- generates `.claude/CLAUDE.md` and `.claude/skills/`
- `CopilotAdapter` -- generates `.github/copilot-instructions.md` and `.github/skills/`
- `CodexAdapter` -- generates `AGENTS.md` and `.agents/skills/`
- `OpenCodeAdapter` -- generates `AGENTS.md` and `.opencode/skills/`

## Key Classes

| Class | Location | Description |
|---|---|---|
| `OpsxPlugin` | `plugin/opsx` | Settings plugin entry point. Registers all tasks, scans proposals. |
| `ExecTask` | `task/opsx` | Execution engine: agent spawning, verification, retry, parallel chains. |
| `SyncTask` | `task/opsx` | Generates agent instruction files, skills, and task commands. |
| `CleanTask` | `task/opsx` | Removes all generated agent files. |
| `InstallGlobalTask` | `task/opsx` | Writes init script to `~/.gradle/init.d/`. |
| `DashboardTask` | `task/opsx` | Live execution dashboard. |
| `OpsxLinkTask` | `task/opsx` | Links project for composite build use. |
| `ProposeTask` | `task/opsx` | Creates proposal directory structure. |
| `ApplyTask` | `task/opsx` | Marks proposal ready for implementation. |
| `ArchiveTask` | `task/opsx` | Archives completed proposal. |
| `StatusTask` | `task/opsx` | Generates status dashboard with progress bars. |
| `TaskItemTask` | `task/opsx` | Dynamically registered per task code. Status view/update and exec delegation. |
| `TaskLifecycle` | `task/opsx` | Verify assertions and build gate logic. |
| `AssertionRunner` | `task/opsx` | Runs verify assertions (`symbol-exists`, `file-exists`, `build-passes`, etc.). |

## Dependencies

- `lib/core` -- Data models, extensions, `ProposalScanner`, `OpenSpecExtension`, `VersionInfo`
- `lib/generators` -- Agent file generators, `ToolAdapterRegistry`, skill formatting
- `lib/exec` -- `AgentRunner`, `BuildVerifier`, `CycleDetector`, `LevelScheduler`, `SpecParser`
- `lib/adapters/claude` -- `ClaudeAdapter`
- `lib/adapters/copilot` -- `CopilotAdapter`
- `lib/adapters/codex` -- `CodexAdapter`
- `lib/adapters/opencode` -- `OpenCodeAdapter`
- `task/opsx` -- Task class implementations
- `compileOnly(gradleApi())`

## Lifecycle Hooks

- `clean` depends on `opsx-clean` (removes generated files on `./gradlew clean`)
- `opsx-sync` is NOT hooked into `assemble` -- run it explicitly or via `srcx-verify`
