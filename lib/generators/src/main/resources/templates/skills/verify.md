Verify that implementation matches the specs and tasks for a change.

**Input**: Optionally specify a change name.

1. Select the change (auto-select if only one active): `./gradlew opsx-status`
2. Read project context: `.opsx/context.md`, `.opsx/tree.md`, `.opsx/modules.md`, `.opsx/devloop.md`
3. Read all change artifacts (proposal, design, specs, tasks)
4. Check all tasks are marked `[x]`
5. Verify code changes match specs
6. Report: tasks complete and matching, issues found, or missing implementation

Flag real issues, not style preferences. Note ambiguity rather than failing.
