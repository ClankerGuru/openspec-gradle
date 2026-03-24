# Tasks — Exec Engine v3

## Streaming & Observability

- [ ] `stream-1` Add output callback to AgentRunner.run() → depends: none
  - Stream stdout/stderr line-by-line via `(String) -> Unit` callback
  - Still capture full output for reports
  - Default callback: no-op (backwards compatible)
  - Timeout heartbeat: background thread logs every 30s during agent execution

- [ ] `stream-2` Prefix all ExecTask log lines with task ID → depends: stream-1
  - Format: `[task-id] message` for all lifecycle, warn, error logs
  - Agent output lines: `[task-id] │ line`
  - Status transitions: `[task-id] ✓ DONE` / `[task-id] ✗ FAILED`
  - Clear visual distinction between parent agent output and subprocess output

- [ ] `stream-3` Add timeouts to runGradleTask and runGradleTaskSafe → depends: none
  - `proc.waitFor(timeout, TimeUnit.MINUTES)` with `destroyForcibly()` on expiry
  - Default: 10 minutes, configurable via `-PbuildTimeout=N` (seconds)
  - Log timeout as error with last captured output lines
  - Apply same timeout pattern to TaskItemTask.runBuildGate()

## Live Status

- [ ] `status-1` Write .opsx/exec/status.json during execution → depends: stream-2
  - Create on exec start, update on each state change
  - Per-task: status, attempt, agent, startedAt, duration, error summary
  - Overall: proposal name, current level, started/completed timestamps
  - Delete on completion (or keep with final status if `-PkeepStatus=true`)

## DAG Scheduler

- [ ] `dag-1` Build dependency graph from tasks.md and topologically sort into levels → depends: none
  - Reuse existing `DependencyGraph` for cycle detection
  - New `LevelScheduler` class: takes flat task list, produces `List<List<TaskItem>>` (levels)
  - Each level contains only tasks whose dependencies are all in previous levels
  - Unit tests: linear chain, diamond, wide fan-out, single task, cycle rejection

- [ ] `dag-2` Execute levels sequentially with tasks within a level running in parallel → depends: dag-1, stream-2, stream-3
  - `ExecutorService` with configurable thread pool (`-PparallelThreads=N`, default: 3)
  - Submit all tasks in a level, await all completions
  - Fail-fast: on first failure, cancel remaining futures in the level
  - Run `opsx-sync` between levels (not between parallel tasks within a level)
  - Gated by `-Pparallel=true` flag — sequential remains default

- [ ] `dag-3` Integrate DAG scheduler into executeTaskChain → depends: dag-2, status-1
  - When `-Pparallel=true`: use LevelScheduler + parallel execution
  - When sequential (default): use LevelScheduler for ordering but execute one at a time
  - Update status.json at each transition
  - Interleaved prefixed output for parallel tasks

## Tests

- [ ] `test-1` Unit tests for streaming callback and timeout behavior → depends: stream-1, stream-3
  - Mock process that emits lines with delays
  - Verify callback receives lines in order
  - Verify timeout triggers destroyForcibly
  - Verify heartbeat logs appear at correct intervals

- [ ] `test-2` Unit tests for LevelScheduler → depends: dag-1
  - Linear chain: A→B→C produces 3 levels
  - Diamond: A→{B,C}→D produces 3 levels with B,C parallel
  - Wide fan-out: A→{B,C,D,E} produces 2 levels
  - No deps: {A,B,C} all in level 0
  - Cycle: throws exception

- [ ] `test-3` Integration test for parallel execution → depends: dag-2
  - Two independent tasks verify they run concurrently (check overlapping timestamps)
  - Dependent task waits for predecessor
  - Failure in one parallel task cancels siblings
