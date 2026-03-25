# Design: exec-dashboard

## Approach

### Dashboard Output

When `./gradlew opsx-status` detects `.opsx/exec/status.json`, it renders an execution section at the top before the normal proposal listing:

```text
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  EXEC  add-user-auth          [████████░░] 80%  4/5
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Code   Status  Agent    Time   Attempt  Level
  ─────  ──────  ───────  ─────  ───────  ─────
  aua-1  ✅ done  claude   12s    1/3      0
  aua-2  ✅ done  claude    8s    1/3      0
  aua-3  🔄 run   claude   47s    1/3      1
  aua-4  🔄 run   copilot  35s    2/3      1
  aua-5  ⏳ wait  —        —      —        2

  ⚡ Parallel: 2/4 threads active │ Verify: compile
  🕐 Started: 14:32:05 │ Elapsed: 1m 22s
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

If no `status.json` exists, `opsx-status` behaves exactly as before.

### Data Model Changes

Enrich `ExecStatus` with fields needed for the dashboard:

```kotlin
@Serializable
data class ExecStatus(
    val proposal: String,
    val startedAt: String,
    val currentLevel: Int,
    val tasks: Map<String, TaskExecStatus>,
    // New fields
    val parallel: Boolean = false,
    val totalThreads: Int = 1,
    val activeThreads: Int = 0,
    val verifyMode: String = "build",
)
```

Enrich `TaskExecStatus` with:

```kotlin
@Serializable
data class TaskExecStatus(
    val status: String,
    val attempt: Int? = null,
    val maxAttempts: Int? = null,
    val agent: String? = null,
    val startedAt: String? = null,
    val duration: String? = null,
    val error: String? = null,
    // New fields
    val pid: Long? = null,
    val level: Int? = null,
)
```

### Reader

Add `ExecStatusReader` to `:exec` module:

```kotlin
object ExecStatusReader {
    fun read(file: File): ExecStatus?
    fun renderDashboard(status: ExecStatus): String
}
```

- `read()` parses `status.json`, returns null if missing or corrupt
- `renderDashboard()` formats the visual table string

### Integration into StatusTask

In `StatusTask.execute()`, before rendering proposals:

```kotlin
val statusFile = File(projectDir, ".opsx/exec/status.json")
val execStatus = ExecStatusReader.read(statusFile)
if (execStatus != null) {
    sb.appendLine(ExecStatusReader.renderDashboard(execStatus))
    sb.appendLine()
}
```

### ExecTask Changes

Update `writeExecStatus()` calls to pass the new fields:
- `parallel`, `totalThreads`, `activeThreads` from execution context
- `pid` from `Process.pid()` after agent spawn
- `level` from the current level index
- `maxAttempts` from resolved retries
- `verifyMode` from the resolved verify mode string

## Key Decisions

1. **Snapshot, not live** — each `opsx-status` call reads the file once. No polling, no watching. Simple.
2. **Render in `:exec` module** — `ExecStatusReader` lives next to `ExecStatus` so the rendering logic is co-located with the data model. `StatusTask` just calls it.
3. **Icons for status** — `✅` done, `🔄` running, `⏳` pending, `❌` failed, `🚫` cancelled. Visual scan without reading text.
4. **Table format** — fixed-width columns with Unicode box-drawing for alignment. Readable in terminals and markdown.
5. **Elapsed time on running tasks** — computed from `startedAt` vs. current time, not stored. Always fresh.

## Risks

1. **Status file read during write** — `ExecStatus.write()` already uses atomic rename, so partial reads are unlikely. `ExecStatusReader.read()` will catch parse exceptions and return null.
2. **PID availability** — `Process.pid()` requires Java 9+. Gradle 9.x requires JDK 17+, so this is safe.
