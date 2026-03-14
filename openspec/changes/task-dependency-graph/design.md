# Design: Task Dependency Graph

## Dependency Syntax in tasks.md

Two types of dependencies:

### 1. Implicit (hierarchy)

Parent tasks implicitly depend on all their children. Task `ttd-2` is done only when `ttd-2.1`, `ttd-2.2`, `ttd-2.3` are all done. This is already natural from the nesting.

### 2. Explicit (cross-cutting)

Declared inline after the task description using `→ depends: <codes>`:

```markdown
- [ ] `ttd-3` Build TaskCodeGenerator → depends: ttd-2
  - [ ] `ttd-3.1` Prefix extraction
  - [ ] `ttd-3.2` Code assignment → depends: ttd-3.1
  - [ ] `ttd-3.3` Inject codes into tasks.md → depends: ttd-2, ttd-3.2
```

The `→ depends:` syntax is:
- Visually distinct (arrow separator)
- Parseable (regex: `→\s*depends:\s*(.+)$`)
- Doesn't break standard Markdown rendering (shows as text)
- Optional — omit if no cross-cutting deps

### Alternative considered: YAML frontmatter block

```markdown
---
dependencies:
  ttd-3: [ttd-2]
  ttd-3.3: [ttd-2, ttd-3.2]
---
```

Rejected because: separates dependency info from the task it belongs to. Harder to maintain.

## Dependency Resolution

### At Configuration Time (plugin apply)

```kotlin
// 1. Scan openspec/changes/*/tasks.md
// 2. Parse into TaskItem tree with dependencies
// 3. Build dependency graph
// 4. Detect cycles (topological sort — fail if cycle found)
// 5. Register Gradle tasks with dependsOn for hierarchy only
```

Gradle `dependsOn` wires **parent → children** only:
```
openspecTask-ttd-2 dependsOn [openspecTask-ttd-2.1, openspecTask-ttd-2.2, openspecTask-ttd-2.3]
```

This means running `openspecTask-ttd-2` runs all subtasks first. That's the natural Gradle way.

### At Execution Time (task action)

Cross-cutting deps are validated, not wired:

```kotlin
@TaskAction
fun execute() {
    val allTasks = TaskParser.parse(tasksFile)
    val thisTask = allTasks.findByCode(code)
    
    // Check explicit dependencies
    for (depCode in thisTask.explicitDeps) {
        val dep = allTasks.findByCode(depCode)
        if (dep.status != DONE) {
            throw GradleException(
                "Task $code is blocked by $depCode (${dep.status}). " +
                "Complete it first: ./gradlew openspecTask-$depCode --set=done"
            )
        }
    }
    
    // Proceed with task action (print status or update)
}
```

**Why not use Gradle `dependsOn` for everything?**

Because cross-cutting deps are about *workflow state* (is the task marked done in a file?), not build outputs. If we wired `openspecTask-ttd-3 dependsOn openspecTask-ttd-2`, running ttd-3 would *execute* ttd-2's action again, which isn't what we want. We want to *check* that ttd-2 is done.

## openspecApply Integration

```
./gradlew openspecApply --task=ttd-3.1
```

1. Parse `tasks.md`, find `ttd-3.1`
2. Resolve all transitive deps (explicit + parent hierarchy)
3. Validate all deps are `DONE`
4. If blocked → fail with clear message showing what's missing
5. If clear → run the apply workflow scoped to that task's description

```
./gradlew openspecApply --all
```

1. Topological sort all tasks
2. Execute in order, respecting deps
3. Stop at first task that can't proceed (deps not met AND not completable in this run)

## Cycle Detection

Standard topological sort with Kahn's algorithm:

```kotlin
object DependencyGraph {
    fun topologicalSort(tasks: List<TaskItem>): List<TaskItem>  // throws if cycle
    fun detectCycle(tasks: List<TaskItem>): List<String>?       // returns cycle path or null
    fun blockers(task: TaskItem, allTasks: List<TaskItem>): List<TaskItem>  // incomplete deps
}
```

Run at parse time. If a cycle exists, fail with:
```
Dependency cycle detected: ttd-1 → ttd-3 → ttd-2 → ttd-1
```

## File Layout

```
src/main/kotlin/zone/clanker/gradle/tracking/
├── TaskParser.kt          // (from ttd proposal)
├── TaskCodeGenerator.kt   // (from ttd proposal)
├── TaskWriter.kt          // (from ttd proposal)
├── DependencyGraph.kt     // NEW — cycle detection, topo sort, blocker resolution
└── TaskDependencyParser.kt // NEW — parses → depends: syntax
```

## Enhanced TaskItem

```kotlin
data class TaskItem(
    val code: String,
    val description: String,
    val status: TaskStatus,
    val children: List<TaskItem>,
    val explicitDeps: List<String>,  // NEW — cross-cutting dep codes
)
```
