!`./gradlew opsx-status 2>/dev/null && cat .opsx/status.md 2>/dev/null || echo "Run ./gradlew opsx-status to see changes"`

## Archive Workflow

1. Select a change from the status above (or use the argument if provided)
2. Read `opsx/changes/<name>/.opsx.yaml` -- warn if any artifacts are not `done`
3. Read `tasks.md` -- warn if incomplete tasks (`- [ ]`), confirm with user
4. If delta specs exist, ask if user wants to sync before archiving
5. Archive:
   ```bash
   mkdir -p opsx/changes/archive
   mv opsx/changes/<name> opsx/changes/archive/$(date +%Y-%m-%d)-<name>
   ```
6. Report: change name, archive location, spec sync status

Don't block on warnings -- inform and confirm. Never auto-select.
