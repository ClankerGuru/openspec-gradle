# adapters

Bridges the generators module to specific AI coding agent tools, mapping skill files and instructions to each agent's expected directory layout and file format.

## What it does

Each adapter is a single-object implementation of `ToolAdapter` that tells the generators module where to write files and how to format them for a specific AI coding agent. Adapters handle three concerns: skill file paths, skill file formatting, and instruction file paths (including whether instructions should be appended with markers or written fresh).

## Why it exists

AI coding agents each have their own conventions. Claude Code reads skills from `.claude/skills/`, Copilot reads from `.github/skills/`, Codex and OpenCode read from their own directories. Instruction file locations and formats differ too. Adapters decouple the content generation (in `generators`) from the per-agent layout, so adding support for a new agent is a single `ToolAdapter` implementation.

## Claude

**Module:** `lib/adapters/claude`

| Property | Value |
|---|---|
| `toolId` | `"claude"` |
| Skill path | `.claude/skills/{dirName}/SKILL.md` |
| Skill format | `formatSkillForClaude` -- minimal frontmatter (name, description, argument-hint, paths, user-invocable) |
| Instructions path | `.claude/CLAUDE.md` |
| Append mode | Yes (preserves existing CLAUDE.md content, replaces OPSX section between markers) |

**Key class:** `ClaudeAdapter` (singleton object)

**CLI:** `claude -p <prompt> --dangerously-skip-permissions`

## Copilot

**Module:** `lib/adapters/copilot`

| Property | Value |
|---|---|
| `toolId` | `"github-copilot"` |
| Skill path | `.github/skills/{dirName}/SKILL.md` |
| Skill format | `formatSkillWithFrontmatter` -- full YAML frontmatter (name, description, license, compatibility, metadata) |
| Instructions path | `.github/copilot-instructions.md` |
| Append mode | Yes |

**Key class:** `CopilotAdapter` (singleton object)

**CLI:** `copilot -p <prompt> --yolo -s --no-ask-user`

## Codex

**Module:** `lib/adapters/codex`

| Property | Value |
|---|---|
| `toolId` | `"codex"` |
| Skill path | `.agents/skills/{dirName}/SKILL.md` |
| Skill format | `formatSkillWithFrontmatter` |
| Instructions path | `AGENTS.md` |
| Append mode | Yes |

**Key class:** `CodexAdapter` (singleton object)

**CLI:** `codex exec <prompt> --full-auto`

## OpenCode

**Module:** `lib/adapters/opencode`

| Property | Value |
|---|---|
| `toolId` | `"opencode"` |
| Skill path | `.opencode/skills/{dirName}/SKILL.md` |
| Skill format | `formatSkillWithFrontmatter` |
| Instructions path | `AGENTS.md` |
| Append mode | Yes |

**Key class:** `OpenCodeAdapter` (singleton object)

**CLI:** `opencode run --prompt <prompt>`

## Dependencies

All four adapter modules depend on:

- `:lib:generators` (api, for `ToolAdapter`, `SkillContent`, `formatSkillForClaude`, `formatSkillWithFrontmatter`)
