# openspec-gradle

[![CI](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml/badge.svg)](https://github.com/ClankerGuru/openspec-gradle/actions/workflows/ci.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/zone.clanker.gradle?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/zone.clanker.gradle)
[![Maven Central](https://img.shields.io/maven-central/v/zone.clanker/openspec-gradle?label=Maven%20Central)](https://central.sonatype.com/artifact/zone.clanker/openspec-gradle)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Gradle plugin that generates Markdown skill and command files for AI coding assistants (**GitHub Copilot**, **Claude Code**), matching [OpenSpec](https://github.com/Fission-AI/OpenSpec) output format.

Zero-config. Auto-applies via init script. Agents configured via a single Gradle property.

## Setup

Install the init script globally:

```bash
./gradlew publishToMavenLocal
./gradlew openspecInstallGlobal
```

This installs to `~/.gradle/init.d/openspec.init.gradle.kts` — every Gradle project on your machine now has OpenSpec tasks.

## Configuration

In `~/.gradle/gradle.properties`:

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

**No `plugins {}` block or `openspec {}` DSL needed in project builds.**

## Tasks

```bash
gradle tasks --group=openspec
```

| Task                   | Description |
|------------------------|-------------|
| `openspecSync`         | Generates and installs skill/command files for configured agents |
| `openspecPropose`      | Creates a new change proposal (`--name=<name>`) |
| `openspecApply`        | Applies a proposed change (`--name=<name>`) |
| `openspecArchive`      | Archives a completed change (`--name=<name>`) |
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
│   └── opsx-explore.prompt.md
└── skills/
    ├── openspec-propose/SKILL.md
    ├── openspec-apply-change/SKILL.md
    ├── openspec-archive-change/SKILL.md
    └── openspec-explore/SKILL.md
```

All generated files are auto-added to `.gitignore`.

## License

[MIT](LICENSE)
