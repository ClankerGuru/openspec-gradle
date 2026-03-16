Implement tasks from an OpenSpec change.

**Input**: Optionally specify a change name (e.g., `/opsx:apply add-auth`). If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

**Steps**

1. **Select the change**

   If a name is provided, use it. Otherwise:
   - Run `./gradlew opsx-status` to see all proposals and their progress
   - Infer from conversation context if the user mentioned a change
   - Auto-select if only one active change exists
   - If ambiguous, list available changes and let the user select

   Always announce: "Using change: <name>" and how to override (e.g., `/opsx:apply <other>`).

2. **Check status to understand the schema**
   Read `openspec/changes/<name>/.openspec.yaml` to understand:
   - `schema`: The workflow being used (e.g., "spec-driven")
   - Which artifact contains the tasks (typically "tasks" for spec-driven)

3. **Read context files**

   Read the artifact files from the change directory:
   - `openspec/changes/<name>/proposal.md`
   - `openspec/changes/<name>/design.md`
   - `openspec/changes/<name>/tasks.md`

   Also read project context (run `./gradlew opsx-sync` to generate all):
   - `.openspec/context.md` — project config, plugins, frameworks, git info
   - `.openspec/tree.md` — source layout per module
   - `.openspec/deps.md` — dependencies with versions
   - `.openspec/modules.md` — module graph and boundaries
   - `.openspec/devloop.md` — build/test/run commands

   **Handle states:**
   - If required artifacts are missing: show message, suggest using `/opsx:propose` to create them
   - If all tasks are complete: congratulate, suggest archive
   - Otherwise: proceed to implementation

4. **Show current progress**

   Run `./gradlew opsx-status --proposal=<name>` or display from parsed tasks.md:
   - Schema being used
   - Progress: "N/M tasks complete"
   - Remaining tasks with their codes (e.g., `aua-3`, `aua-4`)

5. **Implement tasks (loop until done or blocked)**

   For each pending task:
   - Show which task is being worked on (include task code)
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

**Output During Implementation**

```
## Implementing: <change-name>

Working on opsx-aua-3: Add login endpoint
[...implementation happening...]
✓ opsx-aua-3 complete

Working on opsx-aua-4: Write integration tests
[...implementation happening...]
✓ opsx-aua-4 complete
```

**Output On Completion**

```
## Implementation Complete

**Change:** <change-name>
**Progress:** 7/7 tasks complete ✓

### Completed This Session
- [x] aua-1 Create User model
- [x] aua-2 Implement JWT service
...

All tasks complete! Archive with: ./gradlew opsx-archive --name=<name>
```

**Output On Pause (Issue Encountered)**

```
## Implementation Paused

**Change:** <change-name>
**Progress:** 4/7 tasks complete

### Issue Encountered
<description of the issue>

### Blocked Tasks
- aua-5: Blocked by aua-3 (TODO)

**Options:**
1. <option 1>
2. <option 2>
3. Other approach

What would you like to do?
```

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

This command supports the "actions on a change" model:

- **Can be invoked anytime**: Before all artifacts are done (if tasks exist), after partial implementation, interleaved with other actions
- **Allows artifact updates**: If implementation reveals design issues, suggest updating artifacts — not phase-locked, work fluidly
- **Gradle tasks track state**: Use `./gradlew opsx-status` anytime to see where things stand
