# core

Shared models, parsers, and services used by every other module in the plugin.

## What it does

Defines the data model for proposals and tasks, parses `tasks.md` Markdown files into structured trees, writes status updates back, generates hierarchical task codes, resolves task dependency graphs, and provides version info. Also contains the Gradle extension classes for plugin configuration.

## Why it exists

Every module in the plugin needs to understand what a proposal is, what a task looks like, and how task dependencies work. Centralizing these types and parsers in `core` avoids circular dependencies and keeps the data model consistent.

## Key Classes

| Class | Role |
|---|---|
| `Symbol` | A code symbol (class, function, etc.) with its name, kind, file, and line number. |
| `SymbolKind` | Enum: `CLASS`, `INTERFACE`, `ENUM`, `DATA_CLASS`, `OBJECT`, `FUNCTION`, `PROPERTY`. |
| `Reference` | A reference to a symbol found in source code (import, call, type ref, supertype, constructor). |
| `ReferenceKind` | Enum: `IMPORT`, `CALL`, `NAME_REF`, `SUPERTYPE`, `TYPE_REF`, `CONSTRUCTOR`. |
| `MethodCall` | An edge in the call graph linking a caller `Symbol` to a target `Symbol`. |
| `VerifyAssertion` | A machine-checkable assertion on a task (e.g. `symbol-exists`, `file-exists`, `build-passes`). |
| `TaskStatus` | Enum: `TODO`, `IN_PROGRESS`, `DONE`, `BLOCKED`. Provides checkbox, emoji, and icon representations. |
| `TaskMetadata` | Inline metadata parsed from task lines: `agent`, `retries`, `cooldown`. |
| `TaskItem` | A single task parsed from `tasks.md` with code, description, status, children, dependencies, and verify assertions. Supports recursive flattening and progress tracking. |
| `Proposal` | A named proposal containing a task tree, code prefix, and aggregate progress stats. |
| `TaskParser` | Parses `tasks.md` Markdown into a `List<TaskItem>` tree. Handles checkboxes, emoji markers, backtick-wrapped codes, nesting via indentation, `depends:` syntax, and `> verify:` assertions. |
| `TaskWriter` | Updates task status in `tasks.md` files in-place. Handles checkbox rewriting, `unverified` markers, attempt log appending, and automatic parent completion propagation. |
| `TaskCodeGenerator` | Generates short prefixes from proposal names (e.g. `"task-tracking-dashboard"` becomes `"ttd"`) and assigns hierarchical codes (`ttd-1`, `ttd-1.2`) to task items. Can inject codes into raw Markdown lines. |
| `ProposalScanner` | Scans `opsx/changes/` directories for proposals, parses each one, and supports lookup by name or task code. |
| `DependencyGraph` | Directed graph of task dependencies built from `TaskItem.explicitDeps`. Provides cycle detection, transitive dependency resolution, topological ordering, and unblocked-task queries. |
| `VersionInfo` | Reads the plugin version from `openspec-gradle.properties` at runtime. |
| `OpenSpecExtension` | Gradle extension holding the `tools` list property (which agent adapters to generate for). |
| `WrkxExtension` | Gradle settings extension for managing multi-repo composite builds. Handles repo registration, `includeBuild`, tree inclusion, and duplicate build-name detection. |
| `WrkxRepo` | A single repo entry within a workspace, with enable/substitute/ref toggles and composite-build inclusion support. |
| `RepoEntry` | Serializable repo entry parsed from `workspace.json`. Handles substitution parsing and directory name derivation. |

## Dependencies

- `kotlinx-serialization-json` (for `RepoEntry` JSON parsing)
- Gradle API (compile-only, for `OpenSpecExtension`)
