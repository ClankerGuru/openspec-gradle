# Proposal: Task Dependency Graph

## Problem

When `openspecApply` runs, it needs to know which task to execute. Tasks have dependencies — you can't implement `ttd-3.1` (inject codes into tasks.md) if `ttd-2` (build TaskParser) isn't done yet. Currently there's no way to:

1. **Declare dependencies between tasks** in `tasks.md`
2. **Enforce execution order** — Gradle should refuse to run a task whose dependencies aren't complete
3. **Apply a single task or all tasks** via `openspecApply --task=ttd-3.1` or `openspecApply --all`

## Goals

- Define task dependencies in `tasks.md` using a simple, readable syntax
- Map those dependencies to **Gradle task dependencies** so `./gradlew openspecTask-ttd-3.1` automatically depends on `openspecTask-ttd-2`
- `openspecApply` accepts `--task=<code>` to apply a single task (respecting deps) or `--all` to run everything in order
- Dependencies are validated at configuration time — fail fast if a cycle is detected

## The Hard Problem

Gradle task dependencies are declared at **configuration time** (plugin apply), but task status lives in `tasks.md` which is a **runtime** artifact. Two options:

1. **Gradle `dependsOn` + `onlyIf`**: Wire all deps via `dependsOn`, use `onlyIf { parentTaskDone() }` to skip tasks whose deps aren't marked done in `tasks.md`
2. **Validation-only**: Don't use Gradle's `dependsOn` for ordering. Instead, at task execution time, read `tasks.md`, check all deps are `DONE`, and fail with a clear message if not.

Option 2 is simpler and more honest — these aren't build tasks, they're workflow tasks. Gradle's task graph is designed for build outputs, not human workflow state.

**Recommendation**: Option 2 (validation at execution time) with Gradle `dependsOn` only for the natural parent→child hierarchy (task 2 depends on 2.1, 2.2, 2.3 being done). Cross-cutting deps declared explicitly.

## Scope

- **In scope**: Dependency syntax in tasks.md, validation at execution, apply with --task flag, cycle detection
- **Out of scope**: Automatic dependency inference, parallel task execution, remote state

## Success Criteria

- `tasks.md` supports a `depends: ttd-1, ttd-2` syntax per task
- `./gradlew openspecTask-ttd-3.1` fails with "Blocked by: ttd-2 (TODO)" if deps aren't done
- `./gradlew openspecApply --task=ttd-3.1` runs the apply workflow scoped to that task
- `./gradlew openspecApply --all` runs all tasks in dependency order, stopping at first failure
- No cycles allowed — detected at parse time
