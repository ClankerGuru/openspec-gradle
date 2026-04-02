Start a new change with a scaffolded directory.

**Input**: Change name (kebab-case) or a description of what to build.

1. If no input, ask what they want to build. Derive a kebab-case name from the description.
2. Create the change:
   ```bash
   ./gradlew opsx-propose --name=<name>
   ```
3. Create `.opsx.yaml` with metadata:
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
4. Show the artifact sequence: proposal -> design -> tasks
5. **STOP and wait for user direction** — do NOT create artifacts yet

If name already exists, suggest continuing that change instead.
