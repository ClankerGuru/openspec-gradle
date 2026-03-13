# openspec-gradle

[![CI](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml/badge.svg)](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/zone.clanker.gradle?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/zone.clanker.gradle)
[![Maven Central](https://img.shields.io/maven-central/v/zone.clanker/openspec-gradle?label=Maven%20Central)](https://central.sonatype.com/artifact/zone.clanker/openspec-gradle)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Gradle plugin that generates Markdown skill and command files for AI coding assistants, matching [OpenSpec](https://github.com/Fission-AI/OpenSpec) output format.

**Supported tools:** [GitHub Copilot](#github-copilot), [Claude Code](#claude-code), [OpenAI Codex](#openai-codex), [OpenCode/Crush](#opencodeCrush)

Zero-config. Auto-applies via init script. Agents configured via a single Gradle property.

## Installation

### Option 1: Settings Plugin (per-project)

Add to your `settings.gradle.kts`:

```kotlin
plugins {
    id("zone.clanker.gradle") version "<latest>"
}
```

### Option 2: Global Init Script (all projects, zero-config)

```bash
git clone https://github.com/ClankerGuru/openspec-gradle.git
cd openspec-gradle
./gradlew installGlobal
```

This installs to `~/.gradle/init.d/openspec.init.gradle.kts` — every Gradle project on your machine now has OpenSpec tasks. No `plugins {}` block needed in project builds.

### Option 3: Custom Gradle Distribution

Use the [gradle-distribution](https://github.com/ClankerGuru/gradle-distribution) repo to bundle the plugin into a custom Gradle distribution.

## Configuration

In `~/.gradle/gradle.properties` (global) or your project's `gradle.properties`:

```properties
# Which agents to generate files for (comma-separated)
# Values: github, claude, codex, opencode, none
# Default: github
zone.clanker.openspec.agents=github
```

| Value | Tool | Generated path |
|---|---|---|
| `github` | GitHub Copilot | `.github/prompts/` + `.github/skills/` |
| `claude` | Claude Code | `.claude/commands/` + `.claude/skills/` |
| `codex` | OpenAI Codex | `.codex/skills/` |
| `opencode` | OpenCode / Crush | `.opencode/commands/` + `.opencode/skills/` |
| `none` | — | Cleans all generated files |

Combine multiple: `zone.clanker.openspec.agents=github,claude,codex,opencode`

## Tasks

| Task | Description |
|---|---|
| `openspecSync` | Generates and installs skill/command files for configured agents |
| `openspecContext` | Generates `.openspec/context.md` with project metadata, dependencies, and architecture hints (cacheable) |
| `openspecPropose` | Creates a new change proposal |
| `openspecApply` | Implements tasks from a change |
| `openspecArchive` | Archives a completed change |
| `openspecClean` | Removes all generated files |
| `openspecInstallGlobal` | Installs the init script to `~/.gradle/init.d/` |

## Supported Tools

### GitHub Copilot

Generates files that [GitHub Copilot CLI](https://githubnext.com/projects/copilot-cli/) and [VS Code Copilot](https://docs.github.com/en/copilot) read automatically.

- **Prompts** (`/opsx:` slash commands): `.github/prompts/opsx-{name}.prompt.md`
- **Skills** (auto-loaded context): `.github/skills/{name}/SKILL.md`

Commands use `description`-only YAML frontmatter. Skills use full frontmatter (name, description, license, compatibility, metadata).

```
.github/
├── prompts/
│   ├── opsx-propose.prompt.md
│   ├── opsx-apply.prompt.md
│   ├── opsx-archive.prompt.md
│   ├── opsx-explore.prompt.md
│   ├── opsx-new.prompt.md
│   ├── opsx-sync.prompt.md
│   └── opsx-verify.prompt.md
└── skills/
    ├── openspec-propose/SKILL.md
    ├── openspec-apply-change/SKILL.md
    ├── openspec-archive-change/SKILL.md
    ├── openspec-explore/SKILL.md
    ├── openspec-new-change/SKILL.md
    ├── openspec-sync-specs/SKILL.md
    └── openspec-verify-change/SKILL.md
```

### Claude Code

Generates files that [Claude Code](https://docs.anthropic.com/en/docs/claude-code) reads from your project root.

- **Commands** (`/opsx:` slash commands): `.claude/commands/opsx/{name}.md`
- **Skills** (auto-loaded context): `.claude/skills/{name}/SKILL.md`

Commands include full YAML frontmatter (name, description, category, tags). Skills share the same format across all tools.

```
.claude/
├── commands/
│   └── opsx/
│       ├── propose.md
│       ├── apply.md
│       ├── archive.md
│       ├── explore.md
│       ├── new.md
│       ├── sync.md
│       └── verify.md
└── skills/
    ├── openspec-propose/SKILL.md
    ├── openspec-apply-change/SKILL.md
    ├── openspec-archive-change/SKILL.md
    ├── openspec-explore/SKILL.md
    ├── openspec-new-change/SKILL.md
    ├── openspec-sync-specs/SKILL.md
    └── openspec-verify-change/SKILL.md
```

### OpenAI Codex

Generates files for [OpenAI Codex CLI](https://github.com/openai/codex) (and the Codex IDE extension / web app).

Codex unifies commands and skills — everything lives under `.codex/skills/` and is invoked via `$skill-name` in conversation. There is no separate commands directory.

- **Skills** (invoked as `$opsx-propose`, `$openspec-apply-change`, etc.): `.codex/skills/{name}/SKILL.md`

All files use YAML frontmatter with name, description, license, compatibility, and metadata.

```
.codex/
└── skills/
    ├── opsx-propose/SKILL.md
    ├── opsx-apply/SKILL.md
    ├── opsx-archive/SKILL.md
    ├── opsx-explore/SKILL.md
    ├── opsx-new/SKILL.md
    ├── opsx-sync/SKILL.md
    ├── opsx-verify/SKILL.md
    ├── openspec-propose/SKILL.md
    ├── openspec-apply-change/SKILL.md
    ├── openspec-archive-change/SKILL.md
    ├── openspec-explore/SKILL.md
    ├── openspec-new-change/SKILL.md
    ├── openspec-sync-specs/SKILL.md
    └── openspec-verify-change/SKILL.md
```

> **Note:** Codex also reads `AGENTS.md` at the project root for general instructions. The plugin does not generate this file — it's meant to be hand-written for your project.

### OpenCode / Crush

Generates files for [OpenCode](https://github.com/opencode-ai/opencode) (now continued as [Crush](https://github.com/charmbracelet/crush) by Charmbracelet).

- **Commands** (invoked via `/` in the TUI): `.opencode/commands/opsx-{name}.md`
- **Skills** (reference context): `.opencode/skills/{name}/SKILL.md`

Commands use an HTML comment with the description at the top. Skills use standard YAML frontmatter.

OpenCode also reads `opencode.md` / `OPENCODE.md` at the project root for general instructions.

```
.opencode/
├── commands/
│   ├── opsx-propose.md
│   ├── opsx-apply.md
│   ├── opsx-archive.md
│   ├── opsx-explore.md
│   ├── opsx-new.md
│   ├── opsx-sync.md
│   └── opsx-verify.md
└── skills/
    ├── openspec-propose/SKILL.md
    ├── openspec-apply-change/SKILL.md
    ├── openspec-archive-change/SKILL.md
    ├── openspec-explore/SKILL.md
    ├── openspec-new-change/SKILL.md
    ├── openspec-sync-specs/SKILL.md
    └── openspec-verify-change/SKILL.md
```

## Project Context

The `openspecContext` task generates `.openspec/context.md` — a cached snapshot of everything Gradle knows about your project:

- Project metadata (name, group, version, Gradle/Java/Kotlin versions)
- Module graph with inter-module dependencies
- Dependency tree with resolved versions
- Framework detection (Spring Boot, Android, Compose, Hilt, RxJava, Ktor, KMP, etc.)
- Architecture hints based on detected stack
- Git info (branch, remote URL)
- Included/composite build awareness
- Build errors captured as context

This file is auto-generated by `openspecSync` and cached — it only regenerates when build files change.

## Generated File Templates

All tools get the same 7 command templates and 7 skill templates:

| Template | Purpose |
|---|---|
| **propose** | Create a change with proposal, design, and task artifacts |
| **apply** | Implement tasks from a change |
| **archive** | Archive a completed change |
| **explore** | Think through ideas without implementing |
| **new** | Scaffold a new change directory |
| **sync** | Sync delta specs to main specs |
| **verify** | Verify implementation matches specs |

All generated files are auto-added to `.gitignore`.

## License

[MIT](LICENSE)
