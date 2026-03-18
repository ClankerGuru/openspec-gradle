# openspec-gradle

[![🤖 clanker](https://img.shields.io/badge/🤖-clanker-black?style=flat-square)](https://github.com/ClankerGuru) [![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![CI](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml/badge.svg)](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml) [![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/zone.clanker.gradle?label=Gradle%20Plugin%20Portal&style=flat-square)](https://plugins.gradle.org/plugin/zone.clanker.gradle) [![Maven Central](https://img.shields.io/maven-central/v/zone.clanker/openspec-gradle?label=Maven%20Central&style=flat-square)](https://central.sonatype.com/artifact/zone.clanker/openspec-gradle) [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)

**Gradle as a dynamic context engine for AI coding agents.**

> *We Gradle'd the prompt.* 🤖

Your build tool already knows more about your project than any shell script ever will — resolved dependencies, module relationships, framework versions, source structure, symbol graphs. openspec-gradle turns that knowledge into structured context that AI coding agents can actually use.

Works with **GitHub Copilot**, **Claude Code**, **OpenAI Codex**, **OpenCode**, and **Crush**.

---

## Quick Start

Add to `settings.gradle.kts`:

```kotlin
plugins {
    id("zone.clanker.gradle") version "<version>"
}
```

```bash
./gradlew opsx-sync
```

That's it. Your agent now has project context, skills, and commands.

> **Go global** — `./gradlew opsx-install` installs an init script at `~/.gradle/init.d/`.
> Every Gradle project on your machine gets OpenSpec automatically. No `plugins {}` block needed.

---

## Task Catalog

```bash
./gradlew opsx          # list everything
```

### 🔍 Discovery — Understand Your Project

| Task | What it does |
|---|---|
| `opsx-context` | Project metadata, module graph, resolved deps, frameworks, git info |
| `opsx-tree` | Source tree with file counts per package (supports KMP) |
| `opsx-deps` | Resolved dependency tree (actual versions after conflict resolution) |
| `opsx-modules` | Module graph with source counts, detected types, Mermaid diagram |
| `opsx-devloop` | Dev workflow: build stack, test frameworks, useful flags, module table |
| `opsx-arch` | Architecture analysis: dependency graph, layers, entry points, smells |

### 🧠 Code Intelligence — Replace grep with Gradle

| Task | What it does | Key options |
|---|---|---|
| `opsx-symbols` | Full symbol index with usage counts | `-Psymbol=Name` `-Pfile=path` |
| `opsx-find` | Find all usages of a symbol (imports, calls, type refs) | `-Psymbol=BookRepository` |
| `opsx-rename` | Safe rename across the codebase with preview | `-Pfrom=Old -Pto=New -PdryRun=true` |
| `opsx-calls` | Method-level call graph with Mermaid diagrams | `-Pmodule=shared` |

> Code intelligence works on **Kotlin** and **Java** — including KMP multi-source-set projects.

### 📋 Workflow — Proposals & Tracking

| Task | What it does |
|---|---|
| `opsx-sync` | Generate all agent files (context + skills + commands) |
| `opsx-propose` | Create a change proposal with task scaffolding |
| `opsx-apply` | Mark a proposal ready to implement |
| `opsx-archive` | Archive a completed proposal |
| `opsx-status` | Dashboard with progress bars for proposals |

### 🛠️ Utilities

| Task | What it does |
|---|---|
| `opsx-clean` | Remove all generated files |
| `opsx-install` | Install globally via init script |

---

## What It Does

### Build-Model Context

```bash
./gradlew opsx-context
```

Generates `.opsx/context.md` — a structured snapshot from the Gradle build model:

- Project metadata (name, group, version, Gradle/Java/Kotlin versions)
- Module graph with inter-module dependencies
- Resolved dependencies (actual versions after conflict resolution and BOMs)
- Detected frameworks (Spring Boot, Android, Ktor, Compose, KMP, etc.)
- Git info (branch, remote)

All cached via `@CacheableTask` — only regenerates when build files change.

### Architecture Analysis

```bash
./gradlew opsx-arch
```

Generates a full architecture report: component classification (controllers, services, repositories, entities), dependency graph with Mermaid diagrams, sequence diagrams, layer analysis, hub classes, and code smell detection. Works on any project — JVM, KMP, Android, Spring, CLI, library.

### Code Intelligence

```bash
./gradlew opsx-symbols                          # all symbols
./gradlew opsx-find -Psymbol=BookRepository     # find usages
./gradlew opsx-calls -Pmodule=shared            # call graph
./gradlew opsx-rename -Pfrom=Foo -Pto=Bar -PdryRun=true  # preview rename
```

Replaces `grep`/`sed`/Python scripts with build-aware symbol analysis. The index resolves qualified names through imports, distinguishes declaration/call/type-ref/constructor/supertype references, and generates Mermaid flowcharts and sequence diagrams for call graphs.

### Agent Skills & Commands

```bash
./gradlew opsx-sync
```

Generates skill and command files formatted for your agent. Skills encode workflows (propose, apply, archive, explore, sync, verify) so the agent knows *how* to work with your project — not just what's in it.

### Task Tracking

Break work into tasks with dependencies:

```bash
./gradlew opsx-propose --name=add-user-auth
```

Edit `opsx/changes/add-user-auth/tasks.md`:

```markdown
- [ ] `aua-1` Create User model
- [ ] `aua-2` JWT service → depends: aua-1
  - [ ] `aua-2.1` Token generation
  - [ ] `aua-2.2` Token validation
- [ ] `aua-3` Login endpoint → depends: aua-1, aua-2
```

Track progress:

```bash
./gradlew opsx-status                  # dashboard with progress bars
./gradlew opsx-aua-1 --set=progress    # start a task
./gradlew opsx-aua-1 --set=done        # finish it
```

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
| `crush` | Crush | `.crush/commands/` · `.crush/skills/` |

Combine agents: `github,claude` · Per-project overrides global · Set `none` to disable.

---

## Zero-Config Init Script

```bash
./gradlew opsx-install
```

Installs to `~/.gradle/init.d/openspec-init.gradle.kts`. Every Gradle project on your machine gets OpenSpec — no `plugins {}` block needed. Set your agent once in `~/.gradle/gradle.properties`:

```properties
zone.clanker.openspec.agents=claude
```

---

## Why Gradle?

Most AI context tools work outside the build system — scanning files, guessing at structure, producing static snapshots. JVM builds are different:

- **Dependencies resolve at build time.** BOMs, platforms, version catalogs, conflict resolution — the actual dependency tree isn't in any single file. Gradle knows it.
- **Modules form a graph.** Multi-project builds, included builds, composite builds — relationships live in the build model, not the filesystem.
- **Tasks are a DAG.** Gradle's task graph is a structured, dependency-aware execution plan — exactly what an agent needs to reason about a project.
- **Symbols need resolution.** `grep` finds text; Gradle tasks resolve qualified names through imports and cross-reference Kotlin and Java sources.
- **No extra toolchain.** No Node.js. No Python. Runs on the tool you already have.
- **Nothing gets committed.** Generated files live in `.opsx/` and agent-specific directories, excluded via global gitignore. Per-developer, ephemeral, always regenerated.

**IDE-grade knowledge without the IDE.** IntelliJ talks to Gradle through the Tooling API to resolve imports and understand your project. openspec-gradle brings that same build-model knowledge to any agent, anywhere, through plain Gradle tasks.

---

## Roadmap

- **`opsx-verify`** — Validate that code changes conform to declared architecture rules (e.g., UI packages don't import data packages directly).
- **Deterministic refactoring.** Expand `opsx-rename` into a full refactoring toolkit — extract, move, inline — with IDE-quality safety.
- **Pre-approved execution.** Gradle tasks are sandboxed and declarative — agents chain tasks without prompting for approval on each step.
- **Architecture pattern detection.** Pluggable, community-driven skills that identify and enforce patterns (MVVM, Clean Architecture, etc.).

---

## Design Principles

- **Zero config.** One property. No DSL blocks. No YAML files.
- **Nothing committed.** All generated content excluded via global gitignore.
- **Gradle is the API.** Every output comes from a cacheable, composable task.
- **Code is truth.** Architecture analysis reads code, not docs. Documentation can lie; code can't.
- **Agent-agnostic.** Same structured context, formatted for each agent's conventions.
- **Per-developer.** Different agents, different proposals, no conflicts.

---

## License

[MIT](LICENSE)
