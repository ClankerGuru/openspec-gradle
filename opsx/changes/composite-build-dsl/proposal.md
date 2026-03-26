# Proposal: composite-build-dsl

## Summary

Redesign the monolith DSL to support **tree-shaped composite build inclusion** with transitive dependency substitution, **build name sanitization** (spaces, special chars), and **duplicate project name handling** (multiple repos with `:app` subprojects).

Today `includeEnabled()` is flat — every repo gets included at the root level. But real-world monorepo setups have a graph: a demo app depends on libs, and those libs depend on shared core libs. The DSL should let you express this:

```kotlin
monolith {
    fooApp.includeBuild(
        barLib,
        bazLib.includeBuild(barLib),  // bazLib also substitutes barLib
        mozLib
    )
}
```

## Motivation

1. **Transitive substitution** — `bazLib` depends on `barLib` as a Maven artifact. When both are local, `bazLib`'s build needs to substitute `barLib` too. Today there's no way to express this from the host settings.

2. **Project names with spaces** — `includeBuild("path/to/My Cool Project")` generates broken dependency accessors because Gradle can't handle spaces in type-safe accessors. We need to sanitize build names automatically.

3. **Duplicate `:app` projects** — many repos have an `:app` module. When multiple repos are included, Gradle can't distinguish `fooApp:app` from `bazLib:app`. Each included build needs a unique, scoped name.

4. **Declarative over imperative** — users shouldn't need to understand Gradle's `dependencySubstitution` API. They declare the graph in the DSL; the plugin handles the wiring.

## Scope

### In scope
- Tree DSL: `repo.includeBuild(otherRepo, ...)` with arbitrary nesting depth
- Recursive `settings.includeBuild` calls with correct `dependencySubstitution` at each level
- Build name sanitization: spaces → kebab-case, lowercase, strip special chars
- Duplicate project name disambiguation (prefix with build name or repo name)
- `monolith.json` substitution definitions remain the source of truth for artifact → project mappings
- Integration tests with space-in-name projects and duplicate `:app` subprojects
- Backward compat: `includeEnabled()` continues to work for flat inclusion

### Out of scope
- Modifying the libraries' own `settings.gradle.kts` files
- Automatic substitution discovery (user must declare substitutions in JSON)
- Version conflict resolution between included builds
- IDE-specific fixes beyond what Gradle's type-safe accessors provide
