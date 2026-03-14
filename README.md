# openspec-gradle

[![CI](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml/badge.svg)](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/zone.clanker.gradle?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/zone.clanker.gradle)
[![Maven Central](https://img.shields.io/maven-central/v/zone.clanker/openspec-gradle?label=Maven%20Central)](https://central.sonatype.com/artifact/zone.clanker/openspec-gradle)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Gradle-native alternative to [OpenSpec](https://github.com/Fission-AI/OpenSpec) for Kotlin/JVM projects.**

Extracts project context from the Gradle build model and generates command/skill files for AI coding assistants. Includes a task tracking system with dynamic Gradle tasks for managing proposals. Zero-config — auto-applies via init script, agents configured via a single Gradle property.

## How it differs from OpenSpec

| | OpenSpec | openspec-gradle |
|---|---|---|
| **Runtime** | Node.js CLI | Gradle plugin (no extra toolchain) |
| **Scope** | Any project | Kotlin, JVM, Android |
| **Context** | Manual / static | Extracted from Gradle build model (deps, modules, frameworks) |
| **Config** | `openspec init` + YAML | Zero-config via `gradle.properties` |
| **Install** | `npm install -g` | Init script or settings plugin |
| **Task Tracking** | External | Built-in Gradle tasks per proposal item |

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
./gradlew opsx-sync
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

### Core Tasks

| Task | Description |
|---|---|
| `opsx-sync` | Generate and install skill/command files for configured agents |
| `opsx-context` | Generate `.openspec/context.md` — project metadata, dependencies, architecture hints |
| `opsx-propose --name=<name>` | Create a new change proposal with auto-generated task codes |
| `opsx-apply --name=<name>` | Mark a proposed change as ready for implementation |
| `opsx-archive --name=<name>` | Archive a completed change |
| `opsx-clean` | Remove all generated files and `.openspec/` directory |
| `opsx-install` | Install the init script to `~/.gradle/init.d/` |

### Task Tracking

Every task item in a proposal's `tasks.md` becomes a Gradle task:

| Task | Description |
|---|---|
| `opsx-status` | Dashboard — shows all proposals with progress bars |
| `opsx-status --proposal=<name>` | Filter dashboard to a single proposal |
| `opsx-<code>` | Show status of a specific task (e.g., `opsx-ttd-1`) |
| `opsx-<code> --set=done` | Mark a task as done |
| `opsx-<code> --set=progress` | Mark a task as in-progress |
| `opsx-<code> --set=todo` | Reset a task to todo |

#### Example Workflow

```bash
# Create a proposal
./gradlew opsx-propose --name=add-user-auth

# Check the dashboard
./gradlew opsx-status

# Start working on a task
./gradlew opsx-aua-1 --set=progress

# Mark it done
./gradlew opsx-aua-1 --set=done

# Check progress
./gradlew opsx-status --proposal=add-user-auth
```

#### Task Codes

Task codes are auto-generated from the proposal name:
- `add-user-auth` → prefix `aua` → tasks `aua-1`, `aua-1.1`, `aua-2`, etc.
- `fix-login-bug` → prefix `flb` → tasks `flb-1`, `flb-2`, etc.

#### Task Dependencies

Tasks can declare dependencies using `→ depends:` syntax in `tasks.md`:

```markdown
- [ ] `aua-1` Create User model
- [ ] `aua-2` Implement JWT service → depends: aua-1
- [ ] `aua-3` Add login endpoint → depends: aua-1, aua-2
```

Running `opsx-aua-2 --set=done` will fail if `aua-1` isn't done yet.

#### Auto-Propagation

When all children of a parent task are marked done, the parent is automatically marked done too.

## Project Context

`opsx-context` generates `.openspec/context.md` — a cached snapshot of what Gradle knows about your project:

- **Project metadata** — name, group, version, Gradle/Java/Kotlin versions
- **Module graph** — inter-module dependencies with configurations
- **Resolved dependencies** — actual versions after conflict resolution and BOMs
- **Framework detection** — Spring Boot, Android, Compose, Hilt, Room, Ktor, KMP, etc.
- **Architecture hints** — migration opportunities based on detected stack
- **Git info** — branch, remote URL
- **Composite builds** — included build awareness

Cached via `@CacheableTask` — only regenerates when build files change.

## Tool Description Convention

All tasks follow the `[tool]` description convention, making `./gradlew tasks --group openspec` a self-describing tool catalog for AI agents:

```
opsx-context  [tool] Project context generator.
                Output: .openspec/context.md.
                Use when: You need project metadata, plugins, frameworks.

opsx-status  [tool] Proposal dashboard.
                Use when: Check proposal progress, find active tasks.
                Chain: opsx-<code> --set=done to work on a task.
```

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

Patterns managed:
- `.openspec/` — context files
- `openspec/changes/` — proposals (per-developer)
- Agent-specific directories (`.claude/`, `.github/prompts/`, `.codex/`, `.opencode/`)

## Wiki

- [Home](https://github.com/ClankerGuru/openspec-gradle/wiki) — Overview and quick start
- [Vision](https://github.com/ClankerGuru/openspec-gradle/wiki/Vision) — Roadmap and architecture
- [Context Generation](https://github.com/ClankerGuru/openspec-gradle/wiki/Context-Generation) — Deep dive on the context engine

## License

[MIT](LICENSE)
