# plugin-wrkx

Settings plugin that manages multi-repo workspaces with composite build wiring and dependency substitution.

## What it does

Reads a `workspace.json` (or `monolith.json`) configuration file that defines a set of Git repositories. On application, it clones repos, includes them as Gradle composite builds with dependency substitution, and provides tasks to manage branches across all repos. It also aggregates discovery and lifecycle tasks from included builds so that running `opsx-sync` at the root propagates to all included builds.

## Why it exists

Multi-repo Gradle projects need a way to develop across repositories simultaneously. The wrkx plugin replaces manual `includeBuild()` entries and `dependencySubstitution` blocks with a declarative JSON config. It handles cloning, branch management, build name sanitization, and dependency substitution mapping -- turning a collection of repos into a single, composable workspace.

## Plugin ID

```
zone.clanker.wrkx
```

## Usage

```kotlin
// settings.gradle.kts
plugins {
    id("zone.clanker.wrkx") version "<version>"
}
```

### workspace.json format

The configuration file is a JSON array of repository entries. Default location: `~/dev/monolith/workspace.json` (falls back to `monolith.json`).

```json
[
  {
    "name": "ClankerGuru/my-lib",
    "enable": true,
    "substitute": true,
    "substitutions": [
      "zone.clanker:my-lib -> :"
    ],
    "ref": "main"
  }
]
```

| Field | Type | Description |
|---|---|---|
| `name` | `String` | GitHub repo name (e.g., `ClankerGuru/my-lib`) |
| `category` | `String` | Optional grouping category |
| `enable` | `Boolean` | Whether to include this repo by default |
| `substitute` | `Boolean` | Whether to apply dependency substitutions |
| `substitutions` | `List<String>` | Dependency substitution mappings (`artifact -> projectPath`) |
| `ref` | `String` | Git ref to checkout (branch or tag) |

### Configuration properties

| Property | Default | Description |
|---|---|---|
| `zone.clanker.wrkx.configFile` | `~/dev/monolith/workspace.json` | Path to the configuration file |
| `zone.clanker.wrkx.repoDir` | `~/dev/monolith` | Base directory for cloned repos |
| `zone.clanker.wrkx.aggregate` | `true` | Whether to aggregate tasks from included builds |

### Tree DSL for composite builds

Express your dependency tree explicitly:

```kotlin
wrkx {
    featureUi.includeBuild(coreModels, coreUtils)
    hostApp.includeBuild(featureUi, featureData)
}
wrkx.includeTree(wrkx["hostApp"])
```

`includeTree` walks depth-first, deduplicates, validates no build name collisions, and calls `settings.includeBuild` once per unique repo.

DSL accessors use camelCase derived from directory names: `core-models` becomes `coreModels`.

Build names are sanitized: special characters, consecutive hyphens, and leading/trailing hyphens are normalized. Duplicate sanitized names are detected at configuration time with a clear error.

## Tasks

| Task | Description | Key Flags |
|---|---|---|
| `wrkx-clone` | Clone all repositories from workspace.json | `-PdryRun=true\|false` (default: `true`), reads `zone.clanker.wrkx.repoDir` and `zone.clanker.wrkx.configFile` |
| `wrkx-pull` | Pull latest changes for all included repos | -- |
| `wrkx-checkout` | Checkout configured branches (`ref`) for all repos | -- |
| `wrkx-repos` | List all repos with their status | Output: `.opsx/repos.md` |

### Aggregate task wiring

When `zone.clanker.wrkx.aggregate` is `true` (default), the following root tasks automatically depend on their counterparts in all included builds:

**Lifecycle:** `opsx-sync`, `opsx-clean`

**Discovery (all `@CacheableTask`):** `srcx-context`, `srcx-tree`, `srcx-modules`, `srcx-deps`, `srcx-devloop`, `srcx-symbols`, `srcx-arch`

Intelligence tasks (`srcx-find`, `srcx-calls`, `srcx-usages`, `srcx-verify`) are NOT aggregated because they are parameterized and expensive. Use them per-build: `./gradlew :my-lib:srcx-find -Psymbol=Foo`.

## Key Classes

| Class | Location | Description |
|---|---|---|
| `WrkxPlugin` | `plugin/wrkx` | Settings plugin entry point. Reads config, creates extension, includes builds. |
| `WrkxExtension` | `lib/core` | Extension holding all repo entries, `includeEnabled()`, `includeTree()`, and `toCamelCase()`. |
| `WrkxRepo` | `lib/core` | Data class for a single repo: clone path, substitutions, build name sanitization. |
| `RepoEntry` | `lib/core` | Parsed JSON entry from workspace.json. Handles `parseFile()` and `parseSubstitution()`. |
| `CloneTask` | `task/wrkx` | Clones all repos to the configured directory. |
| `PullTask` | `task/wrkx` | Runs `git pull` on all included repos. |
| `CheckoutTask` | `task/wrkx` | Runs `git checkout` to the configured `ref` for each repo. |
| `ReposTask` | `task/wrkx` | Generates a status report of all repos. |

## Dependencies

- `lib/core` -- `WrkxExtension`, `WrkxRepo`, `RepoEntry`
- `task/wrkx` -- Task class implementations (`CloneTask`, `PullTask`, `CheckoutTask`, `ReposTask`)
- `compileOnly(gradleApi())`

## Included Build Wiring

When `includeBuild` is called, the plugin:

1. Checks that the clone path exists on disk
2. Calls `settings.includeBuild()` with the sanitized build name
3. If `substitute` is `true` and substitutions are configured, sets up `dependencySubstitution` rules mapping Maven coordinates to project paths

Auto-include happens for all repos with `enable: true` in workspace.json when entries are found at startup.

## Double-Application Guard

The plugin guards against double-application (e.g., from both an init script and `settings.gradle.kts`). If the `wrkx` extension already exists on the settings object, `apply()` returns immediately.
