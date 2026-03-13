package zone.clanker.gradle.templates

import zone.clanker.gradle.generators.CommandContent
import zone.clanker.gradle.generators.SkillContent

/**
 * Registry of all embedded prompt/skill templates.
 * Templates reference filesystem operations instead of openspec CLI.
 */
object TemplateRegistry {

    fun getCommandTemplates(): List<CommandContent> = listOf(
        proposeCommand(),
        applyCommand(),
        archiveCommand(),
        exploreCommand(),
        newCommand(),
        syncCommand(),
        verifyCommand()
    )

    fun getSkillTemplates(): List<SkillContent> = listOf(
        proposeSkill(),
        applySkill(),
        archiveSkill(),
        exploreSkill(),
        newSkill(),
        syncSkill(),
        verifySkill()
    )

    // ── Propose ──────────────────────────────────────────

    private fun proposeCommand() = CommandContent(
        id = "propose",
        name = "OPSX: Propose",
        description = "Propose a new change - create it and generate all artifacts in one step",
        category = "Workflow",
        tags = listOf("workflow", "artifacts", "experimental"),
        body = """Propose a new change - create the change and generate all artifacts in one step.

I'll create a change with artifacts:
- proposal.md (what & why)
- design.md (how)
- tasks.md (implementation steps)

When ready to implement, run /opsx:apply

---

**Input**: The argument after `/opsx:propose` is the change name (kebab-case), OR a description of what the user wants to build.

**Steps**

1. **If no input provided, ask what they want to build**

   Use the **AskUserQuestion tool** (open-ended, no preset options) to ask:
   > "What change do you want to work on? Describe what you want to build or fix."

   From their description, derive a kebab-case name (e.g., "add user authentication" → `add-user-auth`).

   **IMPORTANT**: Do NOT proceed without understanding what the user wants to build.

2. **Create the change directory**
   ```bash
   mkdir -p openspec/changes/<name>
   ```
   Create `.openspec.yaml` in the change directory with metadata:
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

3. **Get the artifact build order**
   Read `.openspec.yaml` to determine:
   - `apply.requires`: array of artifact IDs needed before implementation (e.g., `["tasks"]`)
   - `artifacts`: list of all artifacts with their status and dependencies

4. **Create artifacts in sequence until apply-ready**

   Use the **TodoWrite tool** to track progress through the artifacts.

   Loop through artifacts in dependency order (artifacts with no pending dependencies first):

   a. **For each artifact that is `ready` (dependencies satisfied)**:
      - Read the artifact entry from `.openspec.yaml` for guidance
      - Read any completed dependency files for context
      - Create the artifact file using appropriate structure for its type:
        - `proposal.md`: What & why — problem statement, goals, scope, success criteria
        - `design.md`: How — architecture, components, interfaces, data model
        - `tasks.md`: Implementation steps — ordered checklist with `- [ ]` items
      - Update `.openspec.yaml` to mark the artifact status as `done`
      - Show brief progress: "Created <artifact-id>"

   b. **Continue until all `apply.requires` artifacts are complete**
      - After creating each artifact, re-read `.openspec.yaml`
      - Check if every artifact ID in `apply.requires` has `status: "done"`
      - Stop when all required artifacts are done

   c. **If an artifact requires user input** (unclear context):
      - Use **AskUserQuestion tool** to clarify
      - Then continue with creation

5. **Show final status**
   Read `.openspec.yaml` and display the status of all artifacts.

**Output**

After completing all artifacts, summarize:
- Change name and location
- List of artifacts created with brief descriptions
- What's ready: "All artifacts created! Ready for implementation."
- Prompt: "Run `/opsx:apply` to start implementing."

**Artifact Creation Guidelines**

- Follow the structure appropriate for each artifact type
- Read dependency artifacts for context before creating new ones
- **IMPORTANT**: Focus on the user's actual project — ground artifacts in their codebase
- Keep artifacts concise but complete

**Guardrails**
- Create ALL artifacts needed for implementation (as defined by `apply.requires`)
- Always read dependency artifacts before creating a new one
- If context is critically unclear, ask the user - but prefer making reasonable decisions to keep momentum
- If a change with that name already exists, ask if user wants to continue it or create a new one
- Verify each artifact file exists after writing before proceeding to next"""
    )

    private fun proposeSkill() = SkillContent(
        dirName = "openspec-propose",
        description = "Propose a new change with all artifacts generated in one step. Use when the user wants to quickly describe what they want to build and get a complete proposal with design, specs, and tasks ready for implementation.",
        instructions = """Propose a new change - create the change and generate all artifacts in one step.

I'll create a change with artifacts:
- proposal.md (what & why)
- design.md (how)
- tasks.md (implementation steps)

When ready to implement, use the openspec-apply-change skill.

---

**Input**: The user's request should include a change name (kebab-case), OR a description of what they want to build.

**Steps**

1. **If no input provided, ask what they want to build**

   Use the **AskUserQuestion tool** (open-ended, no preset options) to ask:
   > "What change do you want to work on? Describe what you want to build or fix."

   From their description, derive a kebab-case name (e.g., "add user authentication" → `add-user-auth`).

   **IMPORTANT**: Do NOT proceed without understanding what the user wants to build.

2. **Create the change directory**
   ```bash
   mkdir -p openspec/changes/<name>
   ```
   Create `.openspec.yaml` in the change directory with metadata:
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

3. **Get the artifact build order**
   Read `.openspec.yaml` to determine:
   - `apply.requires`: array of artifact IDs needed before implementation (e.g., `["tasks"]`)
   - `artifacts`: list of all artifacts with their status and dependencies

4. **Create artifacts in sequence until apply-ready**

   Use the **TodoWrite tool** to track progress through the artifacts.

   Loop through artifacts in dependency order (artifacts with no pending dependencies first):

   a. **For each artifact that is `ready` (dependencies satisfied)**:
      - Read the artifact entry from `.openspec.yaml` for guidance
      - Read any completed dependency files for context
      - Create the artifact file using appropriate structure for its type:
        - `proposal.md`: What & why — problem statement, goals, scope, success criteria
        - `design.md`: How — architecture, components, interfaces, data model
        - `tasks.md`: Implementation steps — ordered checklist with `- [ ]` items
      - Update `.openspec.yaml` to mark the artifact status as `done`
      - Show brief progress: "Created <artifact-id>"

   b. **Continue until all `apply.requires` artifacts are complete**
      - After creating each artifact, re-read `.openspec.yaml`
      - Check if every artifact ID in `apply.requires` has `status: "done"`
      - Stop when all required artifacts are done

   c. **If an artifact requires user input** (unclear context):
      - Use **AskUserQuestion tool** to clarify
      - Then continue with creation

5. **Show final status**
   Read `.openspec.yaml` and display the status of all artifacts.

**Output**

After completing all artifacts, summarize:
- Change name and location
- List of artifacts created with brief descriptions
- What's ready: "All artifacts created! Ready for implementation."
- Prompt: "Use the openspec-apply-change skill to start implementing."

**Artifact Creation Guidelines**

- Follow the structure appropriate for each artifact type
- Read dependency artifacts for context before creating new ones
- **IMPORTANT**: Focus on the user's actual project — ground artifacts in their codebase
- Keep artifacts concise but complete

**Guardrails**
- Create ALL artifacts needed for implementation (as defined by `apply.requires`)
- Always read dependency artifacts before creating a new one
- If context is critically unclear, ask the user - but prefer making reasonable decisions to keep momentum
- If a change with that name already exists, ask if user wants to continue it or create a new one
- Verify each artifact file exists after writing before proceeding to next"""
    )

    // ── Apply ────────────────────────────────────────────

    private fun applyCommand() = CommandContent(
        id = "apply",
        name = "OPSX: Apply",
        description = "Implement tasks from an OpenSpec change (Experimental)",
        category = "Workflow",
        tags = listOf("workflow", "implementation"),
        body = """Implement tasks from an OpenSpec change.

**Input**: Optionally specify a change name (e.g., `/opsx:apply add-auth`). If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

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

   **Handle states:**
   - If required artifacts are missing: show message, suggest using `/opsx:propose` to create them
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

**Output During Implementation**

```
## Implementing: <change-name> (schema: <schema-name>)

Working on task 3/7: <task description>
[...implementation happening...]
✓ Task complete

Working on task 4/7: <task description>
[...implementation happening...]
✓ Task complete
```

**Output On Completion**

```
## Implementation Complete

**Change:** <change-name>
**Schema:** <schema-name>
**Progress:** 7/7 tasks complete ✓

### Completed This Session
- [x] Task 1
- [x] Task 2
...

All tasks complete! You can archive this change with `/opsx:archive`.
```

**Output On Pause (Issue Encountered)**

```
## Implementation Paused

**Change:** <change-name>
**Schema:** <schema-name>
**Progress:** 4/7 tasks complete

### Issue Encountered
<description of the issue>

**Options:**
1. <option 1>
2. <option 2>
3. Other approach

What would you like to do?
```

**Guardrails**
- Keep going through tasks until done or blocked
- Always read context files before starting
- If task is ambiguous, pause and ask before implementing
- If implementation reveals issues, pause and suggest artifact updates
- Keep code changes minimal and scoped to each task
- Update task checkbox immediately after completing each task
- Pause on errors, blockers, or unclear requirements - don't guess

**Fluid Workflow Integration**

This command supports the "actions on a change" model:

- **Can be invoked anytime**: Before all artifacts are done (if tasks exist), after partial implementation, interleaved with other actions
- **Allows artifact updates**: If implementation reveals design issues, suggest updating artifacts - not phase-locked, work fluidly"""
    )

    private fun applySkill() = SkillContent(
        dirName = "openspec-apply-change",
        description = "Implement tasks from an OpenSpec change. Use when the user wants to start implementing, continue implementation, or work through tasks.",
        instructions = """Implement tasks from an OpenSpec change.

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
- **Allows artifact updates**: If implementation reveals design issues, suggest updating artifacts - not phase-locked, work fluidly"""
    )

    // ── Archive ──────────────────────────────────────────

    private fun archiveCommand() = CommandContent(
        id = "archive",
        name = "OPSX: Archive",
        description = "Archive a completed change in the experimental workflow",
        category = "Workflow",
        tags = listOf("workflow", "archive"),
        body = """Archive a completed change in the experimental workflow.

**Input**: Optionally specify a change name after `/opsx:archive` (e.g., `/opsx:archive add-auth`). If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

**Steps**

1. **If no change name provided, prompt for selection**

   List available changes:
   ```bash
   find openspec/changes -maxdepth 1 -mindepth 1 -type d -not -name archive
   ```
   Use the **AskUserQuestion tool** to let the user select.

   Show only active changes (not already archived).

   **IMPORTANT**: Do NOT guess or auto-select a change. Always let the user choose.

2. **Check artifact completion status**

   Read `openspec/changes/<name>/.openspec.yaml` to check artifact completion.

   Parse the YAML to understand:
   - `schema`: The workflow being used
   - `artifacts`: List of artifacts with their status (`done` or other)

   **If any artifacts are not `done`:**
   - Display warning listing incomplete artifacts
   - Prompt user for confirmation to continue
   - Proceed if user confirms

3. **Check task completion status**

   Read the tasks file (typically `tasks.md`) to check for incomplete tasks.

   Count tasks marked with `- [ ]` (incomplete) vs `- [x]` (complete).

   **If incomplete tasks found:**
   - Display warning showing count of incomplete tasks
   - Prompt user for confirmation to continue
   - Proceed if user confirms

   **If no tasks file exists:** Proceed without task-related warning.

4. **Assess delta spec sync state**

   Check for delta specs at `openspec/changes/<name>/specs/`. If none exist, proceed without sync prompt.

   **If delta specs exist:**
   - Compare each delta spec with its corresponding main spec at `openspec/specs/<capability>/spec.md`
   - Determine what changes would be applied (adds, modifications, removals, renames)
   - Show a combined summary before prompting

   **Prompt options:**
   - If changes needed: "Sync now (recommended)", "Archive without syncing"
   - If already synced: "Archive now", "Sync anyway", "Cancel"

   If user chooses sync, perform the sync before archiving.

5. **Perform the archive**

   Create the archive directory if it doesn't exist:
   ```bash
   mkdir -p openspec/changes/archive
   ```

   Generate target name using current date: `YYYY-MM-DD-<change-name>`

   **Check if target already exists:**
   - If yes: Fail with error, suggest renaming existing archive or using different date
   - If no: Move the change directory to archive

   ```bash
   mv openspec/changes/<name> openspec/changes/archive/YYYY-MM-DD-<name>
   ```

6. **Display summary**

   Show archive completion summary including:
   - Change name
   - Schema that was used
   - Archive location
   - Spec sync status (synced / sync skipped / no delta specs)
   - Note about any warnings (incomplete artifacts/tasks)

**Output On Success**

```
## Archive Complete

**Change:** <change-name>
**Schema:** <schema-name>
**Archived to:** openspec/changes/archive/YYYY-MM-DD-<name>/
**Specs:** ✓ Synced to main specs

All artifacts complete. All tasks complete.
```

**Output On Success (No Delta Specs)**

```
## Archive Complete

**Change:** <change-name>
**Schema:** <schema-name>
**Archived to:** openspec/changes/archive/YYYY-MM-DD-<name>/
**Specs:** No delta specs

All artifacts complete. All tasks complete.
```

**Output On Success With Warnings**

```
## Archive Complete (with warnings)

**Change:** <change-name>
**Schema:** <schema-name>
**Archived to:** openspec/changes/archive/YYYY-MM-DD-<name>/
**Specs:** Sync skipped (user chose to skip)

**Warnings:**
- Archived with 2 incomplete artifacts
- Archived with 3 incomplete tasks
- Delta spec sync was skipped (user chose to skip)

Review the archive if this was not intentional.
```

**Output On Error (Archive Exists)**

```
## Archive Failed

**Change:** <change-name>
**Target:** openspec/changes/archive/YYYY-MM-DD-<name>/

Target archive directory already exists.

**Options:**
1. Rename the existing archive
2. Delete the existing archive if it's a duplicate
3. Wait until a different date to archive
```

**Guardrails**
- Always prompt for change selection if not provided
- Don't block archive on warnings - just inform and confirm
- Preserve .openspec.yaml when moving to archive (it moves with the directory)
- Show clear summary of what happened
- If delta specs exist, always run the sync assessment and show the combined summary before prompting"""
    )

    private fun archiveSkill() = SkillContent(
        dirName = "openspec-archive-change",
        description = "Archive a completed change. Use when the user wants to finalize and archive a change after implementation is complete.",
        instructions = """Archive a completed change in the experimental workflow.

**Input**: The user's request should include a change name. If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

**Steps**

1. **If no change name provided, prompt for selection**

   List available changes:
   ```bash
   find openspec/changes -maxdepth 1 -mindepth 1 -type d -not -name archive
   ```
   Use the **AskUserQuestion tool** to let the user select.

   Show only active changes (not already archived).

   **IMPORTANT**: Do NOT guess or auto-select a change. Always let the user choose.

2. **Check artifact completion status**

   Read `openspec/changes/<name>/.openspec.yaml` to check artifact completion.

   **If any artifacts are not `done`:**
   - Display warning listing incomplete artifacts
   - Prompt user for confirmation to continue
   - Proceed if user confirms

3. **Check task completion status**

   Read the tasks file (typically `tasks.md`) to check for incomplete tasks.

   Count tasks marked with `- [ ]` (incomplete) vs `- [x]` (complete).

   **If incomplete tasks found:**
   - Display warning showing count of incomplete tasks
   - Prompt user for confirmation to continue
   - Proceed if user confirms

   **If no tasks file exists:** Proceed without task-related warning.

4. **Assess delta spec sync state**

   Check for delta specs at `openspec/changes/<name>/specs/`. If none exist, proceed without sync prompt.

   **If delta specs exist:**
   - Compare each delta spec with its corresponding main spec at `openspec/specs/<capability>/spec.md`
   - Determine what changes would be applied
   - Show a combined summary before prompting

5. **Perform the archive**

   ```bash
   mkdir -p openspec/changes/archive
   mv openspec/changes/<name> openspec/changes/archive/YYYY-MM-DD-<name>
   ```

6. **Display summary**

   Show archive completion summary including:
   - Change name
   - Archive location
   - Spec sync status
   - Note about any warnings

**Guardrails**
- Always prompt for change selection if not provided
- Don't block archive on warnings - just inform and confirm
- Preserve .openspec.yaml when moving to archive (it moves with the directory)
- Show clear summary of what happened"""
    )

    // ── Explore ──────────────────────────────────────────

    private fun exploreCommand() = CommandContent(
        id = "explore",
        name = "OPSX: Explore",
        description = "Enter explore mode - think through ideas, investigate problems, clarify requirements",
        category = "Workflow",
        tags = listOf("workflow", "explore", "thinking"),
        body = """Enter explore mode. Think deeply. Visualize freely. Follow the conversation wherever it goes.

**IMPORTANT: Explore mode is for thinking, not implementing.** You may read files, search code, and investigate the codebase, but you must NEVER write code or implement features. If the user asks you to implement something, remind them to exit explore mode first and create a change proposal. You MAY create OpenSpec artifacts (proposals, designs, specs) if the user asks—that's capturing thinking, not implementing.

**This is a stance, not a workflow.** There are no fixed steps, no required sequence, no mandatory outputs. You're a thinking partner helping the user explore.

**Input**: The argument after `/opsx:explore` is whatever the user wants to think about. Could be:
- A vague idea: "real-time collaboration"
- A specific problem: "the auth system is getting unwieldy"
- A change name: "add-dark-mode" (to explore in context of that change)
- A comparison: "postgres vs sqlite for this"
- Nothing (just enter explore mode)

---

## The Stance

- **Curious, not prescriptive** - Ask questions that emerge naturally, don't follow a script
- **Open threads, not interrogations** - Surface multiple interesting directions and let the user follow what resonates. Don't funnel them through a single path of questions.
- **Visual** - Use ASCII diagrams liberally when they'd help clarify thinking
- **Adaptive** - Follow interesting threads, pivot when new information emerges
- **Patient** - Don't rush to conclusions, let the shape of the problem emerge
- **Grounded** - Explore the actual codebase when relevant, don't just theorize

---

## What You Might Do

Depending on what the user brings, you might:

**Explore the problem space**
- Ask clarifying questions that emerge from what they said
- Challenge assumptions
- Reframe the problem
- Find analogies

**Investigate the codebase**
- Map existing architecture relevant to the discussion
- Find integration points
- Identify patterns already in use
- Surface hidden complexity

**Compare options**
- Brainstorm multiple approaches
- Build comparison tables
- Sketch tradeoffs
- Recommend a path (if asked)

**Visualize**
```
┌─────────────────────────────────────────┐
│     Use ASCII diagrams liberally        │
├─────────────────────────────────────────┤
│                                         │
│   ┌────────┐         ┌────────┐        │
│   │ State  │────────▶│ State  │        │
│   │   A    │         │   B    │        │
│   └────────┘         └────────┘        │
│                                         │
│   System diagrams, state machines,      │
│   data flows, architecture sketches,    │
│   dependency graphs, comparison tables  │
│                                         │
└─────────────────────────────────────────┘
```

**Surface risks and unknowns**
- Identify what could go wrong
- Find gaps in understanding
- Suggest spikes or investigations

---

## OpenSpec Awareness

You have full context of the OpenSpec system. Use it naturally, don't force it.

### Check for context

At the start, quickly check what exists:
```bash
find openspec/changes -maxdepth 1 -mindepth 1 -type d -not -name archive
```

This tells you:
- If there are active changes
- Their names and status
- What the user might be working on

If the user mentioned a specific change name, read its artifacts for context.

### When no change exists

Think freely. When insights crystallize, you might offer:

- "This feels solid enough to start a change. Want me to create a proposal?"
- Or keep exploring - no pressure to formalize

### When a change exists

If the user mentions a change or you detect one is relevant:

1. **Read existing artifacts for context**
   - `openspec/changes/<name>/proposal.md`
   - `openspec/changes/<name>/design.md`
   - `openspec/changes/<name>/tasks.md`
   - etc.

2. **Reference them naturally in conversation**
   - "Your design mentions using Redis, but we just realized SQLite fits better..."
   - "The proposal scopes this to premium users, but we're now thinking everyone..."

3. **Offer to capture when decisions are made**

   | Insight Type | Where to Capture |
   |--------------|------------------|
   | New requirement discovered | `specs/<capability>/spec.md` |
   | Requirement changed | `specs/<capability>/spec.md` |
   | Design decision made | `design.md` |
   | Scope changed | `proposal.md` |
   | New work identified | `tasks.md` |
   | Assumption invalidated | Relevant artifact |

   Example offers:
   - "That's a design decision. Capture it in design.md?"
   - "This is a new requirement. Add it to specs?"
   - "This changes scope. Update the proposal?"

4. **The user decides** - Offer and move on. Don't pressure. Don't auto-capture.

---

## What You Don't Have To Do

- Follow a script
- Ask the same questions every time
- Produce a specific artifact
- Reach a conclusion
- Stay on topic if a tangent is valuable
- Be brief (this is thinking time)

---

## Ending Discovery

There's no required ending. Discovery might:

- **Flow into a proposal**: "Ready to start? I can create a change proposal."
- **Result in artifact updates**: "Updated design.md with these decisions"
- **Just provide clarity**: User has what they need, moves on
- **Continue later**: "We can pick this up anytime"

When things crystallize, you might offer a summary - but it's optional. Sometimes the thinking IS the value.

---

## Guardrails

- **Don't implement** - Never write code or implement features. Creating OpenSpec artifacts is fine, writing application code is not.
- **Don't fake understanding** - If something is unclear, dig deeper
- **Don't rush** - Discovery is thinking time, not task time
- **Don't force structure** - Let patterns emerge naturally
- **Don't auto-capture** - Offer to save insights, don't just do it
- **Do visualize** - A good diagram is worth many paragraphs
- **Do explore the codebase** - Ground discussions in reality
- **Do question assumptions** - Including the user's and your own"""
    )

    private fun exploreSkill() = SkillContent(
        dirName = "openspec-explore",
        description = "Enter explore mode - a thinking partner for exploring ideas, investigating problems, and clarifying requirements.",
        instructions = """Enter explore mode. Think deeply. Visualize freely. Follow the conversation wherever it goes.

**IMPORTANT: Explore mode is for thinking, not implementing.** You may read files, search code, and investigate the codebase, but you must NEVER write code or implement features. If the user asks you to implement something, remind them to exit explore mode first and create a change proposal. You MAY create OpenSpec artifacts (proposals, designs, specs) if the user asks—that's capturing thinking, not implementing.

**This is a stance, not a workflow.** There are no fixed steps, no required sequence, no mandatory outputs. You're a thinking partner helping the user explore.

**Input**: The user's request should include whatever they want to think about. Could be:
- A vague idea: "real-time collaboration"
- A specific problem: "the auth system is getting unwieldy"
- A change name: "add-dark-mode" (to explore in context of that change)
- A comparison: "postgres vs sqlite for this"
- Nothing (just enter explore mode)

---

## The Stance

- **Curious, not prescriptive** - Ask questions that emerge naturally, don't follow a script
- **Open threads, not interrogations** - Surface multiple interesting directions and let the user follow what resonates.
- **Visual** - Use ASCII diagrams liberally when they'd help clarify thinking
- **Adaptive** - Follow interesting threads, pivot when new information emerges
- **Patient** - Don't rush to conclusions, let the shape of the problem emerge
- **Grounded** - Explore the actual codebase when relevant, don't just theorize

---

## What You Might Do

Depending on what the user brings, you might:

**Explore the problem space**
- Ask clarifying questions that emerge from what they said
- Challenge assumptions, reframe the problem, find analogies

**Investigate the codebase**
- Map existing architecture, find integration points, identify patterns, surface hidden complexity

**Compare options**
- Brainstorm approaches, build comparison tables, sketch tradeoffs

**Visualize**
- Use ASCII diagrams for system diagrams, state machines, data flows, architecture sketches

**Surface risks and unknowns**
- Identify what could go wrong, find gaps, suggest investigations

---

## OpenSpec Awareness

Check for existing changes at the start:
```bash
find openspec/changes -maxdepth 1 -mindepth 1 -type d -not -name archive
```

If changes exist, read their artifacts for context and reference them naturally.

When insights crystallize, offer to capture them — but let the user decide.

---

## Guardrails

- **Don't implement** - Never write code. Creating OpenSpec artifacts is fine.
- **Don't fake understanding** - If something is unclear, dig deeper
- **Don't rush** - Discovery is thinking time
- **Do visualize** - A good diagram is worth many paragraphs
- **Do explore the codebase** - Ground discussions in reality
- **Do question assumptions** - Including the user's and your own"""
    )

    // ── New (expanded profile) ───────────────────────────

    private fun newCommand() = CommandContent(
        id = "new",
        name = "OPSX: New",
        description = "Start a new change with scaffolded directory structure",
        category = "Workflow",
        tags = listOf("workflow", "artifacts"),
        body = """Start a new change using the artifact-driven approach.

**Input**: The argument is the change name (kebab-case), OR a description of what the user wants to build.

**Steps**

1. **If no input provided, ask what they want to build**

   Use the **AskUserQuestion tool** to ask:
   > "What change do you want to work on? Describe what you want to build or fix."

   From their description, derive a kebab-case name.

2. **Create the change directory**
   ```bash
   mkdir -p openspec/changes/<name>
   ```

3. **Create `.openspec.yaml`** with change metadata:
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

4. **Show the artifact sequence**: proposal → design → tasks
5. **STOP and wait for user direction** - do NOT create artifacts yet

**Output**: Change name, location, artifact sequence, and prompt to continue.

**Guardrails**
- Do NOT create any artifacts yet - just scaffold
- If name already exists, suggest continuing that change instead"""
    )

    private fun newSkill() = SkillContent(
        dirName = "openspec-new-change",
        description = "Start a new OpenSpec change with scaffolded directory structure.",
        instructions = newCommand().body
    )

    // ── Sync (expanded profile) ──────────────────────────

    private fun syncCommand() = CommandContent(
        id = "sync",
        name = "OPSX: Sync",
        description = "Sync delta specs from a change to main specs",
        category = "Workflow",
        tags = listOf("workflow", "specs"),
        body = """Sync delta specs from a change to main specs.

**Input**: Optionally specify a change name.

**Steps**

1. **If no change name provided, prompt for selection**

   List available changes:
   ```bash
   find openspec/changes -maxdepth 1 -mindepth 1 -type d -not -name archive
   ```

2. **Find delta specs** in `openspec/changes/<name>/specs/*/spec.md`

3. **For each delta spec, apply changes to main specs** at `openspec/specs/<capability>/spec.md`
   - ADDED Requirements → add to main spec
   - MODIFIED Requirements → merge into main spec
   - REMOVED Requirements → remove from main spec
   - RENAMED Requirements → rename in main spec

4. **Show summary** of what was synced

**Key Principle: Intelligent Merging** - apply partial updates, not wholesale replacement.

**Guardrails**
- Read both delta and main specs before making changes
- Preserve existing content not mentioned in delta
- Operation should be idempotent"""
    )

    private fun syncSkill() = SkillContent(
        dirName = "openspec-sync-specs",
        description = "Sync delta specs from a change to main specs.",
        instructions = syncCommand().body
    )

    // ── Verify (expanded profile) ────────────────────────

    private fun verifyCommand() = CommandContent(
        id = "verify",
        name = "OPSX: Verify",
        description = "Verify implementation matches specs and tasks",
        category = "Workflow",
        tags = listOf("workflow", "verification"),
        body = """Verify that implementation matches the specs and tasks for a change.

**Input**: Optionally specify a change name.

**Steps**

1. **Select the change** (auto-select if only one active)

   List available changes if needed:
   ```bash
   find openspec/changes -maxdepth 1 -mindepth 1 -type d -not -name archive
   ```

2. **Read all artifacts** (proposal, design, specs, tasks)
3. **Check task completion** - all tasks should be marked `[x]`
4. **Verify implementation** - review code changes against specs
5. **Report findings**
   - ✅ Tasks complete and implementation matches
   - ⚠️ Issues found (list them)
   - ❌ Missing implementation

**Guardrails**
- Be thorough but practical
- Flag real issues, not style preferences
- If specs are unclear, note ambiguity rather than failing"""
    )

    private fun verifySkill() = SkillContent(
        dirName = "openspec-verify-change",
        description = "Verify that implementation matches the specs and tasks for a change.",
        instructions = verifyCommand().body
    )
}
