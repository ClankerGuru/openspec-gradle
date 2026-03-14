---
name: openspec-propose
description: "Propose a new change with all artifacts generated in one step. Use when the user wants to quickly describe what they want to build and get a complete proposal with design, specs, and tasks ready for implementation."
license: MIT
compatibility: Requires Gradle build system.
metadata:
  author: "openspec-gradle"
  version: "1.0"
  generatedBy: "openspec-gradle:0.1.0"
---

Propose a new change - create the change and generate all artifacts in one step.

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
- Verify each artifact file exists after writing before proceeding to next
