# Design: tiered-verify

## 1. Tiered Verification (`:exec` module)

### `VerifyMode` enum

```kotlin
// exec/src/main/kotlin/zone/clanker/gradle/exec/TieredVerifier.kt

enum class VerifyMode(val value: String) {
    BUILD("build"),     // Full incremental ./gradlew build
    COMPILE("compile"), // Compile only: classes + testClasses
    OFF("off");         // Skip verification

    companion object {
        fun fromString(value: String): VerifyMode
    }
}
```

### `VerifyResult` data class

```kotlin
data class VerifyResult(
    val success: Boolean,
    val mode: VerifyMode,
    val durationMs: Long,
    val message: String,
)
```

### `BuildVerifier` class

```kotlin
class BuildVerifier(
    private val projectDir: File,
    private val gradlewPath: String,
    private val mode: VerifyMode = VerifyMode.BUILD,
) {
    fun verify(): VerifyResult
    fun verifyModules(modulePaths: List<String>): VerifyResult
    private fun runGradleTasks(tasks: List<String>): Boolean
}
```

- `verify()` runs `./gradlew build` (BUILD) or `./gradlew classes testClasses` (COMPILE) or returns immediately (OFF)
- `verifyModules()` scopes to modules: `:core:build`, `:core:classes`
- No `--no-daemon` — warm JVM between tasks

## 2. Streaming Output

### Callback on `AgentRunner.run()`

```kotlin
fun run(
    agent: String,
    prompt: String,
    workingDir: File,
    timeoutSeconds: Long = 300,
    environment: Map<String, String> = emptyMap(),
    onOutput: (line: String) -> Unit = {},  // NEW
): AgentResult
```

Inside `run()`, stdout/stderr reader threads call `onOutput(line)` for each line AND append to StringBuilder. Both happen — streaming for live visibility, capture for the report.

### `TaskLogger`

```kotlin
// exec/src/main/kotlin/zone/clanker/gradle/exec/TaskLogger.kt

class TaskLogger(
    private val taskId: String,
    private val delegate: org.gradle.api.logging.Logger,
) {
    fun lifecycle(msg: String) = delegate.lifecycle("[$taskId] $msg")
    fun output(line: String) = delegate.lifecycle("[$taskId] │ $line")
    fun success(msg: String) = delegate.lifecycle("[$taskId] ✓ $msg")
    fun failure(msg: String) = delegate.lifecycle("[$taskId] ✗ $msg")
    fun progress(msg: String) = delegate.lifecycle("[$taskId] ⏳ $msg")
}
```

### Heartbeat Thread

Started alongside agent process in `AgentRunner.run()`:

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

## 3. Timeouts

### `runGradleTask` and `runGradleTaskSafe`

Replace `proc.waitFor()` with:

```kotlin
val timeoutMinutes = if (project.hasProperty("opsx.buildTimeout"))
    project.property("opsx.buildTimeout").toString().toLong()
else 10L

val completed = proc.waitFor(timeoutMinutes, TimeUnit.MINUTES)
if (!completed) {
    proc.destroyForcibly()
    throw GradleException("$taskName timed out after ${timeoutMinutes}m")
}
```

Also replace `.inheritIO()` with streamed capture through TaskLogger.

## 4. DAG Scheduler

### `LevelScheduler`

```kotlin
// exec/src/main/kotlin/zone/clanker/gradle/exec/LevelScheduler.kt

class LevelScheduler(private val tasks: List<TaskItem>) {

    /**
     * Returns tasks grouped by execution level.
     * Level 0 = no dependencies (or all deps DONE). Level N = all deps in levels < N.
     * Skips tasks that are already DONE.
     */
    fun schedule(): List<List<TaskItem>>
}
```

Uses existing `DependencyGraph` from `:core` for cycle detection and dependency lookup. Kahn's algorithm to assign levels.

### Parallel Execution in ExecTask

```kotlin
private fun executeLevelParallel(
    level: List<TaskItem>,
    executor: ExecutorService,
    ...
) {
    val futures = level.map { task ->
        executor.submit<Boolean> { executeOneTask(task, ...) }
    }
    // Await all, fail-fast on first exception
    futures.map { it.get() }
}
```

Gated by `-Pparallel=true`. Default remains sequential (but still uses LevelScheduler for ordering).

## 5. Live Status File

### kotlinx.serialization dependency

Add `kotlinx-serialization-json` to the version catalog and `:exec` module:

```toml
# gradle/libs.versions.toml
[versions]
kotlinx-serialization = "1.10.0"

[libraries]
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin-compiler" }
```

```kotlin
// exec/build.gradle.kts
plugins {
    id("openspec-module")
    id("openspec-publish")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core"))
    implementation(libs.kotlinx.serialization.json)
    // ... test deps unchanged
}
```

### `ExecStatus`

```kotlin
// exec/src/main/kotlin/zone/clanker/gradle/exec/ExecStatus.kt

@Serializable
data class ExecStatus(
    val proposal: String,
    val startedAt: String,
    val currentLevel: Int,
    val tasks: Map<String, TaskExecStatus>,
)

@Serializable
data class TaskExecStatus(
    val status: String,  // PENDING, RUNNING, DONE, FAILED, CANCELLED
    val attempt: Int? = null,
    val agent: String? = null,
    val startedAt: String? = null,
    val duration: String? = null,
    val error: String? = null,
)
```

Serialized via `Json { prettyPrint = true }.encodeToString(status)`. Written to `.opsx/exec/status.json`. Atomic write: write to `.status.json.tmp`, then rename.

## File Layout

```
exec/src/main/kotlin/zone/clanker/gradle/exec/
├── AgentRunner.kt        (MODIFIED: onOutput callback, heartbeat thread)
├── CycleDetector.kt      (unchanged)
├── ExecStatus.kt         (NEW: status file model + writer)
├── LevelScheduler.kt     (NEW: DAG → parallel levels)
├── SpecParser.kt         (unchanged)
├── TaskLogger.kt         (NEW: prefixed logging)
└── TieredVerifier.kt     (NEW: BuildVerifier, VerifyMode, VerifyResult)

exec/src/test/kotlin/zone/clanker/gradle/exec/
├── ExecTest.kt           (unchanged)
├── LevelSchedulerTest.kt (NEW)
└── TieredVerifierTest.kt (NEW)

tasks/src/main/kotlin/zone/clanker/gradle/tasks/execution/
└── ExecTask.kt           (MODIFIED: all integrations)
```
