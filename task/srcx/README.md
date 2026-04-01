# srcx

Gradle tasks for project discovery, code intelligence, and automated refactoring via PSI-based source analysis.

## What it does

Provides 15 Gradle tasks that analyze project source code and structure without requiring a running IDE. Tasks fall into three categories: discovery (project metadata and structure), intelligence (symbol search, call graphs, architecture analysis), and refactoring (rename, move, extract, remove). All tasks write their output to `.opsx/*.md` files for consumption by AI agents or human developers.

## Why it exists

AI coding agents need structured project context to make informed changes. IDE-based code intelligence is unavailable in headless environments. This module bridges that gap by providing Gradle-driven source analysis that produces markdown reports an agent can read and act on.

## Tasks

### Discovery

| Task | Gradle Name | Description | Key Properties |
|------|-------------|-------------|----------------|
| `ContextTask` | `srcx-context` | Generates project metadata: name, group, version, Gradle version, plugins, frameworks, dependencies, git info, architecture patterns, package structure. Multi-module projects get per-module context files in `.opsx/context/`. | `buildFiles: ConfigurableFileCollection` (input), `contextFile: RegularFileProperty` (output) |
| `TreeTask` | `srcx-tree` | Generates source file tree with line counts. Supports filtering by module and source set scope. Multi-module projects get per-module tree files in `.opsx/tree/`. | `sourceFiles: ConfigurableFileCollection`, `module: Property<String>` (optional), `scope: Property<String>` (optional, `main` or `test`), `outputFile: RegularFileProperty` |
| `ModulesTask` | `srcx-modules` | Generates module dependency graph with Mermaid diagrams, source counts, plugin types, and inter-module dependency/dependent relationships. Lists included builds for composite projects. | `buildFiles: ConfigurableFileCollection`, `outputFile: RegularFileProperty` |
| `DepsTask` | `srcx-deps` | Resolves full dependency trees per configuration. Shows local module dependencies and external GAV coordinates with transitive dependencies. Falls back to declared (unresolved) dependencies when offline. | `buildFiles: ConfigurableFileCollection`, `outputFile: RegularFileProperty` |
| `DevloopTask` | `srcx-devloop` | Generates a development workflow reference: build/test/run commands, detected stack and test frameworks, lint/format tasks, module summary, OPSX task catalog, and useful Gradle flags. | `buildFiles: ConfigurableFileCollection`, `outputFile: RegularFileProperty` |
| `SymbolsTask` | `srcx-symbols` | Builds a PSI-based symbol index of all declarations (classes, interfaces, functions, properties) with usage locations. Supports filtering by symbol name, file path, or module. Multi-module projects get per-module symbol files in `.opsx/symbols/`. | `sourceFiles: ConfigurableFileCollection`, `module: Property<String>` (optional), `symbol: Property<String>` (optional), `targetFile: Property<String>` (optional, `-Pfile=path`), `outputFile: RegularFileProperty` |

### Intelligence

| Task | Gradle Name | Description | Key Properties |
|------|-------------|-------------|----------------|
| `ArchTask` | `srcx-arch` | Architecture analyzer. Classifies components by role, builds dependency graphs with Mermaid diagrams, generates sequence diagrams for data flow, identifies hub classes and anti-patterns (god classes, circular deps, code smells). Multi-module projects get per-module arch files in `.opsx/arch/`. | `sourceFiles: ConfigurableFileCollection`, `module: Property<String>` (optional), `outputFile: RegularFileProperty` |
| `FindTask` | `srcx-find` | Finds all usages of a symbol across the codebase using PSI analysis. Groups results by file with line numbers and usage context. Prints results to both file and console. | `symbol: Property<String>` (required, `-Psymbol=Name`), `module: Property<String>` (optional), `outputFile: RegularFileProperty` |
| `CallsTask` | `srcx-calls` | Builds a method-level call graph with Mermaid flowchart and sequence diagrams. Outputs a call table with caller, target, and file:line locations. Supports filtering by symbol name. | `module: Property<String>` (optional), `symbol: Property<String>` (optional), `outputFile: RegularFileProperty` |
| `UsagesTask` | `srcx-usages` | Shows all usages of a symbol with file:line locations. Categorizes each usage as import, call, type-ref, supertype, reference, or self-reference using word-boundary matching. | `symbol: Property<String>` (required, `-Psymbol=Name`), `module: Property<String>` (optional), `outputFile: RegularFileProperty` |
| `VerifyTask` | `srcx-verify` | Enforces architecture rules and fails the build on violations. Checks circular dependencies, code smells (Manager/Helper/Util classes), and configurable thresholds for class size, import count, method count, and inheritance depth. | `module: Property<String>` (optional), `maxWarnings: Property<Int>` (optional), `failOnWarning: Property<Boolean>` (default: false), `noCycles: Property<Boolean>` (default: false), `maxInheritanceDepth: Property<Int>` (default: 3), `maxClassSize: Property<Int>` (default: 500), `maxImports: Property<Int>` (default: 30), `maxMethods: Property<Int>` (default: 25), `noSmells: Property<Boolean>` (default: false), `outputFile: RegularFileProperty` |

### Refactoring

All refactoring tasks default to dry-run mode. Add `-PdryRun=false` to apply changes.

| Task | Gradle Name | Description | Key Properties |
|------|-------------|-------------|----------------|
| `RenameTask` | `srcx-rename` | Safe rename across the codebase. Uses `Renamer` to compute all edits (declarations, imports, references) and applies them atomically. Reports edit count and affected files. | `from: Property<String>` (required, `-Pfrom=OldName`), `to: Property<String>` (required, `-Pto=NewName`), `dryRun: Property<Boolean>` (optional), `module: Property<String>` (optional), `outputFile: RegularFileProperty` |
| `MoveTask` | `srcx-move` | Moves a class/file to a different package. Updates the package declaration, computes the new file path, finds and updates all import statements, and cleans up empty parent directories. | `symbol: Property<String>` (required, `-Psymbol=ClassName`), `targetPackage: Property<String>` (required, `-PtargetPackage=new.pkg`), `dryRun: Property<Boolean>` (optional), `module: Property<String>` (optional), `outputFile: RegularFileProperty` |
| `ExtractTask` | `srcx-extract` | Extracts a block of code into a new function. Analyzes the selected lines for free variables (referenced but not declared locally), suggests parameter signatures, and generates the extracted function body with a call-site replacement. | `sourceFile: Property<String>` (required, `-PsourceFile=path`), `startLine: Property<Int>` (required), `endLine: Property<Int>` (required), `newName: Property<String>` (required, `-PnewName=name`), `dryRun: Property<Boolean>` (optional), `outputFile: RegularFileProperty` |
| `RemoveTask` | `srcx-remove` | Removes a symbol or line range from the codebase. For symbol removal: deletes the declaration, cleans up import statements in referencing files, and reports remaining references that will break. For line-range removal: deletes the specified lines. Supports member removal via `ClassName.memberName` syntax. | `symbol: Property<String>` (optional, `-Psymbol=Name`), `sourceFile: Property<String>` (optional, `-Pfile=path`), `startLine: Property<Int>` (optional), `endLine: Property<Int>` (optional), `dryRun: Property<Boolean>` (optional, default: true), `module: Property<String>` (optional), `outputFile: RegularFileProperty` |

## Key Classes

- **Discovery tasks** (`zone.clanker.srcx.tasks.discovery`): `ContextTask`, `TreeTask`, `ModulesTask`, `DepsTask`, `DevloopTask`, `SymbolsTask`
- **Intelligence tasks** (`zone.clanker.srcx.tasks.intelligence`): `ArchTask`, `FindTask`, `CallsTask`, `UsagesTask`, `VerifyTask`
- **Refactoring tasks** (`zone.clanker.srcx.tasks.refactoring`): `RenameTask`, `MoveTask`, `ExtractTask`, `RemoveTask`

Discovery tasks use `@CacheableTask` (except `TreeTask` which is `@UntrackedTask`). Intelligence and refactoring tasks use `@UntrackedTask` since they read dynamic project state.

## Dependencies

- `:lib:core` -- shared data types (`SymbolKind`, `Symbol`, `MethodCall`)
- `:lib:psi` -- PSI-based source parsing (`SourceDiscovery`, `SymbolIndex`, `Renamer`)
- `:lib:arch` -- architecture analysis (`SourceFile`, `ClassifiedComponent`, `ComponentRole`, `AntiPattern`, dependency graph utilities)
- `:lib:quality` -- code quality analysis
- Gradle API (compile-only)
