# Design: composite-build-dsl

## The Core Problem

Given this repo graph:

```
host-app (demo/host app)
├── com.test:feature-ui       → feature-ui lib (local)
│   ├── com.test:core-models  → core-models lib (local)
│   └── com.test:core-utils   → core-utils lib (local)
├── com.test:feature-data     → feature-data lib (local)
│   ├── com.test:core-models  → core-models lib (local)
│   └── com.test:core-utils   → core-utils lib (local)
├── com.test:core-models      → core-models lib (local, direct)
└── com.test:core-utils       → core-utils lib (local, direct)
```

We need all 4 libs included as composite builds with dependency substitution, and we need the transitive deps (feature-ui → core-models) to also resolve to local projects.

## Spike Test Results (cbd-1)

**9 tests, all passing.** Key findings:

### 1. Flat substitution propagates transitively — YES

When you include all builds flat at the root and declare substitutions, Gradle applies them globally. `feature-ui`'s dependency on `com.test:core-models:1.0.0` resolves to the local project even though the substitution was declared on `core-models`'s `includeBuild` block, not on `feature-ui`'s.

**This means no init scripts, no nested `includeBuild`, no requiring the monolith plugin in each lib.**

### 2. Substitution is GLOBAL and ALL-OR-NOTHING per artifact

Once you declare `substitute(module("com.test:core-models")).using(project(":"))`, **every build in the composite** gets the local version. You cannot scope substitution to a single consumer.

- If you substitute `core-models`, both `feature-ui` AND `feature-data` get the local version — no way to give one the published version and the other the local one.
- If you DON'T substitute an artifact, any build that depends on it must resolve it from a Maven repo — there's no implicit substitution.

**Implication for the DSL:** The tree structure (`bazLib.includeBuild(barLib)`) is purely organizational. The actual Gradle wiring is always flat. The `substitute: true/false` flag in `monolith.json` is per-artifact, not per-consumer. The tree DSL expresses developer intent ("I know bazLib needs barLib") but doesn't change substitution behavior.

### 3. Directory names with spaces work

`includeBuild("path/to/Core Models Lib")` works fine in Gradle. The directory name becomes the build name. However, type-safe dependency accessors may break with spaces, so we still need `name =` sanitization.

### 4. Duplicate `rootProject.name` across builds — solved by `name =`

Two included builds can both have `rootProject.name = "app"` if you set different `name =` on their `includeBuild` blocks. Gradle uses the `name` (not `rootProject.name`) for disambiguation.

### 5. Partial substitution fails as expected

If you include `feature-data` but don't substitute its dependency `core-utils`, the build fails with "Could not resolve com.test:core-utils". Substitution is explicit, never implicit.

## Approach: Flat Inclusion with Tree DSL Sugar

Since flat substitution works transitively, the implementation is straightforward:

1. The tree DSL (`repo.includeBuild(...)`) collects which repos should be included
2. The plugin flattens the tree, deduplicates, and calls `settings.includeBuild` once per unique repo
3. Each `includeBuild` sets `name = sanitizedBuildName` and applies `dependencySubstitution` if `substitute == true`

No init scripts. No nested settings. No per-consumer scoping (because Gradle doesn't support it).

## DSL Design

### Tree inclusion API on `MonolithRepo`

```kotlin
open class MonolithRepo(...) {
    // Existing fields...

    /** Builds to include within this repo's composite build scope. */
    private val _includes = mutableListOf<MonolithRepo>()

    /** Declare that this repo should include other repos as composite builds. */
    fun includeBuild(vararg repos: MonolithRepo): MonolithRepo {
        _includes.addAll(repos)
        return this  // enables chaining: bazLib.includeBuild(barLib)
    }

    /** All repos this one includes (for the plugin to process). */
    val includedBuilds: List<MonolithRepo> get() = _includes.toList()
}
```

### Usage in settings.gradle.kts

```kotlin
monolith {
    fooApp.includeBuild(
        barLib,
        bazLib.includeBuild(barLib),
        mozLib
    )
}
```

This builds a tree:
```
fooApp
├── barLib
├── bazLib
│   └── barLib
└── mozLib
```

But the plugin flattens it to: include `fooApp`, `barLib`, `bazLib`, `mozLib` — each once, all at root level.

### Alternative: just use `includeEnabled()`

Since substitution is global, `includeEnabled()` already does the right thing for most cases. The tree DSL is useful when:
- You want to document which repos depend on which
- You only want to include a subset (not all enabled repos)
- You want validation that the dependency graph is satisfied

### Processing the tree in `MonolithPlugin`

```kotlin
fun includeTree(root: MonolithRepo) {
    val action = includeAction
        ?: error("includeTree() can only be called from settings.gradle.kts")

    // Collect all unique repos from the tree (depth-first)
    val allRepos = mutableLinkedSetOf<MonolithRepo>()
    fun collect(repo: MonolithRepo) {
        if (allRepos.add(repo)) {
            repo.includedBuilds.forEach { collect(it) }
        }
    }
    // Include the root itself
    allRepos.add(root)
    root.includedBuilds.forEach { collect(it) }

    // Validate no duplicate sanitized names
    val names = allRepos.groupBy { it.sanitizedBuildName }
    val dupes = names.filter { it.value.size > 1 }
    if (dupes.isNotEmpty()) {
        val detail = dupes.entries.joinToString { (name, repos) ->
            "'$name' ← ${repos.joinToString { it.repoName }}"
        }
        throw GradleException("Duplicate build names after sanitization: $detail")
    }

    // Include each unique repo
    allRepos.forEach { action(it) }
}
```

## Build Name Sanitization

### Problem
`includeBuild("path/to/My Cool Project")` → Gradle uses the directory name as the build name → `"My Cool Project"` breaks type-safe accessors.

### Solution
Set `name` explicitly on `ConfigurableIncludedBuild`:

```kotlin
settings.includeBuild(repo.clonePath) {
    name = repo.sanitizedBuildName
    // ...
}
```

### Sanitization rules

```kotlin
val sanitizedBuildName: String
    get() {
        val raw = directoryName
        return raw
            .replace(Regex("[^a-zA-Z0-9_-]"), "-")  // spaces/special → hyphens
            .replace(Regex("-+"), "-")                // collapse multiple hyphens
            .trim('-')                                // no leading/trailing hyphens
            .lowercase()
    }
```

Examples:
- `"My Cool Project"` → `"my-cool-project"`
- `"bazLib (v2)"` → `"bazlib-v2"`
- `"foo__bar"` → `"foo-bar"` (underscores kept, but could normalize)

## Duplicate Project Name Handling

### Problem
Multiple included builds may have `:app` subprojects. Gradle distinguishes them by build name:
- `fooApp:app` vs `bazLib:app`

But type-safe accessors use the **build name** as a prefix. With sanitized names, this works naturally:
- `gradle.includedBuild("foo-app").task(":app:assemble")`

### For `dependencySubstitution`
`project(":app")` in a `dependencySubstitution` block scopes to the specific included build being configured. So:

```kotlin
settings.includeBuild("path/to/fooApp") {
    name = "foo-app"
    dependencySubstitution {
        // ":app" here means fooApp's :app, not bazLib's :app
        substitute(module("com.example:foo-app")).using(project(":app"))
    }
}
```

This already works correctly — the `project()` reference is scoped to the included build.

### Extra safety: validation
When processing the tree, validate that no two included builds get the same sanitized name. Fail with clear error listing the colliding repos.

## Key Decisions

1. **Flat inclusion is sufficient** — spike tests proved that Gradle's `dependencySubstitution` propagates transitively across all included builds. No init scripts or nested settings needed.

2. **Substitution is global, not per-consumer** — once an artifact is substituted, ALL builds in the composite get the local version. The tree DSL expresses intent but doesn't change this behavior. There is no way to say "feature-ui gets local core-models but feature-data gets published."

3. **Tree DSL is sugar over flat inclusion** — `repo.includeBuild(...)` collects repos, the plugin flattens and deduplicates, then calls `settings.includeBuild` once per unique repo.

4. **`includeBuild()` returns `this`** — enables the chaining syntax `bazLib.includeBuild(barLib)` as an expression inside `fooApp.includeBuild(...)`.

5. **Sanitize at include time, not at registration** — `MonolithRepo.directoryName` stays as-is (it's the actual directory name). `sanitizedBuildName` is a derived property used only when calling `settings.includeBuild`.

6. **Deduplication** — the tree may reference `barLib` multiple times (once under fooApp, once under bazLib). We flatten and deduplicate before calling `includeBuild`.

7. **`includeEnabled()` gets sanitization too** — existing flat path benefits from `sanitizedBuildName` automatically.

## Risks

1. **Name collisions after sanitization** — two repos could sanitize to the same name. Mitigated by validation with clear error messages.

2. **Deep nesting performance** — deeply nested trees mean many included builds, which slows Gradle configuration. This is a Gradle limitation, not something we can fix.

3. **IDE support for included builds** — IDEs may struggle with many composite builds. This is inherent to the approach, not introduced by our DSL.

4. **No per-consumer substitution** — users who want "local core-models for feature-ui but published for feature-data" cannot achieve this with Gradle's composite build model. This is a Gradle limitation. Workaround: don't include the repo you want to keep on published, or use separate Gradle invocations.
