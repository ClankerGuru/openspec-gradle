# Proposal: Gradle Task Pipeline — Replace Bash with Tasks

## Problem

Current skills and prompts tell the AI agent to run bash commands: `find`, `grep`, `cat`, `sed`, `cp`, `rm`. This is:

1. **Fragile** — shell commands vary by OS, path assumptions break
2. **Redundant** — Gradle already knows the project structure, dependencies, source sets
3. **Not composable** — bash output is unstructured text, can't be chained
4. **Not cacheable** — every invocation re-runs from scratch

We're building a Gradle plugin. Gradle IS the runtime. Every operation the AI needs should be a Gradle task.

## Goals

Replace every bash-dependent operation in skills/prompts with a Gradle task that:
- Outputs a small, focused Markdown file to `.openspec/`
- Is cacheable (Gradle up-to-date checking)
- Is composable (task outputs feed into other tasks or AI prompts)
- Works on any OS (Gradle abstracts the filesystem)

## What Bash Commands Get Replaced

| Bash Command | Gradle Task | Output |
|---|---|---|
| `find src/ -name '*.kt'` | `openspecTree` | `.openspec/tree.md` |
| `cat build.gradle.kts` | `openspecContext` | `.openspec/context.md` (already done) |
| `grep -r "import" src/` | `openspecDeps` | `.openspec/deps.md` |
| `git log --oneline` | `openspecContext` | included in context (already done) |
| `sed -i 's/old/new/' file` | `openspecPatch` | applies patch, logs to `.openspec/patches.md` |
| `cp src/A.kt src/B.kt` | `openspecScaffold` | creates files from templates |
| `cat openspec/changes/*/tasks.md` | `openspecStatus` | `.openspec/status.md` |
| `ls -la` | `openspecTree` | scoped file listings |

## The Pattern

Every task follows the same contract:

```
Input:  Gradle build model + source files + config
Output: Small Markdown file in .openspec/
Cache:  @CacheableTask with file inputs
```

The AI agent never runs bash. It runs:
```
./gradlew openspecTree --module=app --scope=main
```
Then reads `.openspec/tree.md`.

Skills/prompts say: "Read `.openspec/tree.md` for the source layout" — not "run `find`".

## Scope

- **In scope**: Define the full task catalog, update all skill/prompt templates to reference tasks instead of bash, establish the output-file contract
- **Out of scope**: Implementing every task (that's per-task proposals), IDE integration

## Success Criteria

- Zero bash commands in generated skills/prompts
- Every discovery operation has a corresponding `openspec*` Gradle task
- Each task outputs a focused `.openspec/*.md` file
- Skills reference task outputs, not shell commands
- AI agent workflow: run task → read output file → make decisions → run next task
