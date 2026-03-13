# openspec-gradle

[![CI](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml/badge.svg)](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/zone.clanker.gradle?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/zone.clanker.gradle)
[![Maven Central](https://img.shields.io/maven-central/v/zone.clanker/openspec-gradle?label=Maven%20Central)](https://central.sonatype.com/artifact/zone.clanker/openspec-gradle)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Gradle-native alternative to [OpenSpec](https://github.com/Fission-AI/OpenSpec) for Kotlin/JVM projects.**

Extracts project context from the Gradle build model and generates command/skill files for AI coding assistants. Zero-config — auto-applies via init script, agents configured via a single Gradle property.

## How it differs from OpenSpec

| | OpenSpec | openspec-gradle |
|---|---|---|
| **Runtime** | Node.js CLI | Gradle plugin (no extra toolchain) |
| **Scope** | Any project | Kotlin, JVM, Android |
| **Context** | Manual / static | Extracted from Gradle build model (deps, modules, frameworks) |
| **Config** | `openspec init` + YAML | Zero-config via `gradle.properties` |
| **Install** | `npm install -g` | Init script or settings plugin |

## Supported Agents

| Property value | Tool | Commands | Skills |
|---|---|---|---|
| `github` | GitHub Copilot | `.github/prompts/opsx-*.prompt.md` | `.github/skills/openspec-*/SKILL.md` |
| `claude` | Claude Code | `.claude/commands/opsx/*.md` | `.claude/skills/openspec-*/SKILL.md` |
| `codex` | OpenAI Codex | `.codex/prompts/opsx-*.md` | `.codex/skills/openspec-*/SKILL.md` |
| `opencode` | OpenCode / Crush | `.opencode/commands/opsx-*.md` | `.opencode/skills/openspec-*/SKILL.md` |

## Quick Start

### Option 1: Settings Plugin (per-project)

Add to your `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("zone.clanker.gradle") version "<latest>"
}
```

Then run:

```bash
./gradlew openspecSync
```

### Option 2: Global Init Script (all projects)

```bash
git clone https://github.com/ClankerGuru/openspec-gradle.git
cd openspec-gradle
./gradlew installGlobal
```

This installs to `~/.gradle/init.d/openspec.init.gradle.kts`. Every Gradle project on your machine now has OpenSpec tasks — no `plugins {}` block needed.

## Configuration

In `~/.gradle/gradle.properties` (global default) or `<project>/gradle.properties` (per-project override):

```properties
# Which agents to generate files for (comma-separated)
# Values: github, claude, codex, opencode, none
# Default: github
zone.clanker.openspec.agents=github
```

**Per-project override works:** Set `agents=none` globally, then `agents=github,claude` in specific projects.

Combine multiple agents:

```properties
zone.clanker.openspec.agents=github,claude,codex,opencode
```

Setting `none` removes all generated files (including `.openspec/context.md`).

## Tasks

| Task | Description |
|---|---|
| `openspecSync` | Generate and install skill/command files for configured agents |
| `openspecContext` | Generate `.openspec/context.md` — project metadata, dependencies, architecture hints |
| `openspecPropose` | Create a new change proposal (specs, design, tasks) |
| `openspecApply` | Mark a proposed change as ready for implementation |
| `openspecArchive` | Archive a completed change |
| `openspecClean` | Remove all generated files and `.openspec/` directory |
| `openspecInstallGlobal` | Install the init script to `~/.gradle/init.d/` |

## Project Context

`openspecContext` generates `.openspec/context.md` — a cached snapshot of what Gradle knows about your project:

- **Project metadata** — name, group, version, Gradle/Java/Kotlin versions
- **Module graph** — inter-module dependencies with configurations
- **Resolved dependencies** — actual versions after conflict resolution and BOMs
- **Framework detection** — Spring Boot, Android, Compose, Hilt, Room, Ktor, KMP, etc.
- **Architecture hints** — migration opportunities based on detected stack
- **Git info** — branch, remote URL
- **Composite builds** — included build awareness

Cached via `@CacheableTask` — only regenerates when build files change.

## Generated Templates

All agents get the same 7 command and 7 skill templates:

| Template | Purpose |
|---|---|
| `propose` | Create a change with proposal, design, and task artifacts |
| `apply` | Implement tasks from a change |
| `archive` | Archive a completed change |
| `explore` | Think through ideas without implementing |
| `new` | Scaffold a new change directory |
| `sync` | Sync delta specs to main specs |
| `verify` | Verify implementation matches specs |

## Gitignore

Generated files are automatically added to your **global gitignore** (`~/.config/git/ignore`), not the project's `.gitignore`. This keeps project repos clean — different developers can use different agents without polluting shared config.

## Wiki

- [Home](https://github.com/ClankerGuru/openspec-gradle/wiki) — Overview and quick start
- [Vision](https://github.com/ClankerGuru/openspec-gradle/wiki/Vision) — Roadmap and architecture
- [Context Generation](https://github.com/ClankerGuru/openspec-gradle/wiki/Context-Generation) — Deep dive on the context engine

## License

[MIT](LICENSE)
