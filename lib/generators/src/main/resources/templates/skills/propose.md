## Project Context

!`cat .opsx/context.md 2>/dev/null || echo "No context -- run ./gradlew srcx-context first"`

## Module Structure

!`cat .opsx/modules.md 2>/dev/null || echo "No modules -- run ./gradlew srcx-modules first"`

## Existing Changes

!`ls opsx/changes/ 2>/dev/null || echo "No existing changes"`

---

**STOP. Do NOT write artifacts yet.**

This is a collaborative design process. You lay out the plan, present options at every design fork, and let the user decide. You never make design decisions silently.

**Investigate first.** Run `srcx-find`, `srcx-usages`, `srcx-arch` before talking. Come back with what you found.

## Steps

1. **Lay out the plan.** Present the steps you intend to follow. Ask: "Does this approach make sense, or would you structure it differently?"

2. **Walk through each design decision.** For every decision point (naming, architecture, boundaries, tooling, phasing):
   - Present **options** (A, B, C) with tradeoffs — or "do something else entirely"
   - Ask about **constraints** — what's non-negotiable, what's flexible
   - **Wait for the user to choose** before moving to the next decision
   - One decision at a time. Do not batch them.

3. **Summarize and confirm.** Before writing anything, recap all decisions made and get explicit go-ahead.

4. **Create the change**: `./gradlew opsx-propose --name=<name>`

5. **Create artifacts in order**: proposal.md → design.md → tasks.md. Update `.opsx.yaml` status to `done` after each.

6. **Task requirements**: Each task touches 1-3 files max. Include exact file paths, line ranges, every import needed. Each implementation task needs a test task.

Task codes: `<prefix>-<number>` from name initials (e.g., `add-user-auth` -> `aua-1`). Tasks can declare dependencies: `-> depends: aua-1`.

If a change with that name exists, ask if user wants to continue it.
