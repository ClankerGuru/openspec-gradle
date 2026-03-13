# openspec-gradle

A Gradle plugin that generates project context and instruction files for AI coding assistants. Zero-config, multi-agent, and designed to work out of the box.

**Plugin ID:** `zone.clanker.gradle`  
**Repository:** [ClankerGuru/openspec-gradle](https://github.com/ClankerGuru/openspec-gradle)

## What it does

openspec-gradle generates command and skill files for AI coding assistants in their tool-specific formats, plus a rich project context file extracted directly from your Gradle build — dependencies, module structure, frameworks, source sets — so your AI assistant understands your project without manual setup.

## Supported agents

| Agent | Selection key |
|---|---|
| GitHub Copilot | `github` |
| Claude Code | `claude` |
| Cursor | `cursor` |
| Codex | `codex` |
| OpenCode | `opencode` |
| Crush | `crush` |

Select agents via Gradle property:

```properties
# gradle.properties
zone.clanker.openspec.agents=github,claude,cursor
```

## Quick start

### Global install (recommended)

```bash
./gradlew openspecInstallGlobal
```

This installs the plugin via a Gradle init script. No `plugins {}` block or `openspec {}` configuration needed — it auto-applies to all your projects.

### Generate files

```bash
./gradlew openspecSync
```

That's it. Agent instruction files are generated, `.gitignore` entries are added automatically.

## Tasks

| Task | Description |
|---|---|
| `openspecSync` | Generate/update command and skill files for configured agents |
| `openspecContext` | Generate rich project context to `.openspec/context.md` |
| `openspecPropose` | Create a new change proposal (specs, design, tasks) |
| `openspecApply` | Mark a proposed change as ready for implementation |
| `openspecArchive` | Archive a completed change |
| `openspecClean` | Remove all generated files |
| `openspecInstallGlobal` | Install plugin globally via init script |

## Cleanup

Removing an agent from the `zone.clanker.openspec.agents` property automatically removes its files (and empty directories) on the next `openspecSync`.

## Wiki pages

- [Vision](Vision) — Where the project is heading
- [Context Generation](Context-Generation) — Deep dive on the context engine
