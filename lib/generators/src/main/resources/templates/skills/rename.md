Preview or execute a rename refactoring across the codebase.

**Input**: The old name and new name.

1. Preview first:
   ```bash
   ./gradlew srcx-rename -Pfrom=<old> -Pto=<new> -PdryRun=true
   ```
2. Read `.opsx/rename.md` and show affected files to the user.
3. If confirmed, apply:
   ```bash
   ./gradlew srcx-rename -Pfrom=<old> -Pto=<new> -PdryRun=false
   ```
