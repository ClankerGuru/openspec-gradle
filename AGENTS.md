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
build-logic/                        # Convention plugins (included build)
├── src/main/kotlin/
│   └── openspec-module.gradle.kts  # Shared config for all submodules

core/                               # Data models, tracking logic, version info
psi/                                # Symbol parsing (Kotlin regex, Java AST), source analysis
arch/                               # Architecture analysis (classifier, dependency analyzer, diagrams)
exec/                               # Agent execution engine (runner, cycle detection, spec parser)
generators/                         # File generators (commands, skills, instructions, gitignore)
adapters/
├── copilot/                        # GitHub Copilot adapter
├── claude/                         # Claude Code adapter
├── codex/                          # Codex adapter
└── opencode/                       # OpenCode adapter
tasks/                              # 22+ Gradle task classes (all opsx-* prefixed)
├── discovery/                      # Context, Deps, Devloop, Modules, Symbols, Tree
├── workflow/                       # Apply, Archive, Propose, Status, TaskItem
├── intelligence/                   # Arch, Calls, Find, Usages, Verify
├── refactoring/                    # Extract, Move, Remove, Rename
└── execution/                      # Clean, Exec, InstallGlobal, Sync
linting/                            # Auto-applied detekt + ktlint plugins
plugin/                             # Settings plugin entry point — the published artifact

gradle/libs.versions.toml           # Version catalog (all dependency versions)
config/detekt.yml                   # Detekt configuration
```

Only `:plugin` is published to Maven Central. All other modules are internal implementation.

## Key Architecture

- **Settings plugin** — applies in `beforeSettings {}` block, not a project plugin
- **Init script** — lives at `~/.gradle/init.d/`, auto-applies to all projects
- **Agent property** — `systemProp.zone.clanker.openspec.agents` in `gradle.properties` (closest scope wins)
- **4 adapters** — Claude, Copilot, Codex, OpenCode (each formats files differently)
- **Instructions delivery**:
  - Claude → `.claude/CLAUDE.md` (standalone, `<!-- OPSX:BEGIN/END -->` markers)
  - Copilot → `.github/copilot-instructions.md` (additive, same markers)
  - Codex/OpenCode → root `AGENTS.md` with markers (append mode)
- **Lifecycle hooks** — `assemble` → `opsx-sync`, `clean` → `opsx-clean`
- **Version from git tag** — `git describe --tags --abbrev=0`, no hardcoded version
- **Convention plugins** — `build-logic/` included build, no `subprojects {}` block
- **Version catalog** — `gradle/libs.versions.toml` for all dependency versions
- **All generated files** go in global gitignore (per-developer, not committed)
- **Proposals** (`opsx/changes/`) ARE committed — they're team-shared artifacts

## Building & Testing

**First time after cloning — install git hooks:**

```bash
curl -fsSL https://raw.githubusercontent.com/ClankerGuru/git-hooks/0.1.0/install.sh | bash
```

This is **mandatory**. Pre-commit runs `./gradlew build`, pre-push blocks direct pushes to `main`.

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
- Always branch + PR — never push to main

## Code Style

- Kotlin-idiomatic: top-level functions, DSLs, composition over inheritance
- No unnecessary interfaces (single impl = just use the class)
- `@UntrackedTask` for tasks reading dynamic project state
- `@CacheableTask` with proper `@InputFiles`/`@OutputFile` for deterministic tasks
- Prefer `trimMargin()` over `buildString` for static templates
