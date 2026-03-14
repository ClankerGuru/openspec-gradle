---
name: openspec-verify-change
description: "Verify that implementation matches the specs and tasks for a change."
license: MIT
compatibility: Requires Gradle build system.
metadata:
  author: "openspec-gradle"
  version: "1.0"
  generatedBy: "openspec-gradle:0.1.0"
---

Verify that implementation matches the specs and tasks for a change.

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
- If specs are unclear, note ambiguity rather than failing
