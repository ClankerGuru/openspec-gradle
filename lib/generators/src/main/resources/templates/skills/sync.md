Sync delta specs from a change to main specs.

**Input**: Optionally specify a change name.

1. If no change name, select from: `./gradlew opsx-status`
2. Find delta specs in `opsx/changes/<name>/specs/*/spec.md`
3. For each delta spec, apply to main specs at `opsx/specs/<capability>/spec.md`:
   - ADDED requirements -> add to main spec
   - MODIFIED requirements -> merge into main spec
   - REMOVED requirements -> remove from main spec
   - RENAMED requirements -> rename in main spec
4. Show summary of what was synced

**Key principle**: Intelligent merging — apply partial updates, not wholesale replacement. Read both delta and main specs before changes. Preserve existing content not mentioned in delta.
