Verify that implementation matches the specs and tasks for a change.

**Input**: Optionally specify a change name.

**Steps**

1. **Select the change** (auto-select if only one active)

   List available changes if needed:
   ```bash
   ./gradlew opsx-status
   ```

2. **Read project context** (run `./gradlew opsx-sync` to generate all):
   - `.openspec/context.md` — project config, plugins, frameworks, git info
   - `.openspec/tree.md` — source layout per module
   - `.openspec/deps.md` — dependencies with versions
   - `.openspec/modules.md` — module graph and boundaries
   - `.openspec/devloop.md` — build/test/run commands

3. **Read all artifacts** (proposal, design, specs, tasks)
4. **Check task completion** - all tasks should be marked `[x]`
5. **Verify implementation** - review code changes against specs
6. **Report findings**
   - ✅ Tasks complete and implementation matches
   - ⚠️ Issues found (list them)
   - ❌ Missing implementation

**Guardrails**
- Be thorough but practical
- Flag real issues, not style preferences
- If specs are unclear, note ambiguity rather than failing