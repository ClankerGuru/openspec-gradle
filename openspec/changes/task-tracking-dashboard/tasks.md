# Tasks: Task Tracking Dashboard

## Core Parsing & Data Model

- [ ] `ttd-1` Create `TaskItem` data class and `TaskStatus` enum in `tracking/` package
- [ ] `ttd-2` Build `TaskParser` — parse `tasks.md` Markdown checkboxes into `TaskItem` tree
  - [ ] `ttd-2.1` Handle `- [ ]` (TODO), `- [~]` (IN_PROGRESS), `- [x]` (DONE)
  - [ ] `ttd-2.2` Handle nested tasks (indentation-based hierarchy)
  - [ ] `ttd-2.3` Extract task code from backtick-wrapped prefix (e.g., `` `ttd-1` ``)
- [ ] `ttd-3` Build `TaskCodeGenerator` — derive prefix from proposal name, assign hierarchical numbers
  - [ ] `ttd-3.1` Prefix extraction: kebab-case initials (e.g., `task-tracking-dashboard` → `ttd`)
  - [ ] `ttd-3.2` Code assignment: walk task tree, assign `<prefix>-N.M` codes
  - [ ] `ttd-3.3` Inject codes into tasks.md if not present (first-run code generation)

## Task Writer

- [ ] `ttd-4` Build `TaskWriter` — update checkbox state in `tasks.md` by task code
  - [ ] `ttd-4.1` Find line by task code, replace checkbox marker
  - [ ] `ttd-4.2` Propagate: if all children done, mark parent done

## Gradle Tasks

- [ ] `ttd-5` Implement `OpenSpecStatusTask` — scans all proposals, prints dashboard table
  - [ ] `ttd-5.1` Scan `openspec/changes/*/tasks.md` files
  - [ ] `ttd-5.2` Aggregate stats per proposal (done/total/percentage)
  - [ ] `ttd-5.3` Print summary table + per-proposal task list with status icons
- [ ] `ttd-6` Dynamic task registration — register `openspecTask-<code>` tasks at configuration time
  - [ ] `ttd-6.1` Scan and parse at plugin apply time
  - [ ] `ttd-6.2` Register one task per `TaskItem` with `--set=todo|progress|done` option
  - [ ] `ttd-6.3` Task action: update `tasks.md` via `TaskWriter` and print new status

## Integration

- [ ] `ttd-7` Add `openspec/changes/` to global gitignore patterns
- [ ] `ttd-8` Update `openspecPropose` to auto-generate task codes in `tasks.md`
- [ ] `ttd-9` Tests
  - [ ] `ttd-9.1` TaskParser unit tests (checkbox parsing, nesting, code extraction)
  - [ ] `ttd-9.2` TaskCodeGenerator unit tests (prefix derivation, code assignment)
  - [ ] `ttd-9.3` TaskWriter unit tests (status update, parent propagation)
  - [ ] `ttd-9.4` OpenSpecStatusTask integration test via Gradle TestKit
  - [ ] `ttd-9.5` Dynamic task registration integration test
