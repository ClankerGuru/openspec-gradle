# Design: repo-clone-plugin

## Approach

### Separate Settings Plugin

A new `MonolithPlugin` class (`Plugin<Settings>`) in the `:plugin` module, registered with its own plugin ID (`zone.clanker.monolith`). Its sole purpose is:

1. Reading `monolith.json` to know which repos should exist locally
2. Cloning missing repos via `gh repo clone` (`opsx-clone` task)
3. (Future) Applying dependency substitutions from the same file

### Configuration

The single source of truth is a JSON file at `~/dev/monolith/monolith.json` (configurable via plugin property):

```json
[
  {
    "name": "ClankerGuru/my-lib",
    "enable": true,
    "category": "internal",
    "substitutions": [
      "com.example:my-lib-api,my-lib",
      "com.example:my-lib-core,my-lib"
    ]
  },
  {
    "name": "ClankerGuru/shared-utils",
    "enable": false,
    "category": "libs",
    "substitutions": [
      "com.example:shared-utils,shared-utils"
    ]
  }
]
```

- **`name`** — full GitHub repo path (`owner/repo`), passed directly to `gh repo clone`
- **`enable`** — when `false`, the entry is skipped entirely
- **`category`** — grouping label for display; not used for clone logic
- **`substitutions`** — each element is `"<artifact>,<project>"` where the left side is the dependency coordinate (group:artifact) and the right side is the local project name that provides it

### Clone Target

The clone directory is derived from the repo name (last segment after `/`):

| name | Clone command | Clone target |
|------|-------------|-------------|
| `ClankerGuru/my-lib` | `gh repo clone ClankerGuru/my-lib ~/dev/monolith/my-lib` | `~/dev/monolith/my-lib/` |
| `OtherOrg/utils` | `gh repo clone OtherOrg/utils ~/dev/monolith/utils` | `~/dev/monolith/utils/` |

### Data Model: `RepoEntry`

```kotlin
@Serializable
data class RepoEntry(
    val name: String,
    val enable: Boolean,
    val category: String,
    val substitutions: List<String>
)
```

- `directoryName` — extracts last segment from `name` (e.g., `ClankerGuru/my-lib` → `my-lib`)
- `parseSubstitution()` — splits `"artifact,project"` into a pair
- `parseFile()` — reads and deserializes the JSON array

### Plugin: `MonolithPlugin`

- Lives in `plugin/src/main/kotlin/zone/clanker/gradle/MonolithPlugin.kt`
- Implements `Plugin<Settings>` — applies via `beforeSettings` in init scripts
- Registers `opsx-clone` as a `CloneTask` on the root project
- Registered as plugin ID `zone.clanker.monolith` in `gradlePlugin {}` block
- Properties wired from project properties:
  - `zone.clanker.openspec.monolithFile` (default `~/dev/monolith/monolith.json`)
  - `zone.clanker.openspec.monolithDir` (default `~/dev/monolith`)
  - `dryRun` (default `true`)

### Task: `opsx-clone`

- `CloneTask` in `:tasks` module
- Uses `ProcessBuilder` to invoke `gh repo clone <name> <target-dir>`

### Flow

1. Read and parse `monolith.json`
2. Filter to entries where `enable == true`
3. For each enabled entry:
   a. Derive target directory: `<reposDir>/<directoryName>/`
   b. Check if target directory exists
   c. If missing and not dry-run: run `gh repo clone <name> <target>`
   d. Report status per entry (cloned / skipped / failed)
4. Print summary grouped by category

## Key Decisions

- **`gh repo clone`** — uses the GitHub CLI, which handles auth via `gh auth login` and supports both HTTPS and SSH transparently. No need for separate org/URL resolution.
- **`name` is the full repo path** — e.g., `ClankerGuru/my-lib`. Passed directly to `gh`, no URL derivation needed.
- **Separate plugin** — `MonolithPlugin` is independent from `OpenSpecSettingsPlugin`. Single responsibility: workspace repo management.
- **Same `:plugin` module** — shares the artifact but has its own plugin ID, keeping deployment simple.
- **`monolith.json` in `init.d/`** — the config file lives alongside init scripts, co-located with Gradle bootstrapping.
- **Dry-run default** — user must pass `-PdryRun=false` to actually clone.

## Risks

- **`gh` not on PATH** — task should fail with a clear error if `gh` is not found, suggesting `brew install gh` or equivalent.
- **Not authenticated** — `gh` will fail if the user hasn't run `gh auth login`; task should surface the error clearly.
- **Missing JSON file** — task should log a warning and exit cleanly if the config file doesn't exist.
