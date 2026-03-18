# AGENTS.md — For AI Coding Assistants

> This project builds the **openspec-gradle** plugin (`zone.clanker.gradle`).
> It is a **Gradle Settings plugin** that auto-applies via init script.

## Golden Rule

**Use Gradle tasks. Never write scripts.**

```bash
./gradlew opsx          # see all available tasks
```

Every `opsx-*` task is self-documenting — run it with `--help` or read the `description` in the task class.

| Instead of... | Use... |
|---|---|
| Manually analyzing code | `./gradlew opsx-arch` |
| `grep` for symbols | `./gradlew opsx-find -Psymbol=ClassName` |
| Shell scripts to rename | `./gradlew opsx-rename -Pfrom=Old -Pto=New -PdryRun=true` |
| Guessing dependencies | `./gradlew opsx-deps` |
| Creating change proposals | `./gradlew opsx-propose --name=my-feature` |

## Project Structure

```
src/main/kotlin/zone/clanker/gradle/
├── OpenSpecSettingsPlugin.kt    # Settings plugin entry point (beforeSettings {})
├── OpenSpecExtension.kt         # DSL extension
├── tasks/                       # 18 Gradle tasks (all opsx-* prefixed)
├── generators/                  # File generators (commands, skills, instructions, gitignore)
├── templates/                   # TemplateRegistry — command/skill template definitions
├── psi/                         # Code intelligence (Kotlin regex parser, Java parser, symbol index)
├── arch/                        # Architecture analysis (classifier, dependency analyzer, diagrams)
└── tracking/                    # Proposal tracking (parser, scanner, dependency graph, reconciler)

src/main/resources/templates/    # Markdown templates for agent files
├── commands/                    # Slash command templates
├── skills/                      # Skill templates
└── instructions.md              # Root instructions template

src/test/kotlin/                 # 236 tests (unit + Gradle TestKit integration)
```

## Key Architecture

- **Settings plugin** — applies in `beforeSettings {}` block, not a project plugin
- **Init script** — lives at `~/.gradle/init.d/`, auto-applies to all projects
- **Agent property** — `zone.clanker.openspec.agents` (lazy provider, project overrides global)
- **5 adapters** — Claude, Copilot, Codex, OpenCode, Crush (each formats files differently)
- **Instructions delivery**:
  - Claude → `.claude/CLAUDE.md` (standalone)
  - Copilot → `.github/instructions/opsx.instructions.md` (additive)
  - Codex/OpenCode/Crush → root `AGENTS.md` with `<!-- OPSX:BEGIN -->` markers
- **Lifecycle hooks** — `assemble` → `opsx-sync`, `clean` → `opsx-clean`
- **Version from git tag** — `git describe --tags --abbrev=0`, no hardcoded version
- **All generated files** go in global gitignore (per-developer, not committed)
- **Proposals** (`opsx/changes/`) ARE committed — they're team-shared artifacts

## Building & Testing

```bash
./gradlew build                     # compile + test + validatePlugins
./gradlew test --no-daemon          # reliable test runs
./gradlew publishToMavenLocal       # local install (uses -LOCAL suffix)
```

- Java 17 required
- `--no-daemon` recommended for test reliability
- Pre-commit hook runs full build — never bypass with `--no-verify`
- Never publish from local — CI only via GitHub Actions
- Never use `[skip ci]` in commit messages

## Code Style

- Kotlin-idiomatic: top-level functions, DSLs, composition over inheritance
- No unnecessary interfaces (single impl = just use the class)
- `@UntrackedTask` for tasks reading dynamic project state
- `@CacheableTask` with proper `@InputFiles`/`@OutputFile` for deterministic tasks
