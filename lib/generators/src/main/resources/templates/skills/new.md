!`ls opsx/changes/ 2>/dev/null || echo "No existing changes"`

## New Change

1. If no input, ask what to build. Derive a kebab-case name.
2. Create the change:
   ```bash
   ./gradlew opsx-propose --name=<name>
   ```
3. The scaffold creates `.opsx.yaml`, `proposal.md`, `design.md`, `tasks.md`
4. Artifact sequence: proposal -> design -> tasks
5. **STOP and wait for user direction** -- do NOT create artifacts yet

If the name already exists in the list above, suggest continuing that change instead.
