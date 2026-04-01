# arch

Architecture analysis: parses source structure, classifies components, detects anti-patterns, and generates diagrams.

## What it does

Scans source directories, parses Kotlin and Java files into structural metadata, classifies each file by its architectural role, builds a dependency graph from imports and supertypes, detects anti-patterns (god classes, circular deps, deep inheritance, missing tests, smell-named classes, premature abstractions), identifies entry points, and generates Mermaid diagrams for dependency visualization and data flow tracing.

## Why it exists

The `srcx-arch` task gives developers an at-a-glance understanding of their project's architecture. Rather than requiring manual diagramming or external tools, this module analyzes the actual source code and produces actionable findings: what components exist, how they relate, where the problems are, and what the data flow looks like.

## Architecture Analysis Pipeline

```
Source directories
    |
    v
SourceParser.scanSources()        -- Parse all .kt/.java files into SourceFile metadata
    |
    v
ComponentClassifier.classifyAll() -- Assign roles (Controller, Service, Repository, etc.)
    |                                 and compute package groups from common prefix
    v
DependencyAnalyzer.buildDependencyGraph()  -- Build edges from imports + supertypes
    |
    +---> findHubClasses()         -- Most-depended-on components
    +---> findCycles()             -- Circular dependency detection
    +---> findEntryPoints()        -- Controllers, main() classes, graph roots
    |
    v
AntiPatternDetector.detectAntiPatterns()   -- God classes, smell names, single-impl
    |                                         interfaces, deep inheritance, circular
    |                                         deps, missing tests
    v
DiagramGenerator.generateDependencyDiagram()   -- Mermaid flowchart grouped by package
DiagramGenerator.generateSequenceDiagrams()    -- Mermaid sequence diagrams from entry points
```

## Key Classes

| Class | Role |
|---|---|
| `SourceFile` | Parsed metadata for a single source file: package, qualified name, imports, annotations, supertypes, interface/abstract/object/data-class flags, language (Kotlin/Java), line count, and method names. |
| `parseSourceFile` | Top-level function that reads a `.kt` or `.java` file and extracts its `SourceFile` metadata without compiling. |
| `scanSources` | Top-level function that walks source directories and parses all Kotlin and Java files. |
| `ComponentRole` | Enum of detected roles: `CONTROLLER`, `SERVICE`, `REPOSITORY`, `ENTITY`, `CONFIGURATION`, `DAO` (annotation-detected); `MANAGER`, `HELPER`, `UTIL` (naming-detected smells); `OTHER`. |
| `ClassifiedComponent` | A `SourceFile` paired with its `ComponentRole` and `packageGroup` (top-level package segment relative to the common base). |
| `classifyComponent` | Classifies a single source file by annotation and naming patterns. |
| `classifyAll` | Classifies all source files and computes package groups from the common package prefix. |
| `commonPackagePrefix` | Finds the longest shared package prefix across all packages. |
| `findEntryPoints` | Identifies entry points: classes with `main()`, annotated controllers, or root nodes in the dependency graph. |
| `ClassDependency` | An edge in the dependency graph from one `ClassifiedComponent` to another. |
| `buildDependencyGraph` | Builds a directed dependency graph from import and supertype analysis. Resolves by qualified name, then simple name. |
| `findHubClasses` | Returns the most-depended-on components sorted by inbound edge count. |
| `findCycles` | Detects circular dependencies in the component graph using DFS. |
| `AntiPattern` | A detected anti-pattern with severity (`WARNING`/`INFO`), message, file, and suggestion. |
| `detectAntiPatterns` | Runs all anti-pattern detectors: smell-named classes (Manager/Helper/Util), single-implementation interfaces, god classes (>30 imports, >25 methods, or >500 lines), deep inheritance (depth >= 3), circular dependencies, and missing test files. |
| `generateDependencyDiagram` | Generates a Mermaid flowchart showing component dependencies, grouped by package. |
| `generateSequenceDiagrams` | Generates Mermaid sequence diagrams tracing data flow from entry points through the dependency graph. |

## Dependencies

- `:lib:psi` (api, which transitively provides `:lib:core`)
