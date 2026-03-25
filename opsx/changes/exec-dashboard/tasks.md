# Tasks: exec-dashboard

## Implementation Tasks

- [x] `ed-1` Enrich ExecStatus and TaskExecStatus data models with new fields
  > Add `parallel`, `totalThreads`, `activeThreads`, `verifyMode` to `ExecStatus`.
  > Add `pid`, `level`, `maxAttempts` to `TaskExecStatus`.
  > File: `exec/src/main/kotlin/zone/clanker/gradle/exec/ExecStatus.kt`
  > All new fields have defaults so existing code continues to compile.
  > verify: symbol-exists ExecStatus, symbol-exists TaskExecStatus

- [x] `ed-2` Create ExecStatusReader with read() and renderDashboard()
  > depends: ed-1
  > New file: `exec/src/main/kotlin/zone/clanker/gradle/exec/ExecStatusReader.kt`
  > `read(file: File): ExecStatus?` — parse status.json, return null if missing/corrupt.
  > `renderDashboard(status: ExecStatus): String` — render the visual table:
  > - Header: boxed proposal name + progress bar + fraction
  > - Table: Code | Status (icon) | Agent | Time | Attempt | Level
  > - Footer: parallel info, verify mode, started time, elapsed
  > - Running task elapsed time computed from `startedAt` vs `System.currentTimeMillis()`
  > Icons: `✅` done, `🔄` running, `⏳` pending, `❌` failed, `🚫` cancelled
  > Imports: `kotlinx.serialization.json.Json`, `kotlinx.serialization.decodeFromString`, `java.io.File`, `java.time.LocalDateTime`, `java.time.Duration`, `java.time.format.DateTimeFormatter`
  > verify: symbol-exists ExecStatusReader

- [x] `ed-3` Unit tests for ExecStatusReader
  > depends: ed-2
  > New file: `exec/src/test/kotlin/zone/clanker/gradle/exec/ExecStatusReaderTest.kt`
  > Tests:
  > - `read returns null for missing file`
  > - `read returns null for corrupt JSON`
  > - `read parses valid status JSON`
  > - `renderDashboard shows progress bar with correct percentage`
  > - `renderDashboard shows running tasks with elapsed time`
  > - `renderDashboard shows parallel info when parallel=true`
  > - `renderDashboard hides parallel line when parallel=false`
  > verify: test-passes ExecStatusReaderTest

- [x] `ed-4` Integrate ExecStatusReader into StatusTask
  > depends: ed-2
  > File: `tasks/src/main/kotlin/zone/clanker/gradle/tasks/workflow/StatusTask.kt`
  > In `execute()`, before the proposal rendering block, add:
  > ```kotlin
  > val statusFile = File(project.projectDir, ".opsx/exec/status.json")
  > val execStatus = ExecStatusReader.read(statusFile)
  > if (execStatus != null) {
  >     sb.appendLine(ExecStatusReader.renderDashboard(execStatus))
  >     sb.appendLine()
  > }
  > ```
  > Import: `zone.clanker.gradle.exec.ExecStatusReader`
  > verify: symbol-exists StatusTask

- [x] `ed-5` Pass new fields from ExecTask to ExecStatus writes
  > depends: ed-1
  > File: `tasks/src/main/kotlin/zone/clanker/gradle/tasks/execution/ExecTask.kt`
  > Update `writeExecStatus()` signature and all call sites to include:
  > - `parallel` from `isParallel` local variable
  > - `totalThreads` from `threads` local variable
  > - `activeThreads` — count of RUNNING entries in `taskStatusMap`
  > - `verifyMode` from resolved verify mode string
  > Update `executeChainTask()` to set:
  > - `pid` from `Process.pid()` after `AgentRunner.run()` — requires exposing PID from AgentRunner
  > - `level` from `levelIdx` parameter
  > - `maxAttempts` from `resolvedRetries`
  > verify: symbol-exists ExecTask

- [x] `ed-6` Expose process PID from AgentRunner
  > depends: ed-5
  > File: `exec/src/main/kotlin/zone/clanker/gradle/exec/AgentRunner.kt`
  > Change `AgentResult` to include `pid: Long? = null`.
  > In `run()`, capture `process.pid()` and pass it to the result.
  > ExecTask can then write it to status.
  > verify: symbol-exists AgentResult

- [x] `ed-7` Integration test: StatusTask renders exec dashboard
  > depends: ed-4, ed-5
  > New file: `tasks/src/test/kotlin/zone/clanker/gradle/tasks/workflow/StatusTaskExecTest.kt`
  > Write a `.opsx/exec/status.json` fixture, invoke StatusTask rendering logic, assert the output contains:
  > - Progress bar
  > - Task table with icons
  > - Parallel thread info
  > verify: test-passes StatusTaskExecTest
