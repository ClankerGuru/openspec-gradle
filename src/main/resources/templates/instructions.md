# OPSX — Gradle-Powered Project Intelligence

This project uses **OPSX** (OpenSpec Gradle Plugin) to provide structured project context via Gradle tasks.

## Golden Rule

**NEVER create Python, Bash, sed, awk, or any scripts to analyze or modify this project.**
All project understanding comes from OPSX tasks. All file operations go through Gradle.

## Available Tasks

Run any task with `./gradlew <task>` (or `./gradlew :<module>:<task>` for subprojects).

### Discovery
| Task | What it does |
|------|-------------|
| `opsx-context` | Project metadata, build stack, frameworks, git info |
| `opsx-tree` | Source file tree with line counts |
| `opsx-modules` | Module structure and dependency graph (Mermaid) |
| `opsx-deps` | Resolved dependency tree per configuration |
| `opsx-devloop` | Build commands, test framework, run commands |

### Code Intelligence
| Task | What it does |
|------|-------------|
| `opsx-arch` | Architecture analysis — component graph, layers, data flow |
| `opsx-symbols` | Symbol index — classes, functions, interfaces with locations |
| `opsx-find` | Find symbol by name (use `-Pquery=<name>`) |
| `opsx-calls` | Call graph for a symbol (use `-Pquery=<name>`) |
| `opsx-rename` | Preview rename refactoring (use `-Pold=<old> -Pnew=<new>`) |

### Workflow
| Task | What it does |
|------|-------------|
| `opsx-propose` | Create a new change proposal |
| `opsx-apply` | Implement tasks from a change |
| `opsx-archive` | Archive a completed change |
| `opsx-status` | Show status of all changes |

### Utilities
| Task | What it does |
|------|-------------|
| `opsx` | Show task catalog |
| `opsx-sync` | Regenerate agent files |
| `opsx-clean` | Remove all generated files |
| `opsx-install` | Install init script globally |

## Output Directory

Task outputs are written to `.opsx/` — read these files for project context:
- `.opsx/context.md` — project metadata
- `.opsx/tree.md` — source tree
- `.opsx/modules.md` — module graph
- `.opsx/deps.md` — dependencies
- `.opsx/devloop.md` — build/test/run commands
- `.opsx/arch.md` — architecture analysis
- `.opsx/symbols.md` — symbol index
- `.opsx/calls.md` — call graph

## Workflow

1. **Understand** — Run discovery tasks first (`opsx-context`, `opsx-tree`, `opsx-modules`)
2. **Analyze** — Use code intelligence (`opsx-arch`, `opsx-symbols`, `opsx-find`)
3. **Plan** — Create a proposal (`opsx-propose`)
4. **Implement** — Work through tasks (`opsx-apply`)
5. **Verify** — Check your work matches the spec

## Tips

- Chain tasks: `./gradlew opsx-context opsx-tree opsx-arch` runs all three
- Read `.opsx/` files for cached output instead of re-running tasks
- Use `opsx-find -Pquery=ClassName` to locate symbols quickly
- Use `opsx-calls -Pquery=methodName` to understand call chains
