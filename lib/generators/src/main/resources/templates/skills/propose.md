## Project Context

!`cat .opsx/context.md 2>/dev/null || echo "No context -- run ./gradlew srcx-context first"`

## Module Structure

!`cat .opsx/modules.md 2>/dev/null || echo "No modules -- run ./gradlew srcx-modules first"`

## Existing Changes

!`ls opsx/changes/ 2>/dev/null || echo "No existing changes"`

---

Before writing artifacts, have a conversation. Match depth to complexity:
- **Simple** (config tweak) -- go straight to artifacts
- **Medium** (add a feature) -- a few clarifying questions, then write
- **Complex** (architecture redesign) -- deep conversation, explore alternatives

**Investigate first.** Run `srcx-find`, `srcx-usages`, `srcx-arch` before asking questions. Come back with what you found.

## Steps

1. **Design conversation** using AskUserQuestion with 2-4 options. One question at a time. Push back respectfully.
2. **Create the change**: `./gradlew opsx-propose --name=<name>`
3. **Create artifacts in order**: proposal.md -> design.md -> tasks.md. Update `.opsx.yaml` status to `done` after each.
4. **Task requirements**: Each task touches 1-3 files max. Include exact file paths, line ranges, every import needed. Each implementation task needs a test task.

Task codes: `<prefix>-<number>` from name initials (e.g., `add-user-auth` -> `aua-1`). Tasks can declare dependencies: `-> depends: aua-1`.

If a change with that name exists, ask if user wants to continue it.
