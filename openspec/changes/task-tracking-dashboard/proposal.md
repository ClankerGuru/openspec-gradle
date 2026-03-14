# Proposal: Task Tracking Dashboard

## Problem

When a proposal is created via `openspecPropose`, a `tasks.md` file is generated with implementation steps. But there's no way to:

1. **Track task status** from Gradle — you have to open the file and read it
2. **See a dashboard** of all proposals and their progress
3. **Execute individual tasks** as Gradle tasks with status outputs

The workflow is disconnected from the build tool.

## Goals

- Each task in `tasks.md` gets a **normalized code** (e.g., `ttd-1`, `ttd-2.1`) derived from the proposal name + task position
- Gradle **dynamically registers tasks** for each tracked item (e.g., `./gradlew openspecTask-ttd-1`)
- Running a task-tracking Gradle task outputs its status: `TODO`, `IN_PROGRESS`, or `DONE`
- A **dashboard task** (`openspecDashboard` or `openspecStatus`) aggregates all proposals and their task statuses into a formatted summary
- Status is persisted by updating `tasks.md` checkbox state (`- [ ]` → `- [x]`)

## Scope

- **In scope**: Task code generation, dynamic Gradle task registration, status dashboard, `tasks.md` parsing/updating
- **Out of scope**: CI integration, remote state sync, notifications

## Success Criteria

- Running `./gradlew openspecStatus` prints a table of all proposals with task completion percentages
- Running `./gradlew openspecTask-ttd-1 --status=done` marks a task as complete
- Task codes are stable (derived from proposal name + hierarchy) and short
- Works with the existing `openspecPropose` → `openspecApply` workflow
