# opsx

Gradle tasks for the OPSX proposal workflow, AI agent execution, and agent file generation.

## What it does

Provides the core workflow engine for OPSX: creating change proposals, tracking task progress with a state machine, executing AI agents with retry and verification, generating agent skill files, and maintaining the `.opsx/` output directory. This module implements the full proposal lifecycle from initial creation through implementation to archival.

## Why it exists

AI agents need a structured workflow to make multi-step changes to a codebase. Raw prompts lack context, verification, and retry logic. This module provides a spec-driven execution model where proposals break down into discrete tasks, each task has machine-checkable assertions, and the execution engine handles agent spawning, build verification, cycle detection, and parallel scheduling.

## Tasks

### Workflow

| Task | Gradle Name | Description | Key Properties |
|------|-------------|-------------|----------------|
| `ProposeTask` | `opsx-propose` | Creates a new change proposal directory at `opsx/changes/<name>/` with template files: `proposal.md` (what and why), `design.md` (how), `tasks.md` (implementation steps), and `.opsx.yaml` (metadata). Generates a task code prefix from the change name. | `--name=<kebab-case-name>` (required, via `@Option`) |
| `ApplyTask` | `opsx-apply` | Shows progress on a change proposal. Parses `tasks.md` to count total/done tasks. If all tasks are complete, prompts for archival. Auto-selects the change when only one active proposal exists. | `--name=<change-name>` (optional, via `@Option`) |
| `ArchiveTask` | `opsx-archive` | Moves a completed proposal to `opsx/changes/archive/<date>-<name>/`. Adds the current date as a prefix to the archived directory name. | `--name=<change-name>` (required, via `@Option`) |
| `StatusTask` | `opsx-status` | Proposal dashboard. Renders progress bars, lists active and completed proposals with task status icons, shows dependency cycle warnings, and runs task reconciliation to detect stale symbol/file references. Integrates with the live exec status dashboard when `opsx-exec` is running. | `proposal: Property<String>` (optional, `--proposal=<name>`), `outputFile: RegularFileProperty` |
| `TaskItemTask` | `opsx-<code>` | Dynamically registered per-task Gradle tasks (e.g., `opsx-ttd-1`). View task status, update status with a state machine (`todo -> progress -> done/blocked`), delegate to the exec engine with `--run`, or force-skip verification with `--force` (interactive only). Validates state transitions and dependency ordering. | `taskCode: Property<String>`, `proposalName: Property<String>`, `setStatus: Property<String>` (optional, `--set=todo/progress/done/blocked`), `runTask: Property<String>` (optional, `--run`), `force: Property<String>` (optional, `--force`) |
| `TaskLifecycle` | (not a task) | Shared completion pipeline used by `TaskItemTask` and `ExecTask`. Pipeline: run verify assertions, mark DONE, propagate parent completion, sync task skill files, reconcile remaining tasks against the codebase. Resolves the verify command from `zone.clanker.opsx.verifyCommand` (default: `assemble`). | N/A (object, not a Gradle task) |
| `AssertionRunner` | (not a task) | Runs machine-checkable verify assertions: `symbol-exists`, `symbol-not-in`, `file-exists`, `file-changed`, `build-passes`. The build-passes assertion runs the configured Gradle command with a 10-minute timeout. | N/A (object, not a Gradle task) |

### Execution

| Task | Gradle Name | Description | Key Properties |
|------|-------------|-------------|----------------|
| `ExecTask` | `opsx-exec` | Core AI agent execution engine. Spawns an external agent (copilot, claude, codex, opencode) with a prompt, verifies the output via build, and retries on failure with cycle detection. Supports three modes: inline prompt (`-Pprompt="..."`), spec file (`-Pspec=path`), and task chain (`-Ptask=code1,code2`). Task chain mode uses `LevelScheduler` for DAG-based parallel execution. Writes per-attempt output, agent logs, and execution summaries to `.opsx/exec/`. | `prompt: Property<String>` (optional), `spec: Property<String>` (optional), `agent: Property<String>` (optional, override), `maxRetries: Property<Int>` (optional, default: 3), `verify: Property<Boolean>` (optional, default: true), `execTimeout: Property<Int>` (optional, default: 600s), `taskCodes: Property<String>` (optional, comma-separated), `verifyMode: Property<String>` (optional, `build`/`compile`/`off`), `parallel: Property<Boolean>` (optional), `parallelThreads: Property<Int>` (optional, default: 4) |
| `DashboardTask` | `opsx-dashboard` | Live dashboard for running OPSX agents. Shows active agents grouped by proposal, pending questions, running/done/failed counts. Writes `dashboard.md` files at both the top level and per-proposal. | (no input properties) |

### Maintenance

| Task | Gradle Name | Description | Key Properties |
|------|-------------|-------------|----------------|
| `SyncTask` | `opsx-sync` | Agent file generator. Generates skill files and instructions for configured AI agents. Runs task reconciliation to detect stale symbol/file references. Installs files to the project root and cleans stale/deselected agent artifacts. Ensures global gitignore patterns are in place. | `tools: ListProperty<String>`, `outputDir: Property<File>` |
| `CleanTask` | `opsx-clean` | Removes all generated OpenSpec files. Cleans skill files and instructions for all supported agents (not just configured ones). Removes the `.opsx/` directory. Preserves `opsx/changes/` (committed proposal work). | `tools: ListProperty<String>` |
| `InstallGlobalTask` | `opsx-install` | Installs the plugin globally via a Gradle init script at `~/.gradle/init.d/openspec.init.gradle.kts`. Sets up the settings plugin, injects detekt/ktlint classpath dependencies, adds `zone.clanker.opsx.agents` to `~/.gradle/gradle.properties`, and ensures global gitignore patterns. | `pluginVersion: Property<String>`, `tools: ListProperty<String>` |
| `OpsxLinkTask` | `opsx-link` | Creates symlinks from the project directory to `~/.config/opsx/` for tool config directories (`.claude`, `.github`, `.agents`, `.opencode`). Handles existing symlinks, non-symlink conflicts, and Windows developer mode requirements. | (no input properties) |

## Proposal Lifecycle

```
propose --> apply --> archive
```

1. **Propose** (`opsx-propose --name=my-feature`): Creates `opsx/changes/my-feature/` with `proposal.md`, `design.md`, `tasks.md`, and `.opsx.yaml`. The `tasks.md` file contains a checklist with task codes (e.g., `mf-1`, `mf-2`).

2. **Apply** (`opsx-apply --name=my-feature`): Shows current progress. Each task item becomes a dynamically registered Gradle task (`opsx-mf-1`, `opsx-mf-2`, etc.) with status management. Tasks follow a state machine:
   - `TODO` -> `IN_PROGRESS` -> `DONE` or `BLOCKED`
   - `DONE` -> `TODO` (reset)
   - `BLOCKED` -> `TODO` (reset)
   - Marking `DONE` triggers the `TaskLifecycle` pipeline: verify assertions run, task is marked complete, parent tasks auto-complete when all children are done, skill files regenerate, and remaining tasks are reconciled.

3. **Archive** (`opsx-archive --name=my-feature`): Moves the completed proposal to `opsx/changes/archive/<date>-my-feature/`.

## ExecTask Detail

The execution engine has three operating modes:

**Inline prompt**: `./gradlew opsx-exec -Pprompt="Refactor X to Y"` -- spawns a single agent run with the given prompt.

**Spec file**: `./gradlew opsx-exec -Pspec=path/to/task.md` -- parses a task spec file for agent, retry, and verify overrides. If the path is a directory, runs all `task-*.md` files as a batch.

**Task chain**: `./gradlew opsx-exec -Ptask=mf-1,mf-2,mf-3` -- executes proposal tasks in dependency order. Uses `LevelScheduler` to group tasks into DAG levels. Tasks within the same level can run in parallel (`-Pparallel=true -PparallelThreads=4`). Each task goes through:
1. Validate the task code exists in a proposal
2. Mark `IN_PROGRESS` in `tasks.md`
3. Build prompt with project context and previous attempt logs
4. Spawn the resolved agent with a timeout
5. Verify via `BuildVerifier` (mode: `build`, `compile`, or `off`)
6. On success: run `TaskLifecycle.onTaskCompleted` (assertions, mark DONE, propagate)
7. On failure: append attempt log, retry with escalating context, or mark `BLOCKED`

Retry logic includes `CycleDetector` to abort early when repeated attempts produce the same error. Per-task metadata in `tasks.md` can override retries, cooldown, and agent selection.

Live execution status is written to `.opsx/exec/status.json` and can be monitored via `opsx-status` or `opsx-dashboard`.

## Key Classes

- **Workflow** (`zone.clanker.opsx.tasks.workflow`): `ProposeTask`, `ApplyTask`, `ArchiveTask`, `StatusTask`, `TaskItemTask`, `TaskLifecycle`, `AssertionRunner`
- **Execution** (`zone.clanker.opsx.tasks.execution`): `ExecTask`, `DashboardTask`, `SyncTask`, `CleanTask`, `InstallGlobalTask`, `OpsxLinkTask`

## Dependencies

- `:lib:core` -- shared data types (`TaskItem`, `TaskStatus`, `TaskParser`, `TaskWriter`, `ProposalScanner`, `Proposal`, `DependencyGraph`, `TaskCodeGenerator`, `VerifyAssertion`, `OpenSpecExtension`)
- `:lib:generators` -- agent file generators (`SkillGenerator`, `InstructionsGenerator`, `TaskCommandGenerator`, `TaskReconciler`, `ToolAdapterRegistry`, `AgentCleaner`, `GlobalGitignore`, `TemplateRegistry`)
- `:lib:exec` -- agent execution infrastructure (`AgentRunner`, `AgentLogWriter`, `BuildVerifier`, `CycleDetector`, `DashboardReader`, `SpecParser`, `ExecStatus`, `LevelScheduler`, `TaskExecStatus`, `TaskLogger`, `VerifyMode`, `ExecStatusReader`)
- Gradle API (compile-only)
