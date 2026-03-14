# openspec-gradle

[![🤖 clanker](https://img.shields.io/badge/🤖-clanker-black?style=flat-square)](https://github.com/ClankerGuru) [![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![CI](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml/badge.svg)](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml) [![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/zone.clanker.gradle?label=Gradle%20Plugin%20Portal&style=flat-square)](https://plugins.gradle.org/plugin/zone.clanker.gradle) [![Maven Central](https://img.shields.io/maven-central/v/zone.clanker/openspec-gradle?label=Maven%20Central&style=flat-square)](https://central.sonatype.com/artifact/zone.clanker/openspec-gradle) [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)

**AI agents that actually understand your Gradle project.**

openspec-gradle gives your AI coding assistant (Copilot, Claude, Codex, OpenCode) real knowledge about your project — dependencies, modules, frameworks — extracted straight from Gradle. No guessing, no grep.

---

## What does it do?

1. **Reads your Gradle build** and creates a project snapshot (dependencies, modules, plugins, versions)
2. **Generates skill/command files** so your AI agent knows how to work with your project
3. **Tracks proposals** — plan a feature, break it into tasks, check them off with Gradle commands

You don't change your build files. You don't configure anything. It just works.

---

## 5-Minute Setup

### 1. Install the plugin (once, for all projects)

```bash
git clone https://github.com/ClankerGuru/openspec-gradle.git
cd openspec-gradle
./gradlew publishToMavenLocal
./gradlew opsx-install
```

Done. Every Gradle project on your machine now has OpenSpec.

### 2. Pick your agent

Edit `~/.gradle/gradle.properties`:

```properties
zone.clanker.openspec.agents=claude
```

Options: `github` (Copilot), `claude`, `codex`, `opencode` — or combine them: `github,claude`

### 3. Generate files in any project

```bash
cd your-project
./gradlew opsx-sync
```

That's it. Your agent now has project-aware skills and commands.

---

## Example: Build a Bookmark Manager CLI

Let's say you want to build a small command-line app to save and search bookmarks. Here's how you'd use openspec-gradle with your AI agent.

### Start a new Gradle project

```bash
mkdir bookmarks && cd bookmarks
gradle init --type kotlin-application --dsl kotlin
```

### Generate project context

```bash
./gradlew opsx-sync
```

This creates `.openspec/context.md` — your agent can now read it and know exactly what your project looks like (Kotlin version, dependencies, source layout, etc).

### Create a proposal

```bash
./gradlew opsx-propose --name=bookmark-cli
```

This creates `openspec/changes/bookmark-cli/` with three files:

| File | What it's for |
|---|---|
| `proposal.md` | What you're building and why |
| `design.md` | How it works |
| `tasks.md` | Step-by-step checklist |

Open `tasks.md` and write your plan:

```markdown
- [ ] `bc-1` Parse CLI arguments (add, search, list)
- [ ] `bc-2` Store bookmarks in a JSON file
  - [ ] `bc-2.1` Save a bookmark (URL + title + tags)
  - [ ] `bc-2.2` Load bookmarks from file
- [ ] `bc-3` Search by tag or title
- [ ] `bc-4` Pretty-print results
```

### Track your progress

```bash
# See the dashboard
./gradlew opsx-status

# Start working on a task
./gradlew opsx-bc-1 --set=progress

# Done with it
./gradlew opsx-bc-1 --set=done

# Check progress
./gradlew opsx-status --proposal=bookmark-cli
```

Each task in your plan becomes a real Gradle command. Type `./gradlew opsx` to see them all.

### Use it with your AI agent

Once you've run `opsx-sync`, your agent has skills and commands installed. How you use them depends on the agent:

**Inside the agent (interactive):**

If you're already chatting with Claude Code, Copilot, Codex, or OpenCode — they'll see the generated skill files automatically. Just talk to them:

> "Look at the bookmark-cli proposal and implement bc-1"

The agent reads `.openspec/context.md` for project context, reads the proposal files, and knows what to do.

**From the command line (slash commands):**

Most agents support commands you can run from the terminal:

```bash
# Claude Code
claude /opsx:propose bookmark-cli

# GitHub Copilot (in VS Code)
# Use the @workspace command with the opsx prompt files

# Codex
codex "Read .openspec/context.md and implement task bc-2"
```

---

## All Tasks

Run `./gradlew opsx` to see everything available:

| Task | What it does |
|---|---|
| `opsx` | List all OpenSpec tasks |
| `opsx-sync` | Generate agent files (skills + commands + context) |
| `opsx-context` | Generate `.openspec/context.md` (project snapshot) |
| `opsx-propose --name=X` | Create a new proposal |
| `opsx-apply --name=X` | Mark a proposal ready to implement |
| `opsx-archive --name=X` | Archive a finished proposal |
| `opsx-status` | Show dashboard with progress bars |
| `opsx-clean` | Remove all generated files |
| `opsx-install` | Install globally via init script |
| `opsx-<code>` | Show/update a specific task |
| `opsx-<code> --set=done` | Mark a task complete |

---

## How tasks.md works

Write a checklist. Each item gets a code based on your proposal name:

```
Proposal: add-user-auth → prefix: aua

- [ ] `aua-1` Create User model
- [ ] `aua-2` JWT service → depends: aua-1
  - [ ] `aua-2.1` Token generation
  - [ ] `aua-2.2` Token validation
- [ ] `aua-3` Login endpoint → depends: aua-1, aua-2
```

**Rules:**
- `[ ]` = todo, `[~]` = in progress, `[x]` = done
- `→ depends: aua-1` = can't mark done until aua-1 is done
- When all children finish, the parent auto-completes

---

## Supported Agents

| Set this in gradle.properties | Agent | Where files go |
|---|---|---|
| `github` | GitHub Copilot | `.github/prompts/` + `.github/skills/` |
| `claude` | Claude Code | `.claude/commands/` + `.claude/skills/` |
| `codex` | OpenAI Codex | `.codex/skills/` |
| `opencode` | OpenCode | `.opencode/commands/` + `.opencode/skills/` |

Mix and match: `zone.clanker.openspec.agents=github,claude`

---

## FAQ

**Do I need to add anything to my build.gradle.kts?**
No. The init script handles everything.

**Where do generated files go?**
They're automatically added to your global gitignore. They won't pollute your repo.

**Can different people on the team use different agents?**
Yes. Each person sets their own `gradle.properties`. Generated files are per-developer.

**What's in context.md?**
Project name, group, version, Gradle/Java/Kotlin versions, all dependencies with resolved versions, module graph, detected frameworks (Spring Boot, Android, Ktor, etc.), and git info.

---

## License

[MIT](LICENSE)
