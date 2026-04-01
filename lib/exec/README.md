# exec

Execution engine that spawns AI agent CLIs, verifies their output, retries on failure, and reports progress.

## What it does

Orchestrates the full lifecycle of running AI coding agents against task specs: build the CLI command, spawn the process, stream output, detect progress steps, verify the build, retry with cycle detection, schedule tasks across dependency levels for parallel execution, and write live dashboards.

## Why it exists

Running an agent is more than `Process.start()`. The exec module handles timeouts, heartbeats, structured logging, build verification after each agent run, cycle detection to avoid infinite retry loops, parallel scheduling respecting task dependencies, and real-time status reporting via JSON and Markdown dashboards.

## Exec Pipeline

```
prompt --> AgentRunner.run() --> BuildVerifier.verify() --> CycleDetector.recordAndCheck()
   |            |                      |                          |
   |       stdout/stderr          pass/fail                 cycle? --> abort
   |       heartbeats                  |                          |
   v            v                      v                     no cycle --> retry
SpecParser  AgentLogWriter       TieredVerifier          LevelScheduler
(parse spec)  (log steps)        (build/compile/off)     (parallel levels)
```

1. **Parse** -- `SpecParser` reads task spec files with title, agent, max-retries, verify flag, and prompt text.
2. **Schedule** -- `LevelScheduler` groups tasks into execution levels using Kahn's algorithm. Tasks within a level run in parallel; levels run sequentially.
3. **Run** -- `AgentRunner` spawns the agent CLI (`copilot`, `claude`, `codex`, or `opencode`) with the prompt in non-interactive mode. Streams stdout/stderr, detects meaningful steps (file writes, build results, errors), and sends heartbeats.
4. **Log** -- `AgentLogWriter` creates per-task Markdown log files with headers (agent, status, elapsed, heartbeat) and appends timestamped step entries. Supports question/answer flow between agent and dashboard.
5. **Verify** -- `BuildVerifier` runs `./gradlew build` (or `assemble`, or skips) after each agent run. Uses Gradle's incremental build for speed. `TieredVerifier` wraps this with mode selection.
6. **Detect cycles** -- `CycleDetector` hashes error output (normalizing timestamps and line numbers) and checks whether the same error pattern has appeared in a non-consecutive previous attempt.
7. **Report** -- `ExecStatus` writes live JSON to `.opsx/exec/status.json`. `DashboardReader` scans agent log files and renders Markdown dashboards (top-level and per-proposal). `ExecStatusReader` renders a CLI progress table.

## Per-Proposal Directory Structure

```
.opsx/exec/
  status.json                  # Live JSON status (ExecStatus)
  dashboard.md                 # Top-level dashboard (DashboardReader)
  {proposal}/
    dashboard.md               # Per-proposal dashboard
    {taskCode}.md              # Per-task agent log (AgentLogWriter)
    answers/
      {taskCode}.md            # Answer files for agent questions
```

## Key Classes

| Class | Role |
|---|---|
| `AgentRunner` | Spawns agent CLI processes, streams output, detects steps, manages timeouts and heartbeats. Supports `github-copilot`, `claude`, `codex`, and `opencode`. |
| `AgentRunner.AgentResult` | Return value with exit code, stdout, stderr, duration, and PID. |
| `AgentLogWriter` | Writes and updates per-task Markdown log files. Creates initial headers, appends steps, updates heartbeat/elapsed, handles question/answer polling, and marks completion. |
| `DashboardReader` | Scans `.opsx/exec/` for agent log files, parses their Markdown format into `AgentStatus` snapshots, detects stale heartbeats, and renders top-level and per-proposal Markdown dashboards. |
| `AgentStatus` | Snapshot of a single agent's state: task code, agent name, status, elapsed time, last step, question text. |
| `LevelScheduler` | Groups tasks into parallel execution levels using Kahn's algorithm (BFS topological sort). Each level's tasks can run concurrently; levels execute in order. |
| `CycleDetector` | Tracks SHA-256 hashes of normalized error output across retry attempts. Detects when an agent is cycling between the same errors. |
| `BuildVerifier` | Runs Gradle build verification after agent changes. Modes: `BUILD` (full incremental), `COMPILE` (assemble only), `OFF` (skip). Supports module-scoped verification. |
| `VerifyMode` | Enum: `BUILD`, `COMPILE`, `OFF`. |
| `VerifyResult` | Result of a verification step with success flag, mode, duration, and message. |
| `TieredVerifier` | Wrapper around `BuildVerifier` referenced by the exec pipeline. |
| `ExecStatus` | Serializable live execution status written to `status.json`. Tracks proposal, current level, per-task status, parallelism, and verify mode. Atomic write via temp file + rename. |
| `TaskExecStatus` | Per-task status within an exec run: `PENDING`, `RUNNING`, `DONE`, `FAILED`, `CANCELLED`. Includes attempt count, agent, duration, PID, and error. |
| `ExecStatusReader` | Reads `status.json` and renders a CLI progress table with progress bar, status icons, and timing. |
| `SpecParser` | Parses task spec Markdown files into `TaskSpec` (title, agent, max-retries, verify flag, prompt). |
| `SpecParser.TaskSpec` | Parsed task specification. |
| `TaskLogger` | Prefixed logger for exec output. Every line gets a `[taskId]` prefix for visual distinction. |

## Dependencies

- `:lib:core` (api)
- `kotlinx-serialization-json`
