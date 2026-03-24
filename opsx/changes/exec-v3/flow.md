# Complete Execution Flow — From `opsx-apply` to Task Completion

## The Full Call Chain

```
YOU (in terminal or agent TUI)
  └─ ./gradlew opsx-apply --name=add-caching
       └─ ExecTask.execute()
            └─ executeTaskChain("cache-1,cache-2,cache-3")
                 │
                 ├─ FOR EACH TASK (sequential):
                 │    │
                 │    ├─ 1. Mark IN_PROGRESS in tasks.md
                 │    │
                 │    ├─ 2. Build prompt (read context files)
                 │    │
                 │    ├─ 3. AgentRunner.run()                    ⬅ PROCESS #1
                 │    │    └─ ProcessBuilder("copilot", "-p", prompt, "--yolo", "-s")
                 │    │    └─ process.waitFor(600s)              ⬅ BLOCKS UP TO 10 MIN
                 │    │    └─ stdout/stderr captured to StringBuilder
                 │    │    └─ NO OUTPUT VISIBLE DURING EXECUTION
                 │    │
                 │    ├─ 4. If agent succeeded + verify=true:
                 │    │    └─ runGradleTaskSafe("opsx-verify")   ⬅ PROCESS #2
                 │    │         └─ ProcessBuilder("./gradlew", "opsx-verify")
                 │    │         └─ .inheritIO()
                 │    │         └─ proc.waitFor()                ⬅ BLOCKS INDEFINITELY ‼️
                 │    │
                 │    ├─ 5. If verify passed:
                 │    │    └─ TaskLifecycle.onTaskCompleted()
                 │    │         │
                 │    │         ├─ 5a. AssertionRunner.run()
                 │    │         │    └─ checkBuildPasses()        ⬅ PROCESS #3
                 │    │         │         └─ ProcessBuilder("./gradlew", "build")
                 │    │         │         └─ .inheritIO()
                 │    │         │         └─ proc.waitFor(10min)
                 │    │         │         └─ THIS RUNS THE FULL BUILD + TESTS
                 │    │         │
                 │    │         ├─ 5b. TaskWriter.updateStatus() → DONE
                 │    │         │
                 │    │         ├─ 5c. TaskWriter.propagateCompletion()
                 │    │         │
                 │    │         ├─ 5d. runPostCompletionSync()
                 │    │         │    └─ TaskCommandGenerator.generate()
                 │    │         │    └─ Copies updated skill files
                 │    │         │
                 │    │         └─ 5e. runReconciliation()
                 │    │              └─ TaskReconciler.reconcileFull()
                 │    │              └─ Symbol index rebuild
                 │    │              └─ Writes .opsx/reconcile.md
                 │    │
                 │    └─ 6. NEXT TASK
                 │
                 └─ "✓ Task chain completed successfully"
```

## Where Time Is Spent (Per Task)

| Step | What | Time | Blocks? | Output? |
|------|------|------|---------|---------|
| Agent spawn | `copilot -p "..." --yolo -s` | 1-10 min | YES (waitFor 600s) | **NONE** ‼️ |
| Verify (optional) | `./gradlew opsx-verify` | 10-30s | YES (waitFor ∞) | inheritIO |
| Build gate | `./gradlew build` | 1-10 min | YES (waitFor 10min) | inheritIO |
| Post-sync | TaskCommandGenerator | <1s | No | logger |
| Reconciliation | Symbol index rebuild | 1-5s | No | logger |

**Total per task: 2-20 minutes**, mostly blocked on subprocesses with no visibility.

## The Problems (Numbered)

### Problem 1: Double verification

Step 4 runs `opsx-verify`, then step 5a runs `./gradlew build` (which includes verify). The build gate is **redundant with the verify step**. For a project with 5-minute tests, this doubles the wait.

**Fix:** If verify passed in step 4, skip the build-passes assertion in step 5a. Or: make the verify step in step 4 run `build` instead of `opsx-verify`, and skip step 5a entirely.

### Problem 2: Agent output is invisible

`AgentRunner.run()` captures stdout/stderr into `StringBuilder` and returns them only after the process finishes. For a 5-minute agent session, you see nothing. In a TUI, it looks frozen.

**Fix:** Stream output line-by-line via callback. Log each line with `[task-id] │ ` prefix.

### Problem 3: Gradle subprocesses have no timeout (or wrong timeout)

- `runGradleTaskSafe("opsx-verify")` → `proc.waitFor()` → **NO TIMEOUT** → hangs forever
- `checkBuildPasses()` → `proc.waitFor(10, MINUTES)` → has timeout but...
- Both use `.inheritIO()` which connects to parent streams — in a TUI, this output may be swallowed

**Fix:** All subprocess calls get timeouts. All subprocess output gets streamed through the prefixed logger instead of inheritIO.

### Problem 4: Sequential execution of independent tasks

If you have:
```
cache-1 (no deps)
cache-2 (depends: cache-1)
cache-3 (depends: cache-1)
cache-4 (depends: cache-2, cache-3)
```

Current: cache-1 → cache-2 → cache-3 → cache-4 (serial, ~60 min)
Optimal: cache-1 → [cache-2 ‖ cache-3] → cache-4 (parallel, ~40 min)

**Fix:** DAG scheduler with level-based parallel execution.

### Problem 5: No way to distinguish parent vs child output

When the agent TUI shows output, you can't tell if it's:
- Your agent (the one you're talking to)
- The subprocess agent (spawned by opsx-exec)
- A Gradle build (spawned by the subprocess)

**Fix:** Every line from opsx-exec gets `[task-id]` prefix. Every subprocess line gets `[task-id] │` prefix. Clear visual nesting.

### Problem 6: No progress or ETA

During a 10-minute build gate, there's no indication of progress. Is it compiling? Running tests? Stuck?

**Fix:** Stream build output through the logger. Add heartbeat every 30s for the agent subprocess (which may not produce output during thinking).

## Proposed New Flow

```
YOU (in terminal or agent TUI)
  └─ ./gradlew opsx-apply --name=add-caching
       └─ ExecTask.execute()
            │
            ├─ Build DAG from tasks.md
            ├─ Sort into levels: L0=[cache-1], L1=[cache-2, cache-3], L2=[cache-4]
            ├─ Write .opsx/exec/status.json (all PENDING)
            │
            ├─ LEVEL 0:
            │    └─ [cache-1] ⏳ Spawning copilot... (attempt 1/3)
            │    └─ [cache-1] │ Reading context...          ← streamed live
            │    └─ [cache-1] │ Creating CacheInterface.kt...
            │    └─ [cache-1] ✓ Agent completed (47s)
            │    └─ [cache-1] ⏳ Running build...
            │    └─ [cache-1] │ > Task :compileKotlin       ← streamed live
            │    └─ [cache-1] │ > Task :test
            │    └─ [cache-1] │ BUILD SUCCESSFUL in 1m2s
            │    └─ [cache-1] ✓ Build passed — DONE (1m49s)
            │    └─ opsx-sync (between levels)
            │
            ├─ LEVEL 1 (parallel):
            │    ├─ [cache-2] ⏳ Spawning copilot...
            │    ├─ [cache-3] ⏳ Spawning copilot...
            │    ├─ [cache-2] │ Modifying UserRepository.kt...
            │    ├─ [cache-3] │ Adding invalidation logic...
            │    ├─ [cache-2] ✓ Agent completed (32s)
            │    ├─ [cache-3] ✓ Agent completed (28s)
            │    ├─ [cache-2] ⏳ Running build...
            │    ├─ [cache-3] ⏳ Running build...
            │    ├─ [cache-2] ✓ Build passed — DONE
            │    ├─ [cache-3] ✓ Build passed — DONE
            │    └─ opsx-sync (between levels)
            │
            ├─ LEVEL 2:
            │    └─ [cache-4] ...
            │
            └─ ✓ All tasks completed (4m12s total)
```

## Key Changes Summary

| Current | Proposed |
|---------|----------|
| Agent output captured silently | Streamed line-by-line with `[task-id]` prefix |
| `runGradleTaskSafe` has no timeout | All subprocesses have configurable timeouts |
| `.inheritIO()` on builds | Stream through prefixed logger |
| Verify + build gate (double) | Single verification step (configurable: `verify` or `build`) |
| Sequential only | DAG scheduler with level-based parallel execution |
| No status file | `.opsx/exec/status.json` updated in real-time |
| No heartbeat | 30s heartbeat during long operations |
| No task prefix in logs | Every line prefixed with `[task-id]` |
