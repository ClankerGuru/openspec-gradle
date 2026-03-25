# Tasks: tiered-verify

## Execution Flow

```
Phase 1 — Foundation (parallel, no deps)
┌─────────┬─────────┬─────────┬─────────┬─────────┬─────────┐
│  tv-0   │  tv-1   │  tv-3   │  tv-4   │  tv-5   │  tv-6   │
│ kotlinx │ verifier│ stream  │ logger  │ timeout │ scheduler│
└────┬────┴────┬────┴────┬────┴────┬────┴────┬────┴────┬────┘
     │         │         │         │         │         │
Phase 2 — Tests + Status (depend on Phase 1)
┌────┴────┬────┴────┬────┴─────────┴────┬────┘    ┌───┴────┐
│  tv-8   │  tv-2   │      tv-12       │         │  tv-7  │
│ status  │ verifier│  stream tests    │         │ sched  │
│ model   │ tests   │                  │         │ tests  │
└────┬────┴────┬────┴──────────────────┘         └───┬────┘
     │         │                                     │
Phase 3 — Integration (sequential, modify ExecTask)
     │    ┌────┴─────────────────┐                   │
     │    │  tv-9                │                   │
     │    │  wire verifier       │                   │
     │    └────┬─────────────────┘                   │
     │    ┌────┴─────────────────┐                   │
     │    │  tv-10               │                   │
     │    │  wire streaming      │                   │
     │    └────┬─────────────────┘                   │
     │    ┌────┴─────────────────┴───────────────────┘
     │    │  tv-11
     └────┤  wire scheduler + status + parallel
          └──────────────────────────────────────────
```

**Phase 1** builds all independent pieces in the `:exec` module — no conflicts.
**Phase 2** adds tests and the status model — each only depends on its Phase 1 piece.
**Phase 3** is sequential because tv-9, tv-10, tv-11 all modify `ExecTask.kt` — each builds on the previous.

After each task: verify step → mark done via `./gradlew opsx-tv-N --set=done`.

---

## Dependencies

- [x] `tv-0` Add kotlinx-serialization-json to version catalog and :exec module
  - **File:** `gradle/libs.versions.toml` (MODIFY)
    - Add `kotlinx-serialization = "1.10.0"` to `[versions]`
    - Add `kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }` to `[libraries]`
    - Add `kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin-compiler" }` to `[plugins]`
  - **File:** `exec/build.gradle.kts` (MODIFY)
    - Add `alias(libs.plugins.kotlin.serialization)` to plugins block
    - Add `implementation(libs.kotlinx.serialization.json)` to dependencies block
  - **Verify:** `./gradlew :exec:compileKotlin`

## Tiered Verification

- [x] `tv-1` Create TieredVerifier.kt with BuildVerifier, VerifyMode, and VerifyResult
  - **File:** `exec/src/main/kotlin/zone/clanker/gradle/exec/TieredVerifier.kt` (NEW)
  - Create `VerifyMode` enum with `BUILD`, `COMPILE`, `OFF` values and `fromString()` companion (case-insensitive, throws on unknown)
  - Create `VerifyResult` data class: `success: Boolean`, `mode: VerifyMode`, `durationMs: Long`, `message: String`
  - Create `BuildVerifier(projectDir: File, gradlewPath: String, mode: VerifyMode)`:
    - `verify()`: runs `build` or `classes testClasses` or returns immediately based on mode
    - `verifyModules(modulePaths: List<String>)`: scopes tasks to modules (`:core:build`)
    - `runGradleTasks(tasks: List<String>)`: ProcessBuilder, no `--no-daemon`, captures output, returns boolean
  - **Imports:** `java.io.File`
  - **Verify:** `./gradlew :exec:compileKotlin`

- [x] `tv-2` Add unit tests for BuildVerifier and VerifyMode → depends: tv-1
  - **File:** `exec/src/test/kotlin/zone/clanker/gradle/exec/TieredVerifierTest.kt` (NEW)
  - Test `VerifyMode.fromString` for all valid values, case-insensitive, throws on unknown ("turbo", "test", "full", "auto")
  - Test `VerifyMode.value` strings match
  - Test OFF mode `verify()` and `verifyModules()` return success immediately (use `/nonexistent/gradlew`)
  - Test `VerifyResult` construction and `copy()`
  - Test BUILD/COMPILE modes fail gracefully with missing gradlew (`success=false`, no exception)
  - **Imports:** `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.assertThrows`, `kotlin.test.assertEquals`, `kotlin.test.assertTrue`, `java.io.File`
  - **Verify:** `./gradlew :exec:test`

## Streaming & Observability

- [x] `tv-3` Add streaming output callback and heartbeat to AgentRunner.run()
  - **File:** `exec/src/main/kotlin/zone/clanker/gradle/exec/AgentRunner.kt` (MODIFY)
  - Add `onOutput: (String) -> Unit = {}` parameter to `run()` method signature
  - In stdout reader thread: call `onOutput(line)` for each line before appending to StringBuilder
  - In stderr reader thread: same — call `onOutput(line)` before appending
  - Add heartbeat daemon thread: while process is alive, sleep 30s, call `onOutput("⏳ Still running... ${elapsed}s")`
  - Join heartbeat thread after process completes (with 1s timeout)
  - Default callback is no-op — backwards compatible
  - **Verify:** `./gradlew :exec:compileKotlin`

- [x] `tv-4` Create TaskLogger for prefixed log output
  - **File:** `exec/src/main/kotlin/zone/clanker/gradle/exec/TaskLogger.kt` (NEW)
  - Class `TaskLogger(taskId: String, delegate: org.gradle.api.logging.Logger)`
  - Methods: `lifecycle(msg)` → `[$taskId] $msg`, `output(line)` → `[$taskId] │ $line`, `success(msg)` → `[$taskId] ✓ $msg`, `failure(msg)` → `[$taskId] ✗ $msg`, `progress(msg)` → `[$taskId] ⏳ $msg`
  - **Imports:** `org.gradle.api.logging.Logger`
  - **Verify:** `./gradlew :exec:compileKotlin`

## Timeouts

- [x] `tv-5` Add configurable timeouts to runGradleTask and runGradleTaskSafe in ExecTask
  - **File:** `tasks/src/main/kotlin/zone/clanker/gradle/tasks/execution/ExecTask.kt` (MODIFY)
  - In `runGradleTask`: replace `proc.waitFor()` with `proc.waitFor(timeout, TimeUnit.MINUTES)` + `destroyForcibly()` on expiry
  - In `runGradleTaskSafe`: same timeout pattern
  - Read timeout from `project.hasProperty("opsx.buildTimeout")`, default 10 minutes
  - Replace `.inheritIO()` with output capture — read `proc.inputStream` line by line, log via `logger.lifecycle`
  - Remove `"--no-daemon"` from both methods' ProcessBuilder args
  - Add import: `java.util.concurrent.TimeUnit`
  - **Verify:** `./gradlew :tasks:compileKotlin`

## DAG Scheduler

- [x] `tv-6` Create LevelScheduler for DAG-based parallel level grouping
  - **File:** `exec/src/main/kotlin/zone/clanker/gradle/exec/LevelScheduler.kt` (NEW)
  - Class `LevelScheduler(tasks: List<TaskItem>)` using `DependencyGraph` from `:core`
  - Method `schedule(): List<List<TaskItem>>` — returns tasks grouped by execution level
    - Skip DONE tasks
    - Level 0 = tasks with no pending dependencies
    - Level N = tasks whose deps are all in levels < N
    - Uses Kahn's algorithm: compute in-degree, dequeue level by level
    - Throws on cycles (delegate to `DependencyGraph.hasCycle()`)
  - **Imports:** `zone.clanker.gradle.core.TaskItem`, `zone.clanker.gradle.core.TaskStatus`, `zone.clanker.gradle.core.DependencyGraph`
  - **Verify:** `./gradlew :exec:compileKotlin`

- [x] `tv-7` Add unit tests for LevelScheduler → depends: tv-6
  - **File:** `exec/src/test/kotlin/zone/clanker/gradle/exec/LevelSchedulerTest.kt` (NEW)
  - Test linear chain: A→B→C produces 3 levels
  - Test diamond: A→{B,C}→D produces 3 levels with B,C in same level
  - Test wide fan-out: A→{B,C,D,E} produces 2 levels
  - Test no deps: {A,B,C} all in level 0
  - Test skips DONE tasks
  - Test cycle throws exception
  - Test single task produces 1 level
  - Helper to create `TaskItem` with code, status, and deps
  - **Imports:** `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.assertThrows`, `kotlin.test.assertEquals`, `zone.clanker.gradle.core.*`
  - **Verify:** `./gradlew :exec:test`

## Live Status

- [x] `tv-8` Create ExecStatus model with kotlinx.serialization and JSON writer → depends: tv-0
  - **File:** `exec/src/main/kotlin/zone/clanker/gradle/exec/ExecStatus.kt` (NEW)
  - `@Serializable data class ExecStatus(val proposal: String, val startedAt: String, val currentLevel: Int, val tasks: Map<String, TaskExecStatus>)`
  - `@Serializable data class TaskExecStatus(val status: String, val attempt: Int? = null, val agent: String? = null, val startedAt: String? = null, val duration: String? = null, val error: String? = null)`
  - `status` values: `PENDING`, `RUNNING`, `DONE`, `FAILED`, `CANCELLED`
  - Companion `ExecStatus.write(file: File, status: ExecStatus)` — serialize with `Json { prettyPrint = true }.encodeToString(status)`, atomic write (write to `.tmp`, rename)
  - **Imports:** `kotlinx.serialization.Serializable`, `kotlinx.serialization.encodeToString`, `kotlinx.serialization.json.Json`, `java.io.File`
  - **Verify:** `./gradlew :exec:compileKotlin`

## Integration

- [x] `tv-9` Wire BuildVerifier into ExecTask verify steps → depends: tv-1, tv-5
  - **File:** `tasks/src/main/kotlin/zone/clanker/gradle/tasks/execution/ExecTask.kt` (MODIFY)
  - Add imports: `zone.clanker.gradle.exec.BuildVerifier`, `zone.clanker.gradle.exec.VerifyMode`
  - Add property: `@get:Input @get:Optional abstract val verifyMode: Property<String>`
  - Add `private fun createBuildVerifier(): BuildVerifier` — reads from `verifyMode` property → `project.hasProperty("opsx.verify")` → default `"build"`
  - In `executeSingle` (~line 195): replace `runGradleTaskSafe("opsx-verify")` with `createBuildVerifier().verify()`, log `verifyResult.message`, use `verifyResult.success`
  - In `executeTaskChain` (~line 330): same replacement, update error message to include mode
  - Update `init` description to include `-Popsx.verify=build|compile|off`
  - **Verify:** `./gradlew :tasks:compileKotlin`

- [x] `tv-10` Wire streaming + TaskLogger into ExecTask → depends: tv-3, tv-4, tv-9
  - **File:** `tasks/src/main/kotlin/zone/clanker/gradle/tasks/execution/ExecTask.kt` (MODIFY)
  - Add imports: `zone.clanker.gradle.exec.TaskLogger`
  - In `executeTaskChain`: create `TaskLogger(code, logger)` per task code
  - Pass `taskLog::output` as `onOutput` callback to `AgentRunner.run()`
  - Replace `logger.lifecycle` calls in task chain loop with `taskLog.lifecycle`, `taskLog.success`, `taskLog.failure`
  - In `executeSingle`: create `TaskLogger(taskId, logger)` and use same pattern
  - **Verify:** `./gradlew :tasks:compileKotlin`

- [x] `tv-11` Wire LevelScheduler + parallel execution into executeTaskChain → depends: tv-6, tv-8, tv-10
  - **File:** `tasks/src/main/kotlin/zone/clanker/gradle/tasks/execution/ExecTask.kt` (MODIFY)
  - Add imports: `zone.clanker.gradle.exec.LevelScheduler`, `zone.clanker.gradle.exec.ExecStatus`, `zone.clanker.gradle.exec.TaskExecStatus`, `java.util.concurrent.Executors`, `java.util.concurrent.ExecutorService`
  - Add properties: `@get:Input @get:Optional abstract val parallel: Property<Boolean>`, `@get:Input @get:Optional abstract val parallelThreads: Property<Int>`
  - In `executeTaskChain`: after validating codes, use `LevelScheduler(allTasks).schedule()` to get levels
  - Initialize `ExecStatus` and write to `.opsx/exec/status.json`
  - When `-Pparallel=true`: create `Executors.newFixedThreadPool(threads)`, submit all tasks in a level, await all, fail-fast on first failure, cancel remaining futures
  - When sequential (default): iterate levels in order, execute tasks one at a time
  - Run `opsx-sync` between levels (not between parallel tasks within a level)
  - Update `ExecStatus` on each state transition (PENDING→RUNNING→DONE/FAILED)
  - Delete status file on completion (or keep if `-PkeepStatus=true`)
  - **Verify:** `./gradlew :tasks:compileKotlin`

## Tests

- [x] `tv-12` Unit tests for streaming callback → depends: tv-3
  - **File:** `exec/src/test/kotlin/zone/clanker/gradle/exec/AgentRunnerStreamTest.kt` (NEW)
  - Test that `onOutput` callback receives lines from a mock process (use a simple shell command like `echo`)
  - Test that output is still captured in `AgentResult.stdout`
  - Test default no-op callback doesn't break existing behavior
  - **Verify:** `./gradlew :exec:test`
