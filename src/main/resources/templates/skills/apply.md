Implement tasks from an OpenSpec change.

**Input**: The user's request should include a change name, or it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

**Steps**

1. **Select the change**

   If a name is provided, use it. Otherwise:
   - Run `./gradlew opsx-status` to see all proposals and their progress
   - Infer from conversation context if the user mentioned a change
   - Auto-select if only one active change exists
   - If ambiguous, let the user select

   Always announce: "Using change: <name>".

2. **Check status to understand the schema**
   Read `openspec/changes/<name>/.openspec.yaml` to understand:
   - `schema`: The workflow being used (e.g., "spec-driven")
   - Which artifact contains the tasks (typically "tasks" for spec-driven)

3. **Read context files**

   Read the artifact files from the change directory:
   - `openspec/changes/<name>/proposal.md`
   - `openspec/changes/<name>/design.md`
   - `openspec/changes/<name>/tasks.md`

   Also read project context: `.openspec/context.md` (run `./gradlew opsx-context` if missing)

   **Handle states:**
   - If required artifacts are missing: show message, suggest creating them first
   - If all tasks are complete: congratulate, suggest `./gradlew opsx-archive --name=<name>`
   - Otherwise: proceed to implementation

4. **Show current progress**

   Run `./gradlew opsx-status --proposal=<name>` or display from parsed tasks.md:
   - Schema being used
   - Progress: "N/M tasks complete"
   - Remaining tasks with their codes

5. **Implement tasks (loop until done or blocked)**

   For each pending task:
   - Show which task is being worked on (include task code, e.g., `aua-3`)
   - Check dependencies: if task has `→ depends:` on incomplete tasks, skip it
   - Make the code changes required
   - Keep changes minimal and focused
   - Mark task complete: `./gradlew opsx-<code> --set=done` or update checkbox in tasks.md
   - Continue to next task

   **Pause if:**
   - Task is unclear → ask for clarification
   - Task is blocked by dependencies → move to next unblocked task
   - Implementation reveals a design issue → suggest updating artifacts
   - Error or blocker encountered → report and wait for guidance
   - User interrupts

6. **On completion or pause, show status**

   Run `./gradlew opsx-status --proposal=<name>` to display:
   - Tasks completed this session
   - Overall progress
   - If all done: suggest `./gradlew opsx-archive --name=<name>`
   - If paused: explain why and wait for guidance

**Guardrails**
- Keep going through tasks until done or blocked
- Always read context files before starting
- Respect task dependencies — don't implement a task whose deps aren't done
- If task is ambiguous, pause and ask before implementing
- If implementation reveals issues, pause and suggest artifact updates
- Keep code changes minimal and scoped to each task
- Update task status immediately after completing each task (via Gradle or checkbox)
- Pause on errors, blockers, or unclear requirements — don't guess

**Fluid Workflow Integration**

This skill supports the "actions on a change" model:

- **Can be invoked anytime**: Before all artifacts are done (if tasks exist), after partial implementation, interleaved with other actions
- **Allows artifact updates**: If implementation reveals design issues, suggest updating artifacts — not phase-locked, work fluidly
- **Gradle tasks track state**: Use `./gradlew opsx-status` anytime to see where things stand
