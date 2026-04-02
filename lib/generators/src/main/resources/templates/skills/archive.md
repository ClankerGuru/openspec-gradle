Archive a completed change.

**Input**: Change name. If omitted, prompt for selection.

1. If no name provided, list active changes with `./gradlew opsx-status` and let the user select
2. Read `opsx/changes/<name>/.opsx.yaml` — warn if any artifacts are not `done`
3. Read `tasks.md` — warn if incomplete tasks (`- [ ]`), confirm with user
4. If delta specs exist in `opsx/changes/<name>/specs/`:
   - Compare with main specs at `opsx/specs/<capability>/spec.md`
   - Show summary, ask if user wants to sync before archiving
5. Archive:
   ```bash
   mkdir -p opsx/changes/archive
   mv opsx/changes/<name> opsx/changes/archive/YYYY-MM-DD-<name>
   ```
6. Show summary: change name, archive location, spec sync status

Don't block on warnings — inform and confirm. Never auto-select a change.
