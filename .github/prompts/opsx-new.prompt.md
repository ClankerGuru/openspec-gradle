---
description: "Start a new change with scaffolded directory structure"
---

Start a new change using the artifact-driven approach.

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
- If name already exists, suggest continuing that change instead
