Propose a new change through a design conversation.

Before writing artifacts, have a conversation. The depth of the conversation should match the complexity of the change.

- **Simple change** (version bump, rename, config tweak) — no questions needed, go straight to artifacts.
- **Medium change** (add a feature, fix a bug with clear scope) — a few clarifying questions, then write.
- **Complex change** (architecture redesign, new plugin, multi-module refactor) — deep conversation. Explore alternatives, challenge assumptions, present tradeoffs. Take your time.

**Read the room.** If the user gives you a clear, detailed description — don't ask obvious questions. If the description is vague, ambiguous, or you're not sure where things live in the codebase — ask. The conversation continues until you could write the proposal without guessing anything important.

---

## How to Converse

When questions are needed:

- Ask **one question at a time**. Don't batch.
- Use the **AskUserQuestion tool** with selectable options (2-4 choices with descriptions). Include previews when comparing approaches (code, directory structure, config).
- **Follow the thread.** If an answer raises a new question, ask it before moving on.
- **Push back respectfully.** "That could work, but X might be a problem because Y. What about Z?"
- **Research first.** Before asking "where does X live?" — look. Read the code. Then ask informed questions: "I see X is in module A, but it's also referenced in B. Should we change both?"

**What to explore** (when the change warrants it):
- What are we changing and why?
- What are the alternatives? Show 2-3 approaches with tradeoffs.
- What could break? Dependencies? Consumers?
- What's in scope vs out of scope?
- Are there things the user hasn't considered?

**When to stop:** When you can write the proposal without assuming anything important. If you'd have to guess — you haven't talked enough.

## Grooming: Split Big Work

Think of a proposal as a **ticket**, not an epic. During the conversation, watch for signs that the work is too big:

- More than ~10 tasks forming
- Multiple unrelated concerns bundled together
- "Oh, but we also need to..." keeps happening
- Different phases that could ship independently

When this happens, **suggest splitting:**

> "This is turning into 3 separate concerns: A, B, and C. Should we make them separate proposals? B depends on A, C is independent."

Use **AskUserQuestion** to let the user decide how to split. Then create multiple proposals with dependencies between them:

```yaml
# proposal B's .opsx.yaml
dependencies:
  - proposal-a
```

A group of related proposals is like an **epic**. Each proposal is a **ticket** — small enough to implement in one session, specific enough to hand to another agent.

**Signs a proposal is the right size:**
- 3-8 tasks
- One clear goal
- Can be verified independently
- Takes one session to implement

**This is grooming.** The conversation IS the grooming session. Take it seriously — splitting well prevents rework.

## Writing Artifacts

Once aligned (and split if needed), create proposal.md, design.md, tasks.md.

The proposal IS the documentation. Future sessions read it to understand the domain. Write it for the next agent, not just for execution.

Tasks must be **specific enough that an agent can execute them without guessing.** Not "create the module" but "create file X with content Y, add Z to settings.gradle.kts, verify with ./gradlew :module:compileKotlin."

If the user provides reference material (CLI help, API docs, spec files), the tasks must cover EVERY item in that material — not a summary.

**Every proposal MUST include test tasks.** No implementation is complete without tests:
- **Unit tests** for every new class — verify inputs produce correct outputs
- **Integration tests** for tasks that run external processes — verify they execute and return expected results
- **Edge case tests** — missing required properties fail with clear errors, optional properties are truly optional
- Test tasks should be separate from implementation tasks, with clear dependencies
- A task without a corresponding test task is incomplete

---

**Input**: The argument after `the opsx-propose skill` is the change name (kebab-case), OR a description of what the user wants to build.

**Steps**

1. **Start the design conversation**

   Use the **AskUserQuestion tool** to ask:
   > "What change do you want to work on? Describe what you want to build or fix."

   Then continue with follow-up questions ONE AT A TIME using AskUserQuestion with options. Do NOT proceed to creating files until the conversation has covered: what, why, where, how, risks, and scope.

   From their description, derive a kebab-case name (e.g., "add user authentication" → `add-user-auth`).

   **IMPORTANT**: Do NOT create any files until the design conversation is complete.

2. **Create the change via Gradle**
   ```bash
   ./gradlew opsx-propose --name=<name>
   ```
   This creates `opsx/changes/<name>/` with scaffolded artifacts (proposal.md, design.md, tasks.md) and auto-generated task codes.

   Alternatively, create manually:
   ```bash
   ./gradlew opsx-propose --name=<name>
   ```
   Create `.opsx.yaml` in the change directory with metadata:
   ```yaml
   name: <name>
   schema: spec-driven
   status: active
   created: <date>
   artifacts:
     - id: proposal
       path: proposal.md
       status: pending
       dependencies: []
     - id: design
       path: design.md
       status: pending
       dependencies: [proposal]
     - id: tasks
       path: tasks.md
       status: pending
       dependencies: [design]
   apply:
     requires: [tasks]
   ```

3. **Read project context** (run `./gradlew opsx-sync` to generate all):
   - `.opsx/context.md` — project config, plugins, frameworks, git info
   - `.opsx/tree.md` — source layout per module
   - `.opsx/deps.md` — dependencies with versions
   - `.opsx/modules.md` — module graph and boundaries
   - `.opsx/devloop.md` — build/test/run commands

4. **Get the artifact build order**
   Read `.opsx.yaml` to determine:
   - `apply.requires`: array of artifact IDs needed before implementation (e.g., `["tasks"]`)
   - `artifacts`: list of all artifacts with their status and dependencies

5. **Create artifacts in sequence until apply-ready**

   Use the **TodoWrite tool** to track progress through the artifacts.

   Loop through artifacts in dependency order (artifacts with no pending dependencies first):

   a. **For each artifact that is `ready` (dependencies satisfied)**:
      - Read the artifact entry from `.opsx.yaml` for guidance
      - Read any completed dependency files for context
      - Create the artifact file using appropriate structure for its type:
        - `proposal.md`: What & why — problem statement, goals, scope, success criteria
        - `design.md`: How — architecture, components, interfaces, data model
        - `tasks.md`: Implementation steps with task codes (e.g., `- [ ] \`abc-1\` Description`)
      - Update `.opsx.yaml` to mark the artifact status as `done`
      - Show brief progress: "Created <artifact-id>"

   b. **Task codes in tasks.md**
      - Each task gets a code: `<prefix>-<number>` (e.g., `aua-1`, `aua-1.1`)
      - Prefix is derived from proposal name initials (e.g., `add-user-auth` → `aua`)
      - Tasks can declare dependencies: `→ depends: aua-1, aua-2`
      - These codes become Gradle tasks: `./gradlew opsx-aua-1 --set=done`

   c. **Continue until all `apply.requires` artifacts are complete**
      - After creating each artifact, re-read `.opsx.yaml`
      - Check if every artifact ID in `apply.requires` has `status: "done"`
      - Stop when all required artifacts are done

   d. **If an artifact requires user input** (unclear context):
      - Use **AskUserQuestion tool** to clarify
      - Then continue with creation

6. **Show final status**
   Read `.opsx.yaml` and display the status of all artifacts.
   Suggest: `./gradlew opsx-status` to see the dashboard.

**Output**

After completing all artifacts, summarize:
- Change name and location
- List of artifacts created with brief descriptions
- Task codes generated (e.g., `aua-1` through `aua-5`)
- What's ready: "All artifacts created! Ready for implementation."
- Prompt: "Run `the opsx-apply skill` to start implementing, or `./gradlew opsx-status` to see the dashboard."

**Artifact Creation Guidelines**

- Follow the structure appropriate for each artifact type
- Read dependency artifacts for context before creating new ones
- **IMPORTANT**: Focus on the user's actual project — ground artifacts in their codebase
- Keep artifacts concise but complete
- Use task codes in tasks.md — they become trackable Gradle tasks

**Guardrails**
- Create ALL artifacts needed for implementation (as defined by `apply.requires`)
- Always read dependency artifacts before creating a new one
- If context is critically unclear, ask the user - but prefer making reasonable decisions to keep momentum
- If a change with that name already exists, ask if user wants to continue it or create a new one
- Verify each artifact file exists after writing before proceeding to next
