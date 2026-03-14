# openspec-gradle

[![🤖 clanker](https://img.shields.io/badge/🤖-clanker-black?style=flat-square)](https://github.com/ClankerGuru) [![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![CI](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml/badge.svg)](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml) [![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/zone.clanker.gradle?label=Gradle%20Plugin%20Portal&style=flat-square)](https://plugins.gradle.org/plugin/zone.clanker.gradle) [![Maven Central](https://img.shields.io/maven-central/v/zone.clanker/openspec-gradle?label=Maven%20Central&style=flat-square)](https://central.sonatype.com/artifact/zone.clanker/openspec-gradle) [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)

**Gradle as a dynamic context engine for AI coding agents.**

Inspired by [OpenSpec](https://github.com/Fission-AI/OpenSpec). Built for the JVM.

Your Gradle build already knows everything about your project — the dependency tree, the module graph, the plugin stack, the framework versions. It's a structured, queryable model that updates every time you sync. So why is your AI agent still running `grep` and `cat` to figure out what's going on?

openspec-gradle treats **Gradle as the engine**. Every task is a structured query. Every output is dynamic context — generated on demand, never committed, always fresh. You don't describe your project to your agent. Gradle does it for you.

> *We Gradle'd the prompt.* 🤖

Works with **GitHub Copilot**, **Claude Code**, **OpenAI Codex**, and **OpenCode**.

---

## Quick Start

Add to your `settings.gradle.kts`:

```kotlin
plugins {
    id("zone.clanker.gradle") version "<version>"
}
```

Run:

```bash
./gradlew opsx-sync
```

That's it. Your agent now has project context and skills.

> **Go global** — run `./gradlew opsx-install` to install an init script at `~/.gradle/init.d/`.
> Every Gradle project on your machine gets OpenSpec — no `plugins {}` block needed anywhere.

---

## Why Gradle?

Most AI context tools work outside the build system. They scan files, guess at structure, and produce static snapshots. That works for simple projects, but JVM builds are different:

- **Dependencies resolve at build time.** BOMs, platforms, version catalogs, conflict resolution — the actual dependency tree isn't in any single file. Gradle knows it. Your agent doesn't.
- **Modules form a graph.** Multi-project builds, included builds, composite builds — the relationships between modules are defined in the build model, not the filesystem.
- **Tasks are a DAG.** Gradle's task graph is already a structured, dependency-aware execution plan. That's exactly what an AI agent needs to reason about a project.

openspec-gradle doesn't reinvent any of this. It exposes what Gradle already knows as focused markdown outputs that agents can read.

**Nothing gets committed.** Generated files live in `.openspec/` and agent-specific directories, all excluded via global gitignore. They're per-developer, ephemeral, and always regenerated from the build model. Your repo stays clean.

---

## What It Does

### Project Context

```bash
./gradlew opsx-context
```

Generates `.openspec/context.md` — a structured snapshot from the build model:

- Project metadata (name, group, version, Gradle/Java/Kotlin versions)
- Module graph with inter-module dependencies
- Resolved dependencies (actual versions after conflict resolution and BOMs)
- Detected frameworks (Spring Boot, Android, Ktor, Compose, KMP, etc.)
- Git info (branch, remote)

Cached via `@CacheableTask` — only regenerates when build files change.

### Agent Skills & Commands

```bash
./gradlew opsx-sync
```

Generates skill and command files in the right format for your agent. Each agent gets 7 workflows: `propose`, `apply`, `archive`, `explore`, `new`, `sync`, `verify`.

The agent reads these alongside the project context. It doesn't need to be told how to work with your project — the skills encode the workflow, and the context provides the knowledge.

### Proposal Tracking

Break work into tasks. Each task becomes a Gradle command:

```bash
./gradlew opsx-propose --name=add-user-auth    # scaffold a proposal
./gradlew opsx-status                           # dashboard with progress
./gradlew opsx-aua-1 --set=done                 # check off a task
```

Tasks are defined in `tasks.md`, auto-coded from the proposal name (`add-user-auth` → prefix `aua` → tasks `aua-1`, `aua-2.1`, etc.), and registered as real Gradle tasks at configuration time.

---

## Example

You're building a small CLI to manage bookmarks. Here's the full flow:

```bash
# Start a project
mkdir bookmarks && cd bookmarks
gradle init --type kotlin-application --dsl kotlin

# Set your agent (gradle.properties)
echo "zone.clanker.openspec.agents=claude" >> gradle.properties

# Generate everything
./gradlew opsx-sync

# Plan a feature
./gradlew opsx-propose --name=bookmark-cli
```

Edit `openspec/changes/bookmark-cli/tasks.md`:

```markdown
- [ ] `bc-1` Parse CLI arguments (add, search, list)
- [ ] `bc-2` Store bookmarks in a JSON file
  - [ ] `bc-2.1` Save a bookmark (URL + title + tags)
  - [ ] `bc-2.2` Load bookmarks from file
- [ ] `bc-3` Search by tag or title
- [ ] `bc-4` Pretty-print results
```

Work through it:

```bash
./gradlew opsx-status                         # see the dashboard
./gradlew opsx-bc-1 --set=progress            # start a task
./gradlew opsx-bc-1 --set=done                # finish it
./gradlew opsx-status --proposal=bookmark-cli # check progress
```

Or just tell your agent:

> "Look at the bookmark-cli proposal and implement bc-2"

It has the context. It has the skills. It knows what to do.

---

## Configuration

One property in `gradle.properties`:

```properties
zone.clanker.openspec.agents=claude
```

| Value | Agent | Where files go |
|---|---|---|
| `github` | GitHub Copilot | `.github/prompts/` · `.github/skills/` |
| `claude` | Claude Code | `.claude/commands/` · `.claude/skills/` |
| `codex` | OpenAI Codex | `.codex/skills/` |
| `opencode` | OpenCode | `.opencode/commands/` · `.opencode/skills/` |

Combine: `github,claude` · Per-project overrides global · Set `none` to disable.

---

## Tasks

```bash
./gradlew opsx          # list everything
```

| Task | What it does |
|---|---|
| `opsx` | List all OpenSpec tasks |
| `opsx-context` | Generate project context from the build model |
| `opsx-sync` | Generate agent files (context + skills + commands) |
| `opsx-propose` | Create a change proposal with task scaffolding |
| `opsx-apply` | Mark a proposal ready to implement |
| `opsx-archive` | Archive a completed proposal |
| `opsx-status` | Dashboard with progress bars |
| `opsx-<code>` | View or update a task (`--set=todo\|progress\|done`) |
| `opsx-clean` | Remove all generated files |
| `opsx-install` | Install globally via init script |

---

## Task Tracking

Tasks live in `tasks.md` inside each proposal:

```markdown
- [ ] `aua-1` Create User model
- [ ] `aua-2` JWT service → depends: aua-1
  - [ ] `aua-2.1` Token generation
  - [ ] `aua-2.2` Token validation
- [ ] `aua-3` Login endpoint → depends: aua-1, aua-2
```

- `[ ]` todo · `[~]` in progress · `[x]` done
- `→ depends:` — blocks completion until dependencies are done
- Parent tasks auto-complete when all children are done
- Codes are generated from the proposal name initials + hierarchy

---

## Design Principles

- **Zero config.** One property. No DSL blocks. No YAML files.
- **Nothing committed.** All generated content excluded via global gitignore. Your repo stays clean.
- **Gradle is the API.** Every output comes from a task. Tasks are composable, cacheable, and dependency-aware.
- **Agent-agnostic.** Same structured context, formatted for each agent's conventions.
- **Per-developer.** Different agents, different proposals, no conflicts. Each developer's workspace is their own.

---

## License

[MIT](LICENSE)
