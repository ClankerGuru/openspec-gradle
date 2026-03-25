# AGENTS.md — For AI Coding Assistants

> This project builds the **openspec-gradle** plugin (`zone.clanker.gradle`).
> It is a **Gradle Settings plugin** that auto-applies via init script.
> It also publishes **3 standalone linting plugins** (`zone.clanker.gradle.linting`, `.detekt`, `.ktlint`).

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
│   ├── openspec-module.gradle.kts  # Shared config: Kotlin/JVM, Java 17, JUnit 5
│   └── openspec-publish.gradle.kts # Maven Central publishing via Vanniktech

core/                               # Data models, task parsing, proposal scanning
├── Model.kt                        #   Symbol, Reference, MethodCall, TaskItem, Proposal
├── TaskParser.kt                   #   Markdown → TaskItem tree (checkbox parsing)
├── TaskCodeGenerator.kt            #   Hierarchical code assignment (e.g. ttd-1.2.1)
├── TaskWriter.kt                   #   Status updates, parent propagation, attempt logs
├── ProposalScanner.kt              #   Discovers proposals in opsx/changes/
├── DependencyGraph.kt              #   Task dependency graph, cycle detection, topological sort
├── OpenSpecExtension.kt            #   Gradle extension (tools list)
└── VersionInfo.kt                  #   Plugin version from properties resource

psi/                                # Symbol parsing — no compiler dependency at runtime
├── KotlinPsiParser.kt              #   Regex-based Kotlin parser (declarations + references)
├── JavaPsiParser.kt                #   JavaParser AST-based Java parser
├── SymbolIndex.kt                  #   Cross-referenced index, usage resolution, call graph
├── SourceDiscovery.kt              #   Source dir detection (JVM, KMP, fallback)
└── Renamer.kt                      #   Compute + apply rename edits safely

arch/                               # Architecture analysis
├── SourceParser.kt                 #   Parse files into SourceFile metadata
├── ComponentClassifier.kt          #   Role detection (Controller, Service, etc.) + entry points
├── DependencyAnalyzer.kt           #   Import-based dependency graph, hub detection, cycles
├── DiagramGenerator.kt             #   Mermaid flowcharts + sequence diagrams
└── AntiPatternDetector.kt          #   God classes, deep inheritance, smell detection

exec/                               # Agent execution engine
├── AgentRunner.kt                  #   Spawns CLI processes (copilot, claude, codex, opencode)
├── CycleDetector.kt                #   Detects oscillating error loops via SHA-256 hashing
└── SpecParser.kt                   #   Parses task-*.md spec files into TaskSpec

generators/                         # File generators
├── SkillGenerator.kt               #   Generates 17 skill templates per tool
├── TaskCommandGenerator.kt         #   Dynamic /opsx:<code> skills from proposals
├── TaskReconciler.kt               #   Validates task symbols against codebase
├── ToolAdapter.kt                  #   Interface + registry for tool-specific formatting
├── InstructionsGenerator.kt        #   Root agent files (CLAUDE.md, copilot-instructions.md)
├── AgentCleaner.kt                 #   Removes opsx files for deselected agents, prunes empty dirs
├── GlobalGitignore.kt              #   Manages ~/.config/git/ignore patterns
├── TemplateRegistry.kt             #   Loads 17 skill templates from resources
└── src/main/resources/templates/   #   17 skill markdown templates

adapters/
├── claude/    ClaudeAdapter        # .claude/skills/ (full YAML frontmatter)
├── copilot/   CopilotAdapter       # .github/skills/ (desc-only YAML frontmatter)
├── codex/     CodexAdapter         # .agents/skills/opsx-*/SKILL.md (unified skill model)
└── opencode/  OpenCodeAdapter      # .opencode/skills/ (HTML comment format)

tasks/                              # 25 Gradle task classes (all opsx-* prefixed)
├── discovery/                      # ContextTask, TreeTask, ModulesTask, DepsTask, DevloopTask, SymbolsTask
├── intelligence/                   # ArchTask, FindTask, UsagesTask, CallsTask, VerifyTask
├── refactoring/                    # RenameTask, MoveTask, ExtractTask, RemoveTask
├── workflow/                       # ProposeTask, ApplyTask, StatusTask, ArchiveTask, TaskItemTask, TaskLifecycle, AssertionRunner
└── execution/                      # SyncTask, CleanTask, ExecTask, OpsxLinkTask, InstallGlobalTask

linting/                            # 3 standalone Gradle plugins (separate from opsx-* tasks)
├── OpenSpecLintingPlugin.kt        #   zone.clanker.gradle.linting — applies both detekt + ktlint
├── OpenSpecDetektPlugin.kt         #   zone.clanker.gradle.detekt — auto-applies detekt via reflection
├── OpenSpecKtlintPlugin.kt         #   zone.clanker.gradle.ktlint — auto-applies ktlint via reflection
└── README.md                       #   Plugin usage docs

plugin/                             # Settings plugin entry point — the published artifact
├── OpenSpecSettingsPlugin.kt       #   Plugin<Settings>, task registration, adapter init
└── src/main/resources/             #   openspec-gradle.properties (version injected at build)

gradle/libs.versions.toml           # Version catalog (junit, javaparser, kotlin, detekt, ktlint)
config/detekt.yml                   # Detekt configuration
```

## Published Artifacts

| Module | Artifact | Type |
|---|---|---|
| `:plugin` | `zone.clanker:openspec-gradle` | **Gradle Settings Plugin** — the main entry point |
| `:linting` | 3 plugins under `zone.clanker.gradle.*` | **Gradle Project Plugins** — detekt, ktlint, linting |
| All others | `zone.clanker:openspec-*` | **Libraries** — bundled into `:plugin` at runtime |

All published to Maven Central via Vanniktech plugin. Version derived from git tags.

## Key Architecture

- **Settings plugin** — applies in `settings.gradle.rootProject {}` block, not a project plugin
- **Init script** — installed to `~/.gradle/init.d/` by `opsx-install`, auto-applies to all projects
- **Agent resolution** — priority: `-P` flag → project `gradle.properties` → global `~/.gradle/gradle.properties` → PATH scan
- **4 adapters** — Claude, Copilot, Codex, OpenCode (each formats files differently via `ToolAdapter` interface)
- **Instructions delivery** (all use marker-based append `<!-- OPSX:BEGIN -->` / `<!-- OPSX:END -->`):
  - Claude → `.claude/CLAUDE.md`
  - Copilot → `.github/copilot-instructions.md`
  - Codex → `AGENTS.md`
  - OpenCode → `AGENTS.md`
- **Linting plugins** — applied via reflection with `compileOnly` deps on detekt/ktlint; skip if already configured; Kotlin-only detection; configurable via system/project properties
- **Task lifecycle** — `--set=done` runs verify assertions → mark DONE → propagate parents → sync skills → reconcile remaining tasks. Assertions declared via `> verify:` lines in tasks.md. `--force` bypasses verification but only works interactively (rejected by automated pipelines). Configurable gate via `zone.clanker.openspec.verifyCommand` property (default: `build`)
- **Lifecycle hooks** — `assemble` triggers `opsx-sync`, `clean` triggers `opsx-clean`
- **Build infrastructure** — Kotlin 2.3, Java 17 toolchain, Gradle 9.4.0, KTS only (no Groovy)
- **Convention plugins** — `openspec-module` (base config) and `openspec-publish` (Maven Central) in `build-logic/`

## Linting Plugins

The `:linting` module produces 3 independent Gradle plugins that can be applied to any Kotlin project:

| Plugin ID | Class | What it does |
|---|---|---|
| `zone.clanker.gradle.linting` | `OpenSpecLintingPlugin` | Applies both detekt + ktlint |
| `zone.clanker.gradle.detekt` | `OpenSpecDetektPlugin` | Auto-configures detekt (static analysis) |
| `zone.clanker.gradle.ktlint` | `OpenSpecKtlintPlugin` | Auto-configures ktlint (code formatting) |

Key behaviors:
- **Non-invasive** — skips if plugin already applied to project
- **Kotlin-only** — only applies to projects with `kotlin("jvm")`, `.android`, or `.multiplatform`
- **Reflection-based** — configures extensions via reflection, deps are `compileOnly`
- **Configurable** — disable via `-Dopenspec.linting.enabled=false` or `gradle.properties`
- **Custom config** — detekt.yml path via `-Dopenspec.detekt.config=...`, ktlint version via `-Dopenspec.ktlint.version=...`
- **Hooks into `check`/`build`** — adds detekt and ktlintCheck as dependencies

## Module Dependency Graph

```
plugin
├── core
├── psi → core
├── arch → psi
├── exec → core
├── generators → core, psi
├── adapters:{claude,copilot,codex,opencode} → generators
├── tasks → core, psi, arch, exec, generators, linting
└── linting (no internal deps)
```
