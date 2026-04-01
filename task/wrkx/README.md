# wrkx

Gradle tasks for managing a multi-repository workspace where multiple Git repositories are cloned, synced, and included as Gradle composite builds.

## What it does

Provides four tasks that manage a fleet of Git repositories from a central workspace project. Repositories are declared in a `workspace.json` config file or via the `WrkxExtension` in `build.gradle.kts`. Tasks handle cloning repos via `gh`, pulling latest changes across all repos, checking out specific branches/tags, and generating a catalog of repo status. All git operations run in parallel using thread pools.

## Why it exists

Large projects span multiple repositories. Gradle composite builds let you develop across repos without publishing artifacts, but managing the cloning, branching, and syncing of those repos is manual and error-prone. This module automates workspace setup and maintenance so that a single `./gradlew` invocation can clone missing repos, sync them to the latest main, or switch all repos to a feature branch.

## Tasks

| Task | Gradle Name | Description | Key Properties |
|------|-------------|-------------|----------------|
| `CloneTask` | `wrkx-clone` | Clones repositories listed in `workspace.json` (or the `WrkxExtension`) using `gh repo clone`. Skips repos that already exist on disk. Runs clones in parallel (up to 4 threads). Validates that `gh` CLI is installed and authenticated. Supports dry-run mode. Groups output by category. | `reposFile: Property<String>` (path to config JSON), `reposDir: Property<String>` (base clone directory), `dryRun: Property<Boolean>`, `extensionRepos: MutableList<WrkxRepo>` (populated by plugin) |
| `PullTask` | `wrkx-pull` | For all enabled repos with existing directories: stashes uncommitted changes, checks out main (falls back to master), pulls with `--ff-only`, and restores stashed changes. Runs in parallel. | `extensionRepos: MutableList<WrkxRepo>` (populated by plugin) |
| `CheckoutTask` | `wrkx-checkout` | Checks out the configured `ref` (branch or tag) in all enabled repos. Fetches all remotes first. If the branch doesn't exist locally, tries `origin/<ref>`, then creates a new branch from HEAD. Stashes and restores uncommitted changes. Runs in parallel. | `extensionRepos: MutableList<WrkxRepo>` (populated by plugin) |
| `ReposTask` | `wrkx-repos` | Generates a catalog of all configured repos at `.opsx/repos.md`. Shows a summary table (enabled, cloned, substitute, ref, substitutions), per-category detail, and a machine-readable config block. | `outputFile: RegularFileProperty`, `extensionRepos: MutableList<WrkxRepo>` (populated by plugin) |

## Workspace Management Model

The wrkx module manages a workspace of Git repositories that can be used as Gradle included builds (composite builds). The flow is:

1. **Declare repos** in `workspace.json` or the Gradle extension. Each entry specifies:
   - `name` -- GitHub repo identifier (e.g., `MyOrg/my-repo`)
   - `enable` -- whether to include this repo
   - `category` -- grouping label (e.g., `internal`, `external`)
   - `substitutions` -- Gradle dependency coordinates this repo substitutes
   - `ref` -- branch or tag to check out (used by `CheckoutTask`)

2. **Clone** (`wrkx-clone`): Downloads missing repos to a base directory. Uses `gh` for authentication.

3. **Sync** (`wrkx-pull`): Updates all repos to latest main with stash/restore safety.

4. **Branch** (`wrkx-checkout`): Switches all repos to a target ref for coordinated multi-repo work.

5. **Catalog** (`wrkx-repos`): Generates a status report showing which repos are enabled, cloned, and configured for dependency substitution.

The `WrkxRepo` data class (from `:lib:core`) holds per-repo configuration including `repoName`, `enabled`, `category`, `ref`, `clonePath`, `substitute`, and `substitutions`. The plugin populates `extensionRepos` on each task from the parsed config.

## Key Classes

- `CloneTask` (`zone.clanker.wrkx.tasks.execution`) -- parallel clone via `gh`
- `PullTask` (`zone.clanker.wrkx.tasks.execution`) -- parallel stash/checkout/pull/restore
- `CheckoutTask` (`zone.clanker.wrkx.tasks.execution`) -- parallel branch checkout with fallback
- `ReposTask` (`zone.clanker.wrkx.tasks.execution`) -- repo catalog generator

All tasks use `@UntrackedTask` since they interact with external filesystem and git state.

## Dependencies

- `:lib:core` -- `WrkxRepo`, `RepoEntry` (JSON config parsing)
- Gradle API (compile-only)
