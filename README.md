# openspec-gradle

[![CI](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml/badge.svg)](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/zone.clanker.gradle?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/zone.clanker.gradle)
[![Maven Central](https://img.shields.io/maven-central/v/zone.clanker/openspec-gradle?label=Maven%20Central)](https://central.sonatype.com/artifact/zone.clanker/openspec-gradle)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Gradle plugin that generates Markdown skill and command files for AI coding assistants (**GitHub Copilot**, **Claude Code**), matching [OpenSpec](https://github.com/Fission-AI/OpenSpec) output format.

Zero-config. Auto-applies via init script. Agents configured via a single Gradle property.

## Installation

### Option 1: Settings Plugin (per-project)

Add to your `settings.gradle.kts`:

```kotlin
plugins {
    id("zone.clanker.gradle") version "0.2.0"
}
```

### Option 2: Global Init Script (all projects, zero-config)

Clone and install globally:

```bash
git clone https://github.com/ClankerGuru/openspec-gradle.git
cd openspec-gradle
./gradlew installGlobal
```

This installs to `~/.gradle/init.d/openspec.init.gradle.kts` — every Gradle project on your machine now has OpenSpec tasks. **No `plugins {}` block or `openspec {}` DSL needed in project builds.**

### Option 3: Custom Gradle Distribution

Use the [gradle-distribution](https://github.com/ClankerGuru/gradle-distribution) repo to bundle the plugin into a custom Gradle distribution that includes it out of the box.

## Configuration

In `~/.gradle/gradle.properties` (global) or your project's `gradle.properties`:

```properties
# Which agents to generate files for (comma-separated)
# Values: github, claude, none
# Default: github
zone.clanker.openspec.agents=github
```

| Value             | Effect                                      |
|-------------------|---------------------------------------------|
| `github`          | Generates GitHub Copilot files (`.github/`)  |
| `claude`          | Generates Claude Code files (`.claude/`)     |
| `github,claude`   | Generates for both                           |
| `none` or empty   | Cleans all generated files                   |

## Tasks

```bash
gradle tasks --group=openspec
```

| Task                   | Description |
|------------------------|-------------|
| `openspecSync`         | Generates and installs skill/command files for configured agents |
| `openspecPropose`      | Creates a new change proposal |
| `openspecApply`        | Implements tasks from a change |
| `openspecArchive`      | Archives a completed change |
| `openspecClean`        | Removes all generated files |
| `openspecInstallGlobal`| Installs the init script to `~/.gradle/init.d/` |

## Generated Files

For `zone.clanker.openspec.agents=github`:

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

For `zone.clanker.openspec.agents=claude`:

```
.claude/
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

All generated files are auto-added to `.gitignore`.

## License

[MIT](LICENSE)
