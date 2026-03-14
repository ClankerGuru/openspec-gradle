---
name: openspec-apply-change
description: "Implement tasks from an OpenSpec change. Use when the user wants to start implementing, continue implementation, or work through tasks."
license: MIT
compatibility: Requires Gradle build system.
metadata:
  author: "openspec-gradle"
  version: "1.0"
  generatedBy: "openspec-gradle:0.1.0"
---

Implement tasks from an OpenSpec change.

**Input**: The user's request should include a change name, or it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

**Steps**

1. **Select the change**

   If a name is provided, use it. Otherwise:
   - Infer from conversation context if the user mentioned a change
   - Auto-select if only one active change exists
   - If ambiguous, list available changes:
     ```bash
     find openspec/changes -maxdepth 1 -mindepth 1 -type d -not -name archive
     ```
     Use the **AskUserQuestion tool** to let the user select.

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

   **Handle states:**
   - If required artifacts are missing: show message, suggest creating them first
   - If all tasks are complete: congratulate, suggest archive
   - Otherwise: proceed to implementation

4. **Show current progress**

   Display:
   - Schema being used
   - Progress: "N/M tasks complete"
   - Remaining tasks overview

5. **Implement tasks (loop until done or blocked)**

   For each pending task:
   - Show which task is being worked on
   - Make the code changes required
   - Keep changes minimal and focused
   - Mark task complete in the tasks file: `- [ ]` → `- [x]`
   - Continue to next task

   **Pause if:**
   - Task is unclear → ask for clarification
   - Implementation reveals a design issue → suggest updating artifacts
   - Error or blocker encountered → report and wait for guidance
   - User interrupts

6. **On completion or pause, show status**

   Display:
   - Tasks completed this session
   - Overall progress: "N/M tasks complete"
   - If all done: suggest archive
   - If paused: explain why and wait for guidance

**Guardrails**
- Keep going through tasks until done or blocked
- Always read context files before starting
- If task is ambiguous, pause and ask before implementing
- If implementation reveals issues, pause and suggest artifact updates
- Keep code changes minimal and scoped to each task
- Update task checkbox immediately after completing each task
- Pause on errors, blockers, or unclear requirements - don't guess

**Fluid Workflow Integration**

This skill supports the "actions on a change" model:

- **Can be invoked anytime**: Before all artifacts are done (if tasks exist), after partial implementation, interleaved with other actions
- **Allows artifact updates**: If implementation reveals design issues, suggest updating artifacts - not phase-locked, work fluidly
