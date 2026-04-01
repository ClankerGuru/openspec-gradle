# psi

Source code parsing and symbol indexing for Kotlin and Java files.

## What it does

Builds a cross-referenced symbol index from source files. Extracts declarations (classes, interfaces, objects, functions, properties) and references (imports, calls, type refs, supertypes, constructors) from Kotlin and Java source code. Supports symbol lookup, usage search, call graph construction, and safe cross-codebase renaming.

## Why it exists

Several plugin features need to understand source code structure without compiling it: `srcx-find` searches for symbols, `srcx-usages` finds references, `srcx-calls` traces call graphs, `srcx-rename` performs safe renames, and `srcx-arch` analyzes architecture. The `psi` module provides this analysis as a shared library.

## Parsing Approach

Despite the module name, **actual JetBrains PSI is not used at runtime** for Kotlin files. `KotlinPsiParser` delegates to `KotlinRegexParser`, which uses robust regex-based text parsing to extract declarations and references with line numbers. This avoids needing `kotlin-compiler-embeddable` on the runtime classpath (it is a compile-only dependency).

For Java files, `JavaPsiParser` uses the `javaparser-core` library (`com.github.javaparser.StaticJavaParser`) for proper AST-based extraction of classes, interfaces, enums, methods, fields, imports, extends/implements, and method calls.

The `SymbolIndex` unifies both parsers' output and provides resolution, usage search, and call graph construction with receiver-type disambiguation.

## Key Classes

| Class | Role |
|---|---|
| `SymbolIndex` | Cross-referenced index of all symbols and references. Resolves references to target symbols via qualified name or import-assisted simple name matching. Provides `findUsages`, `findUsagesByName`, `symbolsInFile`, `usageCounts`, and `callGraph`. The `callGraph` method builds method-level call edges with receiver-type disambiguation using constructor parameter analysis. Static `build` factory accepts a list of source files. |
| `SourceDiscovery` | Discovers source directories across Java, Kotlin JVM, and KMP projects. Probes `JavaPluginExtension` source sets, reflectively accesses KMP `kotlin` extension, and falls back to conventional `src/*/kotlin` and `src/*/java` directories. Also collects build scripts and config files for `srcx-find`/`srcx-symbols`. |
| `KotlinPsiParser` | `AutoCloseable` wrapper that delegates to `KotlinRegexParser`. Extracts declarations and references from `.kt` files. |
| `KotlinRegexParser` | Stateless regex-based Kotlin parser. Extracts package declarations, imports, class/interface/object/function/property declarations, function calls, type references, and supertypes with line numbers. Filters out Kotlin keywords and builtin types from reference extraction. |
| `JavaPsiParser` | Parses `.java` files using `javaparser-core`. Extracts classes, interfaces, enums, methods, fields, imports, extends/implements, and method calls. |
| `Renamer` | Computes and applies rename edits across the codebase using the symbol index. `computeRename` returns a preview list of `RenameEdit` entries (file, line, old/new text, kind). `applyRename` writes the edits to disk and renames files when the filename matches the old symbol name. |
| `Renamer.RenameEdit` | A single rename edit with file, line, old text, new text, and kind (declaration, import, call, etc.). |

## Dependencies

- `:lib:core` (api, for `Symbol`, `Reference`, `MethodCall`, `SymbolKind`, `ReferenceKind`)
- `javaparser-core` (implementation, for Java source parsing)
- `kotlin-compiler-embeddable` (compile-only)
- Gradle API (compile-only, for `SourceDiscovery`)
