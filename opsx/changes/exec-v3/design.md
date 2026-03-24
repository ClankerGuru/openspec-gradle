# Design — Exec Engine v3

## AgentRunner Changes

```kotlin
// New callback-based run method
fun run(
    agent: String,
    prompt: String,
    workingDir: File,
    timeoutSeconds: Long = 300,
    environment: Map<String, String> = emptyMap(),
    onOutput: (line: String) -> Unit = {},  // NEW: streaming callback
): AgentResult
```

Inside `run()`, the stdout/stderr reader threads call `onOutput(line)` for each line AND append to the StringBuilder. Both happen — streaming for live visibility, capture for the report.

Heartbeat thread starts alongside the process:

```kotlin
val heartbeat = Thread {
    var elapsed = 0
    while (process.isAlive) {
        Thread.sleep(30_000)
        elapsed += 30
        onOutput("⏳ Still running... ${elapsed}s")
    }
}
heartbeat.isDaemon = true
heartbeat.start()
```

## Prefixed Logger

```kotlin
class TaskLogger(
    private val taskId: String,
    private val delegate: Logger,
) {
    fun lifecycle(msg: String) = delegate.lifecycle("[$taskId] $msg")
    fun output(line: String) = delegate.lifecycle("[$taskId] │ $line")
    fun success(msg: String) = delegate.lifecycle("[$taskId] ✓ $msg")
    fun failure(msg: String) = delegate.lifecycle("[$taskId] ✗ $msg")
    fun progress(msg: String) = delegate.lifecycle("[$taskId] ⏳ $msg")
}
```

## Timeout Wrapper

```kotlin
private fun runGradleTask(taskName: String, timeoutMinutes: Long = 10) {
    val proc = ProcessBuilder(...)
        .directory(project.rootDir)
        .redirectErrorStream(true)
        .start()

    // Stream output
    val reader = Thread {
        proc.inputStream.bufferedReader().forEachLine { taskLog.output(it) }
    }
    reader.start()

    val completed = proc.waitFor(timeoutMinutes, TimeUnit.MINUTES)
    if (!completed) {
        proc.destroyForcibly()
        reader.join(2000)
        throw GradleException("$taskName timed out after ${timeoutMinutes}m")
    }
    reader.join(5000)

    if (proc.exitValue() != 0) {
        throw GradleException("$taskName failed with exit code ${proc.exitValue()}")
    }
}
```

## LevelScheduler

```kotlin
class LevelScheduler(private val tasks: List<TaskItem>) {

    /**
     * Returns tasks grouped by execution level.
     * Level 0 = no dependencies. Level N = all deps in levels < N.
     */
    fun schedule(): List<List<TaskItem>> {
        // Filter to non-DONE tasks
        // Topological sort using Kahn's algorithm
        // Group by depth into levels
    }
}
```

## Parallel Execution

```kotlin
private fun executeLevelParallel(
    level: List<TaskItem>,
    executor: ExecutorService,
    proposal: Proposal,
    outputDir: File,
    timestamp: String,
) {
    val futures = level.map { task ->
        executor.submit<Boolean> {
            executeOneTask(task, proposal, outputDir, timestamp)
        }
    }

    // Await all, fail-fast on first exception
    val results = futures.map { future ->
        try {
            future.get()  // blocks until this task completes
        } catch (e: ExecutionException) {
            // Cancel remaining
            futures.forEach { it.cancel(true) }
            throw e.cause ?: e
        }
    }

    if (results.any { !it }) {
        throw GradleException("One or more tasks in level failed")
    }
}
```

## Status File

```kotlin
data class ExecStatus(
    val proposal: String,
    val startedAt: String,
    val currentLevel: Int,
    val tasks: Map<String, TaskExecStatus>,
)

data class TaskExecStatus(
    val status: String,  // PENDING, RUNNING, DONE, FAILED, CANCELLED
    val attempt: Int? = null,
    val agent: String? = null,
    val startedAt: String? = null,
    val duration: String? = null,
    val error: String? = null,
)
```

Written as JSON via `kotlinx.serialization` or simple string building (avoid new dependency).

## File Layout

```
exec/
├── AgentRunner.kt          # + callback param, heartbeat thread
├── CycleDetector.kt        # unchanged
├── LevelScheduler.kt       # NEW: DAG → levels
├── SpecParser.kt            # unchanged
├── TaskLogger.kt            # NEW: prefixed logging
└── ExecStatus.kt            # NEW: status file model

tasks/execution/
├── ExecTask.kt              # + parallel flag, timeout params, level execution
├── CleanTask.kt             # unchanged
└── SyncTask.kt              # unchanged
```
