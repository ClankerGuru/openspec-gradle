# Proposal: Local Dependency Substitution

## Problem

You own multiple repositories that depend on each other. To test cross-repo changes, you currently have to:
1. Make changes in the library repo
2. Publish a snapshot (`publishToMavenLocal`)
3. Update the version in the consumer repo
4. Build and test

This is slow and error-prone. Gradle's composite builds (`includeBuild`) solve this — dependencies are substituted with local source automatically. But setting this up per-project is tedious, and it breaks when you move between machines or share repos.

## Goals

- A **single Markdown file** (`~/.gradle/repositories.md`) serves as a machine-level registry of all your repos
- Each entry declares: repo key, git URL, local clone path, GAV coordinates it publishes
- An **init script** reads this file and calls `includeBuild()` for repos that are cloned locally and enabled
- A **property flag** (`zone.clanker.substitute=true/false`) globally enables/disables substitution
- Per-project can opt out via its own `gradle.properties`
- Gradle handles the rest — any dependency matching what the included build produces gets substituted automatically (no explicit `dependencySubstitution` config needed)

## How Gradle Composite Builds Work

Key insight: `Settings.includeBuild("/path/to/repo")` automatically substitutes **all** matching GAV coordinates. If `openspec-gradle` publishes `zone.clanker:openspec-gradle:0.4.5`, any project that depends on that GAV will use the local source instead. No manual mapping needed.

This means the registry only needs to know:
1. Where the repo lives locally
2. Whether substitution is enabled
3. The GAV is discovered automatically by Gradle

However, we still want GAV in the registry for **documentation and validation** — so you can see at a glance what each repo provides.

## Scope

- **In scope**: Registry file format, init script generation/installation, enable/disable properties, validation
- **Out of scope**: Auto-cloning repos, version conflict resolution, CI integration

## Success Criteria

- `~/.gradle/repositories.md` lists all repos with URLs, local paths, and GAV coordinates
- Running any Gradle build with `zone.clanker.substitute=true` automatically substitutes local repos
- Per-project `zone.clanker.substitute=false` disables it for that project
- Clear error if a registered repo's local path doesn't exist
- `./gradlew openspecRepos` prints the registry status (which repos are local, which are substituted)
