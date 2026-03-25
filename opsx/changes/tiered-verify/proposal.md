# Proposal: tiered-verify

## Summary

Overhaul the `opsx-exec` engine with four improvements: tiered build verification (build/compile/off modes), streamed prefixed output from agent subprocesses, configurable timeouts on all subprocess calls, and a DAG scheduler that runs independent tasks in parallel with a live status file.

## Motivation

1. **Double verification is wasteful.** ExecTask runs `opsx-verify` then `TaskLifecycle` runs `./gradlew build` — repeats work. For projects with slow tests, this doubles wait time per task.

2. **Agent output is invisible.** `AgentRunner.run()` captures stdout/stderr into `StringBuilder` and returns them only after the process finishes. For a 5-minute agent session, you see nothing. Looks frozen.

3. **Subprocess calls can hang forever.** `runGradleTaskSafe("opsx-verify")` calls `proc.waitFor()` with no timeout. If a Gradle daemon locks, the entire chain hangs.

4. **Sequential execution of independent tasks.** Tasks with no dependency between them run one after another. A 6-task proposal with 3 independent pairs wastes 2x the time.

5. **No visibility into execution progress.** No way for external tools (parent agent, TUI, monitoring) to know what's running, what's done, what failed.

6. **`--no-daemon` kills performance.** Every subprocess spawns a cold JVM. Removing it lets the daemon stay warm between exec tasks.

## Scope

**In scope:**
- `BuildVerifier` class in `:exec` with `VerifyMode` enum (BUILD/COMPILE/OFF) and `VerifyResult`
- Streaming output callback on `AgentRunner.run()` — line-by-line via `(String) -> Unit`
- `TaskLogger` for prefixed log output: `[task-id] │ line`
- Heartbeat thread that logs every 30s during long agent operations
- Configurable timeouts on `runGradleTask` and `runGradleTaskSafe` with `destroyForcibly()` on expiry
- `LevelScheduler` in `:exec` — topological sort into parallel levels using existing `DependencyGraph`
- Parallel level execution in `executeTaskChain` via `ExecutorService` (gated by `-Pparallel=true`)
- Live status file `.opsx/exec/status.json` updated on each state transition
- Remove `--no-daemon` from all subprocess calls
- Wire everything into ExecTask
- Unit tests for all new classes
