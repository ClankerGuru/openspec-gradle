# openspec-gradle

[![🤖 clanker](https://img.shields.io/badge/🤖-clanker-black?style=flat-square)](https://github.com/ClankerGuru) [![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![CI](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml/badge.svg)](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml) [![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/zone.clanker.gradle?label=Gradle%20Plugin%20Portal&style=flat-square)](https://plugins.gradle.org/plugin/zone.clanker.gradle) [![Maven Central](https://img.shields.io/maven-central/v/zone.clanker/openspec-gradle?label=Maven%20Central&style=flat-square)](https://central.sonatype.com/artifact/zone.clanker/openspec-gradle) [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)

**Gradle as a dynamic context engine for AI coding agents.**

> *We Gradle'd the prompt.* 🤖

Your build tool already knows more about your project than any shell script ever will — resolved dependencies, module relationships, framework versions, source structure, symbol graphs. openspec-gradle turns that knowledge into structured context that AI coding agents can actually use.

Works with **GitHub Copilot**, **Claude Code**, **OpenAI Codex**, and **OpenCode**.

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

That's it. Your agent now has project context and skills.

> **Go global** — `./gradlew opsx-install` installs an init script at `~/.gradle/init.d/`.
> Every Gradle project on your machine gets OPSX automatically. No `plugins {}` block needed.

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

### 🧠 Code Intelligence — Replace grep with Gradle

| Task | What it does | Key options |
|---|---|---|
| `opsx-arch` | Architecture: dependency graph, layers, entry points, smells | — |
| `opsx-symbols` | Full symbol index with usage counts | `-Psymbol=Name` `-Pfile=path` |
| `opsx-find` | Find all usages of a symbol (imports, calls, type refs) | `-Psymbol=BookRepository` |
| `opsx-calls` | Method-level call graph with Mermaid diagrams | `-Psymbol=ClassName` `-Pmodule=shared` |
| `opsx-rename` | Safe rename across the codebase with preview | `-Pfrom=Old -Pto=New -PdryRun=true` |
| `opsx-move` | Move symbol to a different package | `-Psymbol=Name -PtargetPackage=pkg` |
| `opsx-usages` | Find all usages of a symbol with context | `-Psymbol=Name` |
| `opsx-extract` | Extract lines into a new function | `-PsourceFile=path -PstartLine=N -PendLine=M -PnewName=name` |
| `opsx-remove` | Remove a symbol or line range, cleaning up imports | `-Psymbol=Name` or `-Pfile=path -PstartLine=N -PendLine=M` |
| `opsx-verify` | Enforce architecture rules, fail build on violations | `-PnoCycles -PnoSmells -PmaxClassSize=500` |

> All refactoring tasks default to **dry-run** (`-PdryRun=true`). Add `-PdryRun=false` to execute.
>
> Code intelligence works on **Kotlin** and **Java** — including KMP multi-source-set projects.

### 📋 Workflow — Proposals & Tracking

| Task | What it does |
|---|---|
| `opsx-propose` | Create a change proposal with task scaffolding |
| `opsx-apply` | Mark a proposal ready to implement |
| `opsx-archive` | Archive a completed proposal |
| `opsx-status` | Dashboard with progress bars for active proposals |

### 🛠️ Utilities

| Task | What it does |
|---|---|
| `opsx-sync` | Generate all agent files (context + skills + instructions) |
| `opsx-clean` | Remove all generated files |
| `opsx-install` | Install globally via init script |

---

## What It Does

### Build-Model Context

```bash
./gradlew opsx-context
```

Generates `.opsx/context.md` — a structured snapshot from the Gradle build model. For multi-module projects, per-module detail files are written to `.opsx/context/<module>.md` with a summary index:

- Project metadata (name, group, version, Gradle/Java/Kotlin versions)
- Module graph with inter-module dependencies
- Resolved dependencies (actual versions after conflict resolution and BOMs)
- Detected frameworks (Spring Boot, Android, Ktor, Compose, KMP, etc.)
- Git info (branch, remote)
- Composite build awareness (included builds with branch + available OPSX task paths)

All cached via `@CacheableTask` — only regenerates when build files change.

### Architecture Analysis

```bash
./gradlew opsx-arch
```

Full architecture report: component classification (controllers, services, repositories, entities), dependency graph with Mermaid diagrams, sequence diagrams, layer analysis, hub classes, and code smell detection. For multi-module projects, splits into `.opsx/arch/<module>.md` files. Works on any project — JVM, KMP, Android, Spring, CLI, library.

### Code Intelligence

```bash
./gradlew opsx-symbols                          # all symbols
./gradlew opsx-find -Psymbol=BookRepository     # find usages
./gradlew opsx-calls -Psymbol=BookService       # call graph
./gradlew opsx-rename -Pfrom=Foo -Pto=Bar -PdryRun=true  # preview rename
```

Replaces `grep`/`sed`/Python scripts with build-aware symbol analysis. The index resolves qualified names through imports, distinguishes declaration/call/type-ref/constructor/supertype references, and generates Mermaid flowcharts and sequence diagrams for call graphs.

### Agent Instructions

`opsx-sync` generates root instruction files that teach agents about available tasks:

| Agent | Instructions file | Delivery |
|---|---|---|
| Claude | `.claude/CLAUDE.md` | Appended between `<!-- OPSX:BEGIN -->` markers |
| Copilot | `.github/copilot-instructions.md` | Appended between `<!-- OPSX:BEGIN -->` markers |
| Codex / OpenCode | `AGENTS.md` | Appended between `<!-- OPSX:BEGIN -->` markers |

All agents use marker-based append mode — your existing instruction content is preserved. OPSX adds its section between markers and updates it on every sync.

### Agent Skills

Skills encode workflows (propose, apply, archive, explore, verify) so the agent knows *how* to work with your project — not just what's in it. Each skill provides slash-command-style actions (`/opsx:find`, `/opsx:rename`, `/opsx:status`).

### Dynamic Task Skills

Every task code in your proposals becomes a skill automatically:

```markdown
- [ ] `aua-1` Create User model
- [ ] `aua-2` JWT service → depends: aua-1
```

On next build: `/opsx:aua-1` and `/opsx:aua-2` appear as agent skills, each with full context — the proposal, design, dependencies, and implementation instructions.

### Task Reconciler

The reconciler checks task descriptions against the current codebase symbol index. If a task references a class that no longer exists, you get a warning:

```
⚠️ aua-3 (add-user-auth): references missing symbol(s): UserController → did you mean: BookController?
```

Warnings appear in `opsx-status` and in the generated task commands.

### Task Tracking

Break work into tasks with dependencies:

```bash
./gradlew opsx-propose --name=add-user-auth
```

Edit `opsx/changes/add-user-auth/tasks.md`:

```markdown
- [ ] `aua-1` Create User model
  > verify: symbol-exists User, file-exists src/main/kotlin/com/example/User.kt
- [ ] `aua-2` JWT service → depends: aua-1
  > verify: symbol-exists JwtService
  - [ ] `aua-2.1` Token generation
  - [ ] `aua-2.2` Token validation
- [ ] `aua-3` Login endpoint → depends: aua-1, aua-2
  > verify: symbol-exists LoginController, build-passes
```

Tasks can declare verify assertions with `> verify:` lines. When `--set=done` is run, the build tool checks these assertions — the agent cannot self-certify completion.

Track progress:

```bash
./gradlew opsx-status                  # dashboard with progress bars
./gradlew opsx-aua-1 --set=progress    # start a task
./gradlew opsx-aua-1 --set=done        # verify assertions + finish
```

#### Verify Assertions

| Assertion | What it checks |
|---|---|
| `symbol-exists Foo` | Symbol exists in the symbol index |
| `symbol-not-in Foo.bar` | Symbol does NOT exist (removed/extracted) |
| `file-exists path/to/File.kt` | File exists on disk |
| `file-changed path/to/File.kt` | File was modified (git diff) |
| `build-passes` | Configured Gradle task exits 0 |

Tasks without `> verify:` default to `build-passes`. Configure the build gate:

```properties
# gradle.properties
zone.clanker.openspec.verifyCommand=build          # default (full build)
zone.clanker.openspec.verifyCommand=compileKotlin  # compile only (fast)
zone.clanker.openspec.verifyCommand=opsx-verify    # architecture rules only
```

`--force` bypasses verification but can only be used interactively — automated pipelines cannot skip verification.

### Lifecycle Hooks

OPSX hooks into the standard Gradle lifecycle:

- **`assemble`** → triggers `opsx-sync` (regenerates all agent files)
- **`clean`** → triggers `opsx-clean` (removes `.opsx/` and generated files)

Every `./gradlew assemble` (or `./gradlew build`) keeps your agent context fresh automatically.

---

## Configuration

One property in `gradle.properties`:

```properties
zone.clanker.openspec.agents=claude
```

| Value | Agent | Skills |
|---|---|---|
| `github` | GitHub Copilot | `.github/skills/` |
| `claude` | Claude Code | `.claude/skills/` |
| `codex` | OpenAI Codex | `.agents/skills/` |
| `opencode` | OpenCode | `.opencode/skills/` |

Combine agents: `github,claude` · Per-project overrides global · Set `none` to disable.

---

## Zero-Config Init Script

```bash
./gradlew opsx-install
```

Installs to `~/.gradle/init.d/openspec-init.gradle.kts`. Every Gradle project on your machine gets OPSX — no `plugins {}` block needed. Set your agent once in `~/.gradle/gradle.properties`:

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

- **Pre-approved execution** — Agents chain tasks without prompting for approval on each step.
- **Architecture pattern detection** — Pluggable, community-driven skills that identify and enforce patterns.
- **Worktree support** — Multi-session workflows across git worktrees.

---

## Design Principles

- **Zero config.** One property. No DSL blocks. No YAML files.
- **Nothing committed.** All generated content excluded via global gitignore.
- **Gradle is the API.** Every output comes from a cacheable, composable task.
- **Code is truth.** Architecture analysis reads code, not docs.
- **Agent-agnostic.** Same structured context, formatted for each agent's conventions.
- **Per-developer.** Different agents, different proposals, no conflicts.

---

## Resources

- 📖 [Agentic Execution Lessons](https://github.com/ClankerGuru/openspec-gradle/wiki/Agentic-Execution-Lessons) — Real-world lessons from running AI agents via Gradle

---

## Development Setup

After cloning the repo, install the shared git hooks:

```bash
curl -fsSL https://raw.githubusercontent.com/ClankerGuru/git-hooks/main/install.sh | bash
```

This installs a **pre-commit hook** that runs `./gradlew build` before every commit (build fails → commit blocked) and a **pre-push hook** that prevents direct pushes to `main`.

## License

[MIT](LICENSE)
