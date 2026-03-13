# Vision

## Gradle-native alternative to OpenSpec

openspec-gradle started as a way to bring [OpenSpec](https://openspec.dev)-style project instructions to Gradle builds. Rather than wrapping or depending on OpenSpec, it takes a different approach: extract context directly from the Gradle object model and generate tool-specific files natively.

This means no external toolchain, no YAML/JSON spec files to maintain, and no sync issues between your build config and your AI assistant's understanding of the project.

## The context engine

The core idea is that Gradle already knows almost everything an AI assistant needs:

- What modules exist and how they depend on each other
- Every dependency and its version
- What frameworks are in use (Spring Boot, Micronaut, Android, etc.)
- Source set layout
- Git state

Rather than asking developers to manually describe their project in markdown files, openspec-gradle extracts this automatically and writes it in whatever format each AI tool expects.

This is the **context engine** — a structured extraction layer that turns Gradle's build model into AI-consumable project context.

## Current state

What's built today:

- **6 tool adapters** — GitHub Copilot, Claude Code, Cursor, Codex, OpenCode, Crush
- **Zero-config operation** — global init script, auto-applies to projects, no `plugins {}` block needed
- **Context extraction** — project metadata, module dependency graph, resolved dependency versions, framework detection (30+ frameworks), architecture/migration hints, git info, source sets
- **Lifecycle management** — auto-cleanup of removed agents, auto `.gitignore` entries

## Where it's heading

Planned directions (not yet built):

- **Dependency classification** — Categorizing dependencies as local (included builds, modifiable), organization (same group), or external (third-party, read-only)
- **Development loop generation** — Per-module build/test commands derived from the task graph
- **Composite build awareness** — Substitution mappings and cross-build impact analysis
- **Richer context** — Build performance data, test results, code quality metrics as context
- **Smart templates** — Command templates that reference `./gradlew openspecContext` instead of `find`/`grep`
- **Full OpenSpec replacement** — For Gradle projects, openspec-gradle should be the only tool needed to set up AI assistant integration

The end goal: if you use Gradle, this plugin gives your AI assistant complete project understanding with zero manual setup.
