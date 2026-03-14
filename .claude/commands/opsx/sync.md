---
name: "OPSX: Sync"
description: "Sync delta specs from a change to main specs"
category: Workflow
tags: [workflow, specs]
---

Sync delta specs from a change to main specs.

**Input**: Optionally specify a change name.

**Steps**

1. **If no change name provided, prompt for selection**

   List available changes:
   ```bash
   find openspec/changes -maxdepth 1 -mindepth 1 -type d -not -name archive
   ```

2. **Find delta specs** in `openspec/changes/<name>/specs/*/spec.md`

3. **For each delta spec, apply changes to main specs** at `openspec/specs/<capability>/spec.md`
   - ADDED Requirements → add to main spec
   - MODIFIED Requirements → merge into main spec
   - REMOVED Requirements → remove from main spec
   - RENAMED Requirements → rename in main spec

4. **Show summary** of what was synced

**Key Principle: Intelligent Merging** - apply partial updates, not wholesale replacement.

**Guardrails**
- Read both delta and main specs before making changes
- Preserve existing content not mentioned in delta
- Operation should be idempotent
