# openspec-gradle

[![🤖 clanker](https://img.shields.io/badge/🤖-clanker-black?style=flat-square)](https://github.com/ClankerGuru) [![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![CI](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml/badge.svg)](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml) [![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/zone.clanker.gradle?label=Gradle%20Plugin%20Portal&style=flat-square)](https://plugins.gradle.org/plugin/zone.clanker.gradle) [![Maven Central](https://img.shields.io/maven-central/v/zone.clanker/openspec-gradle?label=Maven%20Central&style=flat-square)](https://central.sonatype.com/artifact/zone.clanker/openspec-gradle) [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)

**Your AI coding agent doesn't know your project. This plugin fixes that.**

openspec-gradle extracts real context from your Gradle build — dependencies, modules, frameworks, versions — and gives it to your AI assistant. No guessing. No grep. Just structured knowledge from the build model itself.

Works with **GitHub Copilot**, **Claude Code**, **OpenAI Codex**, and **OpenCode**.

---

## Quick Start

Add to your `settings.gradle.kts`:

```kotlin
plugins {
    id("zone.clanker.gradle") version "<version>"
}
```

Then run:

```bash
./gradlew opsx-sync
```

Done. Your AI agent now has project context and skills installed.

> **Global install** — want it on every project without touching build files?
> Run `./gradlew opsx-install` to drop an init script into `~/.gradle/init.d/`.
> Every Gradle project on your machine gets OpenSpec automatically.

---

## What It Does

### 1. Project Context

```bash
./gradlew opsx-context
```

Generates `.openspec/context.md` — a snapshot of everything Gradle knows about your project:

- Project metadata (name, group, version, Gradle/Java/Kotlin versions)
- Module graph with inter-module dependencies
- All resolved dependencies (actual versions after conflict resolution)
- Detected frameworks (Spring Boot, Android, Ktor, Compose, KMP, etc.)
- Git info (branch, remote)

Your agent reads this instead of guessing.

### 2. Agent Skills & Commands

```bash
./gradlew opsx-sync
```

Generates skill and command files in the right format for your agent. Each agent gets 7 workflows: `propose`, `apply`, `archive`, `explore`, `new`, `sync`, `verify`.

### 3. Proposal Tracking

Plan a feature, break it into tasks, track them with Gradle commands:

```bash
# Create a proposal
./gradlew opsx-propose --name=add-user-auth

# See the dashboard
./gradlew opsx-status

# Work through tasks
./gradlew opsx-aua-1 --set=progress
./gradlew opsx-aua-1 --set=done
```

Every task in your plan becomes a real Gradle command.

---

## Example: Building a Bookmark CLI

You want to build a small command-line tool to save and search bookmarks.

**1. Start a project**

```bash
mkdir bookmarks && cd bookmarks
gradle init --type kotlin-application --dsl kotlin
```

**2. Add the plugin** to `settings.gradle.kts`:

```kotlin
plugins {
    id("zone.clanker.gradle") version "<version>"
}
```

**3. Set your agent** in `gradle.properties`:

```properties
zone.clanker.openspec.agents=claude
```

**4. Generate context + skills**

```bash
./gradlew opsx-sync
```

**5. Create a proposal**

```bash
./gradlew opsx-propose --name=bookmark-cli
```

This creates `openspec/changes/bookmark-cli/` with `proposal.md`, `design.md`, and `tasks.md`.

**6. Write your task plan** in `tasks.md`:

```markdown
- [ ] `bc-1` Parse CLI arguments (add, search, list)
- [ ] `bc-2` Store bookmarks in a JSON file
  - [ ] `bc-2.1` Save a bookmark (URL + title + tags)
  - [ ] `bc-2.2` Load bookmarks from file
- [ ] `bc-3` Search by tag or title
- [ ] `bc-4` Pretty-print results
```

**7. Track progress**

```bash
./gradlew opsx-status                        # dashboard
./gradlew opsx-bc-1 --set=done               # check off a task
./gradlew opsx-status --proposal=bookmark-cli # see progress
```

**8. Ask your agent to implement**

Your agent already has the context and the skills. Just ask:

> "Look at the bookmark-cli proposal and implement bc-2"

It reads `.openspec/context.md`, reads the proposal, and knows exactly what to do.

---

## Configuration

Set your agent in `gradle.properties` (project-level or `~/.gradle/gradle.properties` for global default):

```properties
zone.clanker.openspec.agents=claude
```

| Value | Agent | Generated files |
|---|---|---|
| `github` | GitHub Copilot | `.github/prompts/` · `.github/skills/` |
| `claude` | Claude Code | `.claude/commands/` · `.claude/skills/` |
| `codex` | OpenAI Codex | `.codex/skills/` |
| `opencode` | OpenCode | `.opencode/commands/` · `.opencode/skills/` |

Combine agents: `zone.clanker.openspec.agents=github,claude`

Per-project overrides global. Set `none` to disable.

---

## Tasks

Run `./gradlew opsx` to see everything:

| Task | Description |
|---|---|
| `opsx` | List all available tasks |
| `opsx-context` | Generate project context snapshot |
| `opsx-sync` | Generate agent skills + commands + context |
| `opsx-propose` | Create a new change proposal |
| `opsx-apply` | Mark a proposal ready to implement |
| `opsx-archive` | Archive a completed proposal |
| `opsx-status` | Dashboard with progress bars |
| `opsx-<code>` | View or update a specific task |
| `opsx-clean` | Remove all generated files |
| `opsx-install` | Install globally via init script |

---

## How Task Tracking Works

Write a checklist in `tasks.md`. Codes are auto-generated from the proposal name:

```
add-user-auth → prefix aua → tasks aua-1, aua-2, aua-2.1, etc.
```

```markdown
- [ ] `aua-1` Create User model
- [ ] `aua-2` JWT service → depends: aua-1
  - [ ] `aua-2.1` Token generation
  - [ ] `aua-2.2` Token validation
- [ ] `aua-3` Login endpoint → depends: aua-1, aua-2
```

- `[ ]` todo · `[~]` in progress · `[x]` done
- `→ depends: aua-1` — can't mark done until dependency is done
- When all children finish, the parent auto-completes

---

## Gitignore

Generated files are added to your **global** gitignore (`~/.config/git/ignore`), not the project's `.gitignore`. Different developers can use different agents without polluting the repo.

---

## FAQ

**Do I need to change my build.gradle.kts?**
No. Add the plugin in `settings.gradle.kts` or use the global init script. Your build files stay clean.

**Can teammates use different agents?**
Yes. Each person sets their own `gradle.properties`. Generated files are per-developer.

**What goes in context.md?**
Everything Gradle knows: project metadata, all dependencies with resolved versions, module graph, detected frameworks, git info. Cached — only regenerates when build files change.

---

## License

[MIT](LICENSE)
