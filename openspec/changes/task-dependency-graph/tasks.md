# Tasks: Task Dependency Graph

## Dependency Syntax

- [ ] `tdg-1` Define `→ depends:` syntax and add to TaskItem data class
- [ ] `tdg-2` Build `TaskDependencyParser` — extract explicit deps from task lines → depends: tdg-1
  - [ ] `tdg-2.1` Regex parser for `→\s*depends:\s*(.+)$`
  - [ ] `tdg-2.2` Integrate into existing TaskParser so deps are parsed alongside checkboxes

## Graph Operations

- [ ] `tdg-3` Build `DependencyGraph` utility → depends: tdg-2
  - [ ] `tdg-3.1` Topological sort (Kahn's algorithm)
  - [ ] `tdg-3.2` Cycle detection with path reporting
  - [ ] `tdg-3.3` Blocker resolution — given a task, return list of incomplete deps (transitive)

## Execution Validation

- [ ] `tdg-4` Add dep validation to `openspecTask-<code>` execution → depends: tdg-3
  - [ ] `tdg-4.1` Read tasks.md, check all explicit + implicit deps are DONE
  - [ ] `tdg-4.2` Fail with actionable message: "Blocked by: ttd-2 (TODO)"
  - [ ] `tdg-4.3` Skip validation when `--set=todo` (allow resetting tasks)

## Apply Integration

- [ ] `tdg-5` Update `openspecApply` to accept `--task=<code>` → depends: tdg-4
  - [ ] `tdg-5.1` Resolve task from code, validate deps
  - [ ] `tdg-5.2` Scope apply workflow to single task description
- [ ] `tdg-6` Implement `openspecApply --all` → depends: tdg-3, tdg-5
  - [ ] `tdg-6.1` Topological sort all tasks
  - [ ] `tdg-6.2` Execute in order, stop at first blocker

## Tests

- [ ] `tdg-7` Tests → depends: tdg-3
  - [ ] `tdg-7.1` Dependency parser unit tests (syntax extraction, edge cases)
  - [ ] `tdg-7.2` Cycle detection tests (no cycle, simple cycle, transitive cycle)
  - [ ] `tdg-7.3` Blocker resolution tests
  - [ ] `tdg-7.4` Integration test: task blocked → clear error message
  - [ ] `tdg-7.5` Integration test: `openspecApply --task=<code>` end-to-end
