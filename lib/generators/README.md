# generators

Generates agent-facing files: skill definitions, root instructions, per-task commands, and manages cleanup.

## What it does

Produces the files that teach AI coding agents about OPSX tasks. This includes static skill files (explore, propose, apply, rename, etc.), root instruction files (CLAUDE.md, copilot-instructions.md, AGENTS.md), and dynamic per-task-code skill files that give agents context about specific tasks. Also handles reconciliation of task descriptions against the current codebase, global gitignore management, and cleanup of generated files.

## Why it exists

Each AI coding agent has its own conventions for skill files and instruction locations. Generators abstract the content from the format, using `ToolAdapter` to handle per-agent path and formatting differences. This module also bridges the gap between proposals (which are Markdown) and the agent-specific skill formats that tools actually read.

## Skill File Formats

Skills are Markdown files with YAML frontmatter. The format varies by agent:

**Claude Code** (`formatSkillForClaude`): Minimal frontmatter with only the fields Claude Code recognizes -- `name`, `description`, optional `argument-hint`, `paths`, and `user-invocable`. Includes a version comment.

**Generic/Frontmatter** (`formatSkillWithFrontmatter`): Full YAML frontmatter with `name`, `description`, `license`, `compatibility`, `metadata` (author, version, generatedBy). Used by Copilot, Codex, and OpenCode adapters.

Both formats place the skill instructions as the Markdown body after the frontmatter.

## Key Classes

| Class | Role |
|---|---|
| `SkillGenerator` | Generates SKILL.md files for all registered skill templates, writing one per adapter per skill into the build directory. |
| `GeneratedFile` | A generated file with its relative path and absolute `File` reference. |
| `InstructionsGenerator` | Generates root agent instruction files from a bundled template. Handles two modes: overwrite (Claude) and append-with-markers (Copilot, Codex, OpenCode). Supports install, clean, and marker-section replacement. |
| `TaskCommandGenerator` | Generates dynamic per-task skill files from open proposals. Each task code becomes a skill (e.g. `/opsx-ttd-1`) with context links, implementation steps, dependency info, subtask listing, and reconciliation warnings. |
| `TemplateRegistry` | Registry of all 17 embedded skill templates: `opsx-dashboard`, `opsx-propose`, `opsx-apply`, `opsx-archive`, `opsx-explore`, `opsx-new`, `opsx-sync`, `srcx-verify`, `srcx-find`, `srcx-calls`, `srcx-rename`, `opsx-status`, `srcx-move`, `srcx-usages`, `srcx-extract`, `srcx-inline`, `srcx-deps`, `srcx-remove`. Each loads its instructions from a bundled resource template. |
| `SkillContent` | Tool-agnostic skill content: `dirName`, `description`, `instructions`, `license`, `compatibility`, `metadata`, plus Claude-specific fields (`argumentHint`, `paths`, `userInvocable`). |
| `ToolAdapter` | Interface for per-agent path and formatting conventions. Methods: `getSkillFilePath`, `formatSkillFile`, `getInstructionsFilePath`. Property: `appendInstructions`. |
| `ToolAdapterRegistry` | Singleton registry of `ToolAdapter` instances, keyed by `toolId`. |
| `formatSkillForClaude` | Top-level function that formats a `SkillContent` into Claude Code's SKILL.md format. |
| `formatSkillWithFrontmatter` | Top-level function that formats a `SkillContent` into the generic YAML frontmatter format. |
| `escapeYaml` | Utility that quotes YAML values when they contain special characters. |
| `TaskReconciler` | Reconciles proposal tasks against the current symbol index and filesystem. Extracts PascalCase symbol names and backtick-wrapped file paths from task descriptions, checks if they exist, and suggests similar names when they do not. Returns `TaskWarning` and `FileWarning` lists. |
| `TaskWarning` | Warning for a task that references symbols not found in the codebase, with fuzzy-match suggestions. |
| `FileWarning` | Warning for a task that references file paths not found on disk. |
| `ReconciliationReport` | Combined report of stale symbol and file warnings. |
| `AgentCleaner` | Removes all OPSX-generated files for a given adapter: skill directories, instruction marker sections, and empty parent directories. |
| `GlobalGitignore` | Manages the global gitignore file (`~/.config/git/ignore` or `core.excludesFile`) to ensure OPSX-generated files are ignored. |

## Dependencies

- `:lib:core` (api)
- `:lib:psi` (api, for `TaskReconciler`'s symbol index)
- Gradle API (compile-only, for `GlobalGitignore` logger)
