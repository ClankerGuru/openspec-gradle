Implement tasks from an OPSX change.

**You are an OPSX user.** You configure Gradle tasks and let OPSX execute them. Independent tasks run in parallel.

**Input**: Change name, or infer from context. If ambiguous, prompt for selection.

**Parallel execution**: If multiple tasks have no dependencies, execute simultaneously with `./gradlew opsx-exec -Ptask=code1,code2,code3 -Pparallel=true` or separate background processes.

**Steps**

1. **Select the change**
   - If provided, use it. Otherwise run `./gradlew opsx-status`
   - Auto-select if only one active change exists
   - Announce: "Using change: <name>"

2. **Read context**
   - `opsx/changes/<name>/.opsx.yaml` — schema and artifact status
   - `opsx/changes/<name>/proposal.md`, `design.md`, `tasks.md`
   - `.opsx/context.md`, `.opsx/tree.md`, `.opsx/modules.md`, `.opsx/devloop.md`

3. **Show progress**: schema, "N/M tasks complete", remaining tasks with codes

4. **Implement tasks** (loop until done or blocked):
   - Check dependencies — skip tasks with incomplete deps
   - **Before starting**: `./gradlew opsx-<code> --set=progress`
   - Make the code changes
   - **After completing**: `./gradlew opsx-<code> --set=done`
   - `--set=done` runs verify assertions. If they fail, task stays IN_PROGRESS — fix and retry
   - Only use `--force` when the user explicitly asks to skip verification

   **Never manually edit checkboxes in tasks.md.** Use Gradle commands only.

   **Pause if**: task is unclear, blocked by deps, reveals design issues, or errors occur

5. **On completion**: run `./gradlew opsx-status --proposal=<name>`. If all done, suggest archiving.

**Guardrails**
- Keep going through tasks until done or blocked
- Respect task dependencies
- Keep changes minimal and scoped to each task
- Pause on errors or unclear requirements — don't guess
