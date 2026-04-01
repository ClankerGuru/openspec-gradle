# plugin-srcx

Settings plugin that provides code intelligence: project discovery, architecture analysis, symbol search, and safe refactoring.

## What it does

Registers the `srcx-*` discovery, intelligence, and refactoring tasks on the root project. These tasks analyze Kotlin and Java source files to produce structured context (dependency trees, module graphs, symbol indices, architecture reports) and perform codebase-wide refactoring operations (rename, move, extract, remove) with dry-run previews.

All discovery tasks use `@CacheableTask` and only regenerate when build files or source files change.

## Why it exists

Replaces grep, sed, and ad-hoc shell scripts with build-aware code analysis. Because the analysis runs inside Gradle, it has access to the resolved dependency tree, module graph, and framework metadata that no external tool can infer from files alone. The symbol index resolves qualified names through imports, distinguishes declaration/call/type-ref/constructor/supertype references, and generates Mermaid diagrams.

## Plugin ID

```
zone.clanker.srcx
```

## Usage

```kotlin
// settings.gradle.kts
plugins {
    id("zone.clanker.srcx") version "<version>"
}
```

```bash
./gradlew srcx-context             # project metadata
./gradlew srcx-find -Psymbol=Foo   # find symbol
./gradlew srcx-rename -Pfrom=Old -Pto=New -PdryRun=true
```

## Tasks

### Discovery

| Task | Output | Description | Key Flags |
|---|---|---|---|
| `srcx-context` | `.opsx/context.md` | Project metadata, module graph, resolved deps, frameworks, git info | -- |
| `srcx-tree` | `.opsx/tree.md` | Source file tree with line counts per package | `-Pmodule=:name`, `-Pscope=...` |
| `srcx-deps` | `.opsx/deps.md` | Resolved dependency tree per configuration | -- |
| `srcx-modules` | `.opsx/modules.md` | Module structure and dependency graph | -- |
| `srcx-devloop` | `.opsx/devloop.md` | Build commands, test framework, run commands, module table | -- |
| `srcx-symbols` | `.opsx/symbols.md` | Symbol index: classes, functions, interfaces with locations and usage counts | `-Pmodule=:name`, `-Psymbol=Name`, `-Pfile=path` |

### Intelligence

| Task | Output | Description | Key Flags |
|---|---|---|---|
| `srcx-arch` | `.opsx/arch.md` | Architecture: component graph, layers, entry points, dependency diagram, smells | `-Pmodule=:name` |
| `srcx-find` | `.opsx/find.md` | Find symbol by name across the codebase | `-Psymbol=Name` (required), `-Pmodule=:name` |
| `srcx-calls` | `.opsx/calls.md` | Method-level call graph with Mermaid diagrams | `-Psymbol=Name` (required), `-Pmodule=:name` |
| `srcx-usages` | `.opsx/usages.md` | Find all usages of a symbol with context | `-Psymbol=Name` (required), `-Pmodule=:name` |
| `srcx-verify` | `.opsx/verify.md` | Enforce architecture rules, fail on violations | `-Pmodule=:name`, `-PmaxWarnings=N`, `-PfailOnWarning=true`, `-PnoCycles=true`, `-PmaxInheritanceDepth=N`, `-PmaxClassSize=N`, `-PmaxImports=N`, `-PmaxMethods=N`, `-PnoSmells=true` |

### Refactoring

All refactoring tasks default to `-PdryRun=true`. Add `-PdryRun=false` to execute.

| Task | Output | Description | Key Flags |
|---|---|---|---|
| `srcx-rename` | `.opsx/rename.md` | Rename symbol across the codebase | `-Pfrom=OldName` (required), `-Pto=NewName` (required), `-PdryRun=true\|false`, `-Pmodule=:name` |
| `srcx-move` | `.opsx/move.md` | Move symbol to a new package | `-Psymbol=Name` (required), `-PtargetPackage=pkg` (required), `-PdryRun=true\|false`, `-Pmodule=:name` |
| `srcx-extract` | `.opsx/extract.md` | Extract lines into a new function | `-PsourceFile=path` (required), `-PstartLine=N` (required), `-PendLine=M` (required), `-PnewName=name` (required), `-PdryRun=true\|false` |
| `srcx-remove` | `.opsx/remove.md` | Remove a symbol or line range, cleaning up imports | `-Psymbol=Name` or `-Pfile=path -PstartLine=N -PendLine=M`, `-PdryRun=true\|false`, `-Pmodule=:name` |

## Key Classes

| Class | Location | Description |
|---|---|---|
| `SrcxPlugin` | `plugin/srcx` | Settings plugin entry point. Registers all tasks on the root project. |
| `ContextTask` | `task/srcx` | Generates project metadata from the Gradle build model. |
| `TreeTask` | `task/srcx` | Builds source file tree with line counts. |
| `DepsTask` | `task/srcx` | Dumps resolved dependency tree. |
| `ModulesTask` | `task/srcx` | Analyzes module structure and inter-module dependencies. |
| `DevloopTask` | `task/srcx` | Detects build stack, test framework, and dev workflow. |
| `SymbolsTask` | `task/srcx` | Builds symbol index from Kotlin/Java sources. |
| `ArchTask` | `task/srcx` | Architecture analysis: components, layers, smells, Mermaid diagrams. |
| `FindTask` | `task/srcx` | Finds symbol declarations and references. |
| `CallsTask` | `task/srcx` | Generates method-level call graphs. |
| `UsagesTask` | `task/srcx` | Finds all usages of a symbol with surrounding context. |
| `VerifyTask` | `task/srcx` | Enforces architecture rules and code quality checks. |
| `RenameTask` | `task/srcx` | Renames a symbol across the entire codebase. |
| `MoveTask` | `task/srcx` | Moves a symbol to a different package. |
| `ExtractTask` | `task/srcx` | Extracts a line range into a new function. |
| `RemoveTask` | `task/srcx` | Removes a symbol or line range, cleaning up imports. |
| `SourceDiscovery` | `lib/psi` | Resolves source directories for projects and source sets. |

## Dependencies

- `lib/core` -- Data models, extensions
- `lib/psi` -- Kotlin/Java source parsing and symbol extraction
- `lib/arch` -- Architecture analysis engine (depends on `lib/psi`)
- `lib/quality` -- Linting integration
- `task/srcx` -- Task class implementations (depends on `lib/core`, `lib/psi`, `lib/arch`, `lib/quality`)
- `compileOnly(gradleApi())`

## Source File Resolution

The plugin discovers source files by scanning all projects (or a specific module with `-Pmodule`) for standard source directories (`src/main/kotlin`, `src/main/java`, etc.) and builds file trees filtered to `**/*.kt` and `**/*.java`. Build file trees include `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, lockfiles, and version catalogs.
