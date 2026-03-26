# Monolith Workspace Plugin

The **Monolith plugin** (`zone.clanker.monolith`) manages multi-repo workspaces. Declare your repositories in a JSON file, clone them with one command, include them as composite builds with dependency substitution, and manage branches across all repos.

## Prerequisites

- [GitHub CLI](https://cli.github.com/) (`gh`) installed and authenticated (`gh auth login`)

## Setup

### 1. Create the configuration file

Create `~/dev/monolith/monolith.json` with your repositories:

```json
[
  {
    "name": "MyOrg/core-lib",
    "enable": true,
    "category": "core",
    "substitutions": ["com.example:core-lib,:"],
    "substitute": true,
    "ref": "main"
  },
  {
    "name": "MyOrg/feature-lib",
    "enable": true,
    "category": "features",
    "substitutions": ["com.example:feature-lib,:"],
    "substitute": true,
    "ref": "main"
  },
  {
    "name": "MyOrg/demo-app",
    "enable": true,
    "category": "apps",
    "substitutions": [],
    "substitute": false,
    "ref": "main"
  }
]
```

### JSON fields

| Field | Type | Default | Description |
|---|---|---|---|
| `name` | string | *required* | Full GitHub repo path (`owner/repo`), passed to `gh repo clone` |
| `enable` | boolean | *required* | Whether the repo is active (cloned, included, checked out) |
| `category` | string | *required* | Grouping label for output (e.g., `"core"`, `"features"`) |
| `substitutions` | string[] | *required* | Dependency substitution pairs: `"group:artifact,local-project"` |
| `substitute` | boolean | `false` | Whether to apply dependency substitution when including as composite build |
| `ref` | string | `"main"` | Branch or tag to check out (used by `opsx-checkout`) |

**Substitution format:** Each entry is `"group:artifact,project-path"` — for example, `"com.example:core-lib,:"` substitutes the Maven artifact `com.example:core-lib` with the root project (`:`) of the included build. For multi-module repos, use the subproject path: `"com.example:core-lib,:core"`.

### 2. Apply the plugin

Add to your project's `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("zone.clanker.monolith") version "<version>"
}
```

### 3. Include repos as composite builds

Call `includeEnabled()` to include all enabled repos whose directories exist on disk:

```kotlin
plugins {
    id("zone.clanker.monolith") version "<version>"
}

monolith.includeEnabled()
```

This calls `settings.includeBuild()` for each enabled repo. When `substitute` is `true`, it also wires `dependencySubstitution` using the `substitutions` list from JSON.

**Transitive substitution is automatic.** If `feature-lib` depends on `com.example:core-lib` and both are included with substitution, Gradle resolves `feature-lib`'s dependency to the local `core-lib` project. No extra configuration needed — Gradle's `dependencySubstitution` is global across all included builds.

### 4. (Optional) Override repos via the Settings DSL

Each repo from the JSON becomes a camelCase property on the `monolith` extension:

```kotlin
monolith {
    coreLib.enable(false)          // disable a repo that's enabled in JSON
    featureLib.substitute(true)    // enable substitution for a repo
    featureLib.ref("feature-x")   // override the branch to check out
    demoApp.enable(true)           // enable a repo that's disabled in JSON
}
```

Bracket syntax also works: `monolith["coreLib"].enable(false)`

Property names are derived from the repo's directory name: `MyOrg/core-lib` -> `coreLib`, `MyOrg/feature-lib` -> `featureLib`. DSL overrides win over JSON defaults.

**Available DSL methods:**
| Method | Description |
|---|---|
| `enable(Boolean)` | Toggle whether the repo is active |
| `substitute(Boolean)` | Toggle dependency substitution |
| `ref(String)` | Set branch/tag for checkout |

## Tasks

### Clone

```bash
./gradlew opsx-clone                  # dry-run — preview what will be cloned
./gradlew opsx-clone -PdryRun=false   # actually clone repos
```

Clones all enabled repos in parallel (up to 4 concurrent) via `gh repo clone`. Existing directories are skipped.

### Checkout

```bash
./gradlew opsx-checkout
```

Checks out the configured `ref` (branch or tag) in all enabled repos. For each repo:
1. Stashes uncommitted changes
2. Fetches all remotes
3. Checks out the ref (creates tracking branch if needed, or creates new branch from HEAD if remote doesn't have it)
4. Pulls latest if on `main`/`master`
5. Pops stashed changes

Reports OK/CREATED/SKIP/FAIL per repo.

### Pull

```bash
./gradlew opsx-pull
```

Syncs all enabled repos to the latest `main`:
1. Stashes uncommitted changes
2. Checks out `main` (falls back to `master`)
3. Pulls with `--ff-only`
4. Pops stashed changes

Reports OK/SKIP/FAIL per repo.

### All tasks

| Task | Description |
|---|---|
| `opsx-clone` | Clone repos from JSON config via `gh` (dry-run by default) |
| `opsx-checkout` | Checkout configured ref in all enabled repos |
| `opsx-pull` | Stash, checkout main, pull latest for all enabled repos |

All tasks run in parallel with a thread pool capped at 4.

## Configuration properties

| Property | Default | Description |
|---|---|---|
| `zone.clanker.openspec.monolithFile` | `~/dev/monolith/monolith.json` | Path to the JSON config file |
| `zone.clanker.openspec.monolithDir` | `~/dev/monolith` | Base directory for cloned repos |
| `dryRun` | `true` | Set to `false` to actually clone (opsx-clone only) |

Override via command line:

```bash
./gradlew opsx-clone \
  -Pzone.clanker.openspec.monolithDir=/path/to/workspace \
  -Pzone.clanker.openspec.monolithFile=/path/to/repos.json \
  -PdryRun=false
```

## Composite builds and dependency substitution

When `monolith.includeEnabled()` is called, the plugin:

1. Iterates all enabled repos whose `clonePath` exists on disk
2. Calls `settings.includeBuild(repo.clonePath)` for each
3. If `substitute == true` and `substitutions` is non-empty, configures `dependencySubstitution`:

```kotlin
settings.includeBuild("path/to/core-lib") {
    dependencySubstitution {
        substitute(module("com.example:core-lib")).using(project(":"))
    }
}
```

**Key behavior:**
- Substitution is **global** — once declared, it applies to all builds in the composite
- Substitution is **all-or-nothing per artifact** — you cannot substitute an artifact for one consumer but not another
- Transitive dependencies resolve automatically — if `feature-lib` depends on `com.example:core-lib` and both are included, the local project is used

## Example: Multi-repo feature development

```json
[
  {"name": "MyOrg/core-models", "enable": true, "category": "core", "substitutions": ["com.example:core-models,:"], "substitute": true, "ref": "feature-x"},
  {"name": "MyOrg/core-utils", "enable": true, "category": "core", "substitutions": ["com.example:core-utils,:"], "substitute": true, "ref": "feature-x"},
  {"name": "MyOrg/feature-ui", "enable": true, "category": "features", "substitutions": ["com.example:feature-ui,:"], "substitute": true, "ref": "feature-x"},
  {"name": "MyOrg/host-app", "enable": true, "category": "apps", "substitutions": [], "substitute": false, "ref": "feature-x"}
]
```

```bash
./gradlew opsx-clone -PdryRun=false   # clone all repos
./gradlew opsx-checkout               # checkout feature-x in all repos
```

Now `host-app` includes `feature-ui`, `core-models`, and `core-utils` as local projects. Changes to `core-models` are immediately visible in `feature-ui` and `host-app` without publishing.

```bash
./gradlew opsx-pull                   # sync all repos to latest main when done
```
