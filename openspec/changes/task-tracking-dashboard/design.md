# Design: Task Tracking Dashboard

## Task Code Format

Codes are derived from the proposal name (abbreviated) + task position:

```
<prefix>-<number>[.<subnumber>]
```

**Prefix generation**: Take the proposal kebab-case name, extract initials or abbreviate:
- `task-tracking-dashboard` → `ttd`
- `add-user-auth` → `aua`
- `fix-login-bug` → `flb`

**Numbering**: Follows the hierarchy in `tasks.md`:
- `ttd-1` — first top-level task
- `ttd-1.1` — first subtask of task 1
- `ttd-2` — second top-level task

## tasks.md Format (Enhanced)

The existing `tasks.md` gets task codes injected:

```markdown
## Tasks

- [ ] `ttd-1` Parse tasks.md files from openspec/changes/
  - [ ] `ttd-1.1` Define TaskItem data class
  - [ ] `ttd-1.2` Build Markdown checkbox parser
- [ ] `ttd-2` Generate task codes from proposal name
- [x] `ttd-3` Register dynamic Gradle tasks
```

Status is determined by checkbox state:
- `- [ ]` → `TODO`
- `- [~]` → `IN_PROGRESS`  
- `- [x]` → `DONE`

## Components

### 1. TaskParser

Parses `tasks.md` files into structured `TaskItem` objects.

```kotlin
data class TaskItem(
    val code: String,        // e.g., "ttd-1.1"
    val description: String,
    val status: TaskStatus,  // TODO, IN_PROGRESS, DONE
    val children: List<TaskItem>
)

enum class TaskStatus { TODO, IN_PROGRESS, DONE }
```

### 2. TaskCodeGenerator

Generates short prefix from proposal name + assigns hierarchical numbers.

```kotlin
object TaskCodeGenerator {
    fun prefix(proposalName: String): String  // "task-tracking-dashboard" → "ttd"
    fun assignCodes(prefix: String, tasks: List<TaskItem>): List<TaskItem>
}
```

### 3. OpenSpecStatusTask (Gradle Task)

`./gradlew openspecStatus`

Scans `openspec/changes/*/tasks.md`, parses all tasks, outputs dashboard:

```
┌─────────────────────────────┬────────┬──────┬─────┬──────────┐
│ Proposal                    │ Status │ Done │ All │ Progress │
├─────────────────────────────┼────────┼──────┼─────┼──────────┤
│ task-tracking-dashboard     │ active │ 2    │ 8   │ 25%      │
│ fix-context-generation      │ active │ 5    │ 5   │ 100%     │
└─────────────────────────────┴────────┴──────┴─────┴──────────┘

task-tracking-dashboard (ttd) — 2/8 tasks done
  ✅ ttd-1    Parse tasks.md files
  ⬜ ttd-1.1  Define TaskItem data class
  ⬜ ttd-1.2  Build Markdown checkbox parser
  ⬜ ttd-2    Generate task codes
  ✅ ttd-3    Register dynamic tasks
  ...
```

### 4. Dynamic Task Registration

At project evaluation time, scan `openspec/changes/*/tasks.md` and register:

- `openspecStatus` — the dashboard (always registered)
- `openspecTask-<code>` — one per task item (e.g., `openspecTask-ttd-1`)
  - `--set=todo|progress|done` to change status
  - Default action (no flag): print current status

### 5. TaskWriter

Updates `tasks.md` in place — changes checkbox state for a given task code.

## File Layout

```
src/main/kotlin/zone/clanker/gradle/
├── tasks/
│   └── OpenSpecStatusTask.kt
├── tracking/
│   ├── TaskParser.kt
│   ├── TaskCodeGenerator.kt
│   └── TaskWriter.kt
```

## Registration Flow

```
Plugin.apply()
  → scan openspec/changes/*/tasks.md
  → parse each into TaskItem list
  → register openspecStatus task
  → for each TaskItem: register openspecTask-<code>
```

Note: Dynamic task registration happens at configuration time, so it reads the filesystem directly (not via task inputs). This is fine — these are developer workflow tasks, not build outputs.
