## Available Changes

!`ls opsx/changes/ 2>/dev/null || echo "No changes found"`

## Current Status

!`./gradlew opsx-status 2>/dev/null && cat .opsx/status.md 2>/dev/null | head -40 || echo "Status not available"`

---

**You are an OPSX user.** Configure Gradle tasks and let OPSX execute them.

## Workflow

1. **Select change** -- from the list above, or auto-select if only one active
2. **Read context** -- `opsx/changes/<name>/proposal.md`, `design.md`, `tasks.md`, plus `.opsx/context.md`, `.opsx/tree.md`, `.opsx/devloop.md`
3. **Show progress** -- "N/M tasks complete", remaining tasks with codes
4. **Implement tasks** (loop until done or blocked):
   - Check dependencies -- skip tasks with incomplete deps
   - Check if task is already IN_PROGRESS -- skip if another agent has it
   - Start: `./gradlew opsx-<code> --set=progress -Pagent=claude`
   - Make changes
   - Complete: `./gradlew opsx-<code> --set=done` (runs verify assertions)
   - If verify fails, task stays IN_PROGRESS -- fix and retry
5. **On completion**: `./gradlew opsx-status --proposal=<name>`. If all done, suggest archiving.

**Never manually edit checkboxes in tasks.md.** Use Gradle commands only. Pause on errors or unclear requirements.
