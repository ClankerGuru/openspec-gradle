---
name: openspec-archive-change
description: "Archive a completed change. Use when the user wants to finalize and archive a change after implementation is complete."
license: MIT
compatibility: Requires Gradle build system.
metadata:
  author: "openspec-gradle"
  version: "1.0"
  generatedBy: "openspec-gradle:0.1.0"
---

Archive a completed change in the experimental workflow.

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
- Show clear summary of what happened
