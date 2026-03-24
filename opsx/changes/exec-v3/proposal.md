# Exec Engine v3 — Streaming, Timeouts, DAG Scheduler

## Problem

The exec engine (`opsx-exec`) has three critical issues discovered during real-world usage:

1. **Invisible execution.** When an agent spawns a subprocess (agent CLI or `./gradlew build`), there's zero output until it finishes. For 5-10 minute operations, the parent process appears frozen. You can't tell if it's your current agent or a subprocess doing the work.

2. **Hangs with no recovery.** `runGradleTask()` and `runGradleTaskSafe()` call `proc.waitFor()` with no timeout. If a UI test hangs or a Gradle daemon locks, the entire chain hangs forever.

3. **Sequential only.** Independent tasks run one by one. A proposal with 6 tasks where only 2-3 have dependencies wastes significant time waiting.

## Solution

### 1. Streamed, Prefixed Output

Every log line from a subprocess gets prefixed with the task ID:

```
[cache-1] ⏳ Spawning copilot... (attempt 1/3)
[cache-1] │ Reading .opsx/context.md...
[cache-1] │ Creating CacheInterface.kt...
[cache-1] ✓ Agent completed (47s)
[cache-1] ⏳ Running build gate...
[cache-1] ✓ Build passed — DONE (1m12s)
```

Implementation: Replace `StringBuilder` collection in `AgentRunner.run()` with line-by-line streaming via a callback. The callback receives each line as it arrives and logs it with the task prefix. Output is still captured for the report, but streamed live.

### 2. Timeouts Everywhere

- `AgentRunner.run()` — already has timeout (default 600s) ✓
- `runGradleTask()` — add configurable timeout (default 10 min), `destroyForcibly()` on expiry
- `runGradleTaskSafe()` — same timeout behavior
- Background heartbeat thread: logs `[task-id] ⏳ Still running... 2m30s` every 30s during long operations

### 3. DAG Scheduler with Level-Based Parallel Execution

Build the dependency graph from `tasks.md`, topologically sort into levels, execute each level as a batch:

```
Level 0: [A]          ← run, wait
Level 1: [B, C]       ← run in parallel, wait for all
Level 2: [D]          ← run, wait
```

Rules:
- Each task runs exactly once, even if multiple tasks depend on it
- Tasks within a level are independent and run concurrently
- Re-sync context between levels (not between parallel tasks within a level)
- Fail-fast: if any task in a level fails, cancel remaining parallel tasks and stop
- Parallel tasks within a level share the same context snapshot

### 4. Live Status File

Write `.opsx/exec/status.json` during execution:

```json
{
  "proposal": "add-caching",
  "startedAt": "2026-03-24T22:50:00",
  "currentLevel": 1,
  "tasks": {
    "cache-1": {"status": "DONE", "duration": "1m12s"},
    "cache-2": {"status": "RUNNING", "attempt": 1, "agent": "copilot", "startedAt": "..."},
    "cache-3": {"status": "RUNNING", "attempt": 1, "agent": "copilot", "startedAt": "..."},
    "cache-4": {"status": "PENDING"}
  }
}
```

Any external tool (parent agent, TUI, monitoring) can poll this file.

## Design Decisions

- **Parallel execution uses threads, not coroutines** — Gradle tasks run on JVM threads, and we need `ProcessBuilder` for subprocesses. A simple `ExecutorService` with a fixed thread pool is sufficient.
- **Level-based, not eager** — We could start a task the instant all its deps complete (eager scheduling). But level-based is simpler, re-syncs at level boundaries are natural, and the perf difference is small for typical proposal sizes (5-15 tasks).
- **Streaming callback, not logger injection** — `AgentRunner` receives a `(String) -> Unit` callback for output lines. The caller provides the prefixed logger. This keeps AgentRunner testable and decoupled from Gradle's logging.
- **Status file is best-effort** — Written on state changes, not locked. Race conditions between parallel writers are handled by writing per-task status atomically.

## Constraints

- Must not break existing single-task `opsx-exec -Pprompt="..."` usage
- Must not break `--run` flag on `TaskItemTask`
- Timeouts must be configurable via `-P` flags
- Parallel execution is opt-in initially (`-Pparallel=true`) — sequential remains default until battle-tested
