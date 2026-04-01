---
name: opsx-rename
description: "Rename a symbol across the codebase safely. Use when the user says 'rename X to Y' or wants to refactor a name."
argument-hint: "[old-name new-name]"
---

Preview or execute a rename refactoring across the codebase.

---

**Input**: The old name and new name for the symbol to rename.

**Steps**

1. Run the rename task in dry-run mode first:
   ```bash
   ./gradlew opsx-rename -Pfrom=<old> -Pto=<new> -PdryRun=true
   ```

2. Read the output at `.opsx/rename.md` and show the preview to the user.

3. If the user confirms, apply:
   ```bash
   ./gradlew opsx-rename -Pfrom=<old> -Pto=<new> -PdryRun=false
   ```

**Output**

Show affected files and the changes that will be (or were) made.

