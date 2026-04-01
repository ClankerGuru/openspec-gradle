---
name: opsx-propose
description: "Create a change proposal with design, specs, and tasks. Use when planning work, building a feature, or fixing a bug."
---

<!-- openspec-gradle:0.33.0 -->

Propose a new change - create the change and generate all artifacts in one step.

I'll create a change with artifacts:
- proposal.md (what & why)
- design.md (how)
- tasks.md (implementation steps with task codes)

When ready to implement, run the opsx-apply skill

---

## Planning Philosophy

**Spend more time planning.** A good proposal prevents rework. A bad proposal wastes tokens on failed execution.

Before writing artifacts:
- **Ask questions** if the request is vague — don't assume
- **Enumerate exhaustively** — if wrapping a CLI, list every flag; if modifying files, list every file
- **Call out gotchas** — "This will be tricky because X", "Watch out for Y"
- **Show key decisions** — "These are the 3 things that matter most"
- **Warn about risks** — "If we do A, then B might break"

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

1. **If no input provided, ask what they want to build**

   Use the **AskUserQuestion tool** (open-ended, no preset options) to ask:
   > "What change do you want to work on? Describe what you want to build or fix."

   From their description, derive a kebab-case name (e.g., "add user authentication" → `add-user-auth`).

   **IMPORTANT**: Do NOT proceed without understanding what the user wants to build.

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

