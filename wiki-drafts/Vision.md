# Vision

## Gradle-native alternative to OpenSpec

openspec-gradle started as a way to bring [OpenSpec](https://openspec.dev)-style project instructions to Gradle builds. Rather than wrapping or depending on OpenSpec, it takes a different approach: extract context directly from the Gradle object model and generate tool-specific files natively.

This means no external toolchain, no YAML/JSON spec files to maintain, and no sync issues between your build config and your AI assistant's understanding of the project.

## Core idea: Gradle tasks as a structured API for AI agents

Instead of AI assistants fumbling with `grep`, `find`, and `cat` to understand a project, they call Gradle tasks that return **deterministic, structured output**. Each task answers a specific question about the project.

The output of one task can feed into another task or an AI prompt. The AI doesn't guess — it queries.

```
./gradlew openspecModules      → structured module graph with cross-repo awareness
./gradlew openspecTree         → scoped ASCII source tree for any module/package
./gradlew openspecDeps         → classified dependency tree (local/org/external)
./gradlew openspecContext      → combined project manifest
```

80-90% of what an AI needs to know about any Gradle project is the same: module dependencies, source structure, dependency versions, build commands. This information should be **standardized** in a consistent output format so AI agents can consume it reliably across every project.

## Project onboarding for AI agents

When a new developer joins a project, they ask the same questions every time. AI agents are no different — except they start from zero every session.

The plugin pre-computes answers to standard questions:

| Gradle computes (deterministic) | AI figures out (per-session) |
|---|---|
| Module dependency graph | "Why is this code structured this way?" |
| Source tree per module/package | Specific implementation decisions |
| Dependencies + resolved versions | Creative and design choices |
| Build/test commands per module | |
| Included build boundaries | |
| Config and doc locations | |

## Composite build awareness

In composite builds, code spans multiple repositories. AI agents have no visibility into what comes from where. The plugin walks across included build boundaries to show the unified picture:

```
app/src/main/kotlin/com/example/app/
├── ui/
│   ├── HomeScreen.kt          ← uses DesignSystem from :design-system
│   └── ProductList.kt         ← uses ProductCard from :ui-components
│
├── Included build: ../design-system/
│   └── src/main/kotlin/com/example/design/
│       ├── DesignSystem.kt
│       └── tokens/
│           ├── Color.kt
│           └── Typography.kt
│
├── Included build: ../ui-components/
│   └── src/main/kotlin/com/example/components/
│       ├── ProductCard.kt
│       └── LoadingState.kt
```

Gradle knows the substitution mappings. The task exposes them so the AI understands where module boundaries are and what code is modifiable vs external.

## Current state

What's built today:

- **6 tool adapters** — GitHub Copilot, Claude Code, Cursor, Codex, OpenCode, Crush
- **Zero-config operation** — global init script, auto-applies to projects, no `plugins {}` block needed
- **Context extraction** — project metadata, module dependency graph, resolved dependency versions, framework detection, architecture hints, git info, source sets
- **Lifecycle management** — auto-cleanup of removed agents, auto `.gitignore` entries
- **OpenSpec workflow** — propose, apply, archive change management

## Planned: task pipeline

Granular tasks that each answer one question, composable into pipelines:

- **`openspecTree`** — scoped ASCII source tree (`--module=app --package=com.example.ui`)
- **`openspecModules`** — module graph with included build awareness
- **`openspecDeps`** — dependency classification (local/org/external) with resolved versions
- **`openspecDevLoop`** — per-module build/test/run commands from the task graph
- **Optional DAGP integration** — if [dependency-analysis-gradle-plugin](https://github.com/autonomousapps/dependency-analysis-gradle-plugin) is applied, consume its output (unused deps, wrong configurations, project graph) for richer context

## Planned: Android Studio / IntelliJ IDEA plugin

A companion IDE plugin that serves as an **execution backend** for AI-driven refactoring. Instead of the AI doing text manipulation (sed, regex find-replace), it tells the IDE to perform proper refactoring operations.

### Why this matters

When an AI renames a class by editing text files, it misses:
- Import updates across the project
- String references, XML layouts, manifest entries
- Kotlin/Java interop references
- Resource references (`R.layout`, `R.string`)
- Dagger/Hilt binding updates
- Navigation graph references

The IDE's refactoring engine handles all of this correctly. The AI should decide **what** to refactor; the IDE should execute **how**.

### How it would work

The IDE plugin exposes actions that Gradle tasks or AI agents can invoke:

- **Rename** — class, method, variable, file (full cross-reference update)
- **Move** — move class/file to different package (updates all imports)
- **Extract interface/superclass** — structural refactoring
- **Change signature** — add/remove/reorder parameters
- **Inline / Extract method** — code restructuring
- **Run inspections** — get IDE-level diagnostics programmatically

### Technical feasibility

IntelliJ Platform provides full programmatic APIs for refactoring:
- `PsiNamedElement.setName()` for rename
- `PsiReference.handleElementRename()` for reference updates
- `RefactoringFactory` for structural refactoring
- External System integration for Gradle ↔ IDE communication

Communication between Gradle tasks and the IDE plugin could use:
- A local socket/REST API the IDE plugin exposes
- File-based command queue (Gradle writes commands, IDE plugin watches and executes)
- Gradle's tooling API connection

This is a **separate project** that complements the Gradle plugin. The Gradle tasks work standalone — if the IDE plugin is available, the AI uses it for precise refactoring. If not, it falls back to normal file editing. Same tasks, same context, optional execution upgrade.

## The end goal

If you use Gradle, this plugin gives your AI assistant complete project understanding with zero manual setup. Standardized output, deterministic queries, composable tasks — the build system becomes the AI's reliable API into the project.
