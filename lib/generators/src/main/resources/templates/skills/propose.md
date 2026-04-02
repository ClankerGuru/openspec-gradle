Propose a new change through a design conversation.

Before writing artifacts, have a conversation. Match depth to complexity:

- **Simple** (version bump, config tweak) — go straight to artifacts
- **Medium** (add a feature, fix a scoped bug) — a few clarifying questions, then write
- **Complex** (architecture redesign, multi-module refactor) — deep conversation. Explore alternatives, challenge assumptions, present tradeoffs.

**Read the room.** If the user gives a clear, detailed description — don't ask obvious questions. If vague or ambiguous — ask.

---

## How to Converse

- Ask **one question at a time**. Don't batch.
- Use the **AskUserQuestion tool** with 2-4 selectable options and previews.
- **Follow the thread.** If an answer raises a new question, ask it before moving on.
- **Push back respectfully.** "That could work, but X might be a problem because Y."
- **Research first.** Before asking "where does X live?" — look. Then ask informed questions.

**What to explore** (when warranted): What are we changing and why? What are the alternatives (2-3 approaches with tradeoffs)? What could break? What's in scope vs out?

**When to stop:** When you can write the proposal without guessing anything important.

## Investigate First

Before asking questions, **go look.** Run `srcx-find`, `srcx-usages`, `srcx-arch`. Then come back with what you found:

> "I looked at the codebase. This class is in module A but referenced in B and C."

Now your questions are informed.

## The Full Cycle

A proposal's tasks should cover: tests first (TDD) -> implement -> verify (build passes) -> PR -> docs updates.

---

**Input**: Change name (kebab-case) or description of what to build.

**Steps**

1. **Design conversation** — If the change is clear and simple, proceed directly to artifacts. Otherwise, use AskUserQuestion to cover what, why, where, how, risks, scope. Do NOT create files until conversation is complete.

2. **Create the change**:
   ```bash
   ./gradlew opsx-propose --name=<name>
   ```

3. **Read project context** (run `./gradlew opsx-sync` to generate):
   - `.opsx/context.md`, `.opsx/tree.md`, `.opsx/deps.md`, `.opsx/modules.md`, `.opsx/devloop.md`

4. **Read `.opsx.yaml`** for artifact build order and `apply.requires`

5. **Create artifacts in dependency order** until apply-ready:
   - `proposal.md`: What & why — problem, goals, scope, success criteria
   - `design.md`: How — architecture, components, interfaces, data model
   - `tasks.md`: Implementation steps with task codes (`- [ ] \`abc-1\` Description`)
   - Update `.opsx.yaml` status to `done` after each

   **Task codes**: `<prefix>-<number>` from name initials (e.g., `add-user-auth` -> `aua-1`). Tasks can declare dependencies: `-> depends: aua-1, aua-2`

6. **Show final status** and suggest `./gradlew opsx-status`

**Task requirements**: Each task should touch 1-3 files max. Include exact file paths, line ranges, specific changes, every import needed. Every implementation task needs a corresponding test task.

**Guardrails**
- Create ALL artifacts needed for `apply.requires`
- Read dependency artifacts before creating new ones
- If a change with that name exists, ask if user wants to continue it
- Ground artifacts in the user's actual codebase
