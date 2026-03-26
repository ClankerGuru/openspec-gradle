# Design: worktree-workspace

## Directory Layout

```text
~/dev/monolith/                          ← baseDir
├── .bare/                               ← bare repos live here (hidden)
│   ├── gort.git/                        ← bare clone of ClankerGuru/gort
│   ├── openspec-gradle.git/             ← bare clone of ClankerGuru/openspec-gradle
│   └── ...
├── main/                                ← worktrees for "main" branch
│   ├── gort/                            ← git worktree → .bare/gort.git
│   ├── openspec-gradle/                 ← git worktree → .bare/openspec-gradle.git
│   └── ...
├── feature-x/                           ← worktrees for "feature-x" branch
│   ├── gort/
│   ├── openspec-gradle/
│   └── ...
└── monolith.json                        ← config file (unchanged location)
```

**Key insight:** The branch name becomes a directory level. `includeBuild` points to `~/dev/monolith/<active-branch>/<repo-name>` — the "active branch" is determined by the repo's `ref` field.

### Clone mode layout (unchanged)
```text
~/dev/monolith/
├── gort/                                ← regular clone
├── openspec-gradle/                     ← regular clone
└── monolith.json
```

## Data Model Changes

### `monolith.json` — new `mode` field

```json
[
  {
    "name": "ClankerGuru/gort",
    "enable": true,
    "category": "tools",
    "substitutions": [],
    "mode": "worktree",
    "ref": "main"
  }
]
```

- `mode`: `"worktree"` | `"clone"` — defaults to `"clone"` for backward compat
- When `mode` is `"worktree"`, `clonePath` resolves to `<baseDir>/<ref>/<directoryName>` instead of `<baseDir>/<directoryName>`

### `RepoEntry` — add `mode` field

```kotlin
@Serializable
data class RepoEntry(
    // ... existing fields ...
    val mode: String = "clone"  // "clone" or "worktree"
)
```

### `MonolithRepo` — add `mode` and path resolution

```kotlin
open class MonolithRepo(
    // ... existing params ...
    val mode: String = "clone"
) {
    // clonePath changes meaning:
    // - clone mode: ~/dev/monolith/<repo-name>
    // - worktree mode: ~/dev/monolith/<ref>/<repo-name>
    // bareDir (worktree only): ~/dev/monolith/.bare/<repo-name>.git
    var bareDir: File = File("")
}
```

## Task Design

### `opsx-worktree-init`

For each enabled repo with `mode: "worktree"`:
1. If bare repo doesn't exist at `.bare/<name>.git`: `git clone --bare <url> .bare/<name>.git`
2. If regular clone exists at `<baseDir>/<name>`: offer to convert (move `.git` to bare, set up as worktree)
3. Create initial worktree for `main`: `git worktree add <baseDir>/main/<name> main`
4. Update `clonePath` to point to the worktree

Runs in parallel (same thread pool pattern as CloneTask).

### `opsx-worktree-add -Pbranch=<name>`

For each enabled worktree-mode repo:
1. `git -C .bare/<repo>.git fetch --all --tags --prune`
2. `git -C .bare/<repo>.git worktree add <baseDir>/<branch>/<repo> <branch>`
   - If branch exists on remote: tracks `origin/<branch>`
   - If branch doesn't exist anywhere: creates from HEAD
3. Report: OK/CREATED/FAIL per repo

### `opsx-worktree-remove -Pbranch=<name>`

For each enabled worktree-mode repo:
1. `git -C .bare/<repo>.git worktree remove <baseDir>/<branch>/<repo>`
2. Clean up empty branch directory
3. Refuse to remove `main` worktree (safety)

### `opsx-worktree-list`

For each enabled worktree-mode repo:
1. `git -C .bare/<repo>.git worktree list`
2. Aggregate and display per branch

### `opsx-worktree-prune`

For each enabled worktree-mode repo:
1. `git -C .bare/<repo>.git worktree prune`
2. Report cleaned entries

## Plugin Integration

### `MonolithPlugin.apply()` — path resolution

```kotlin
// In the loop where repos are created:
if (entry.mode == "worktree") {
    repo.clonePath = File(monolithDir, "${entry.ref}/${entry.directoryName}")
    repo.bareDir = File(monolithDir, ".bare/${entry.directoryName}.git")
} else {
    repo.clonePath = File(monolithDir, entry.directoryName)
}
```

`includeBuild(repo.clonePath)` works unchanged — it just points to the worktree directory instead of a clone directory. The worktree is a fully functional working directory.

### Existing tasks compatibility

- `opsx-clone`: Only operates on `mode: "clone"` repos. Worktree repos are handled by `opsx-worktree-init`.
- `opsx-checkout`: Only operates on `mode: "clone"` repos. Worktree repos use `opsx-worktree-add`.
- `opsx-pull`: Works on both modes — for worktree repos, fetches into bare and pulls in the active worktree.

## Key Decisions

1. **Branch-as-directory** — `<baseDir>/<branch>/<repo>` gives stable, predictable paths. Each branch is a "workspace" you can open in IDE without affecting other branches.

2. **`.bare/` hidden directory** — bare repos are implementation detail. Users interact with worktree directories.

3. **Per-repo mode, but enforced globally for now** — the field is per-repo in JSON for future flexibility, but the proposal scope enforces all repos use the same mode. This avoids mixed layouts.

4. **`ref` field drives the active worktree** — `MonolithRepo.ref` determines which worktree directory is used for `includeBuild`. Changing `ref` in JSON (or DSL) and re-syncing Gradle points composite builds to a different branch's worktree.

5. **Backward compatible** — `mode` defaults to `"clone"`. Existing users see zero behavior change.

## Risks

1. **Worktree limitations** — a branch can only be checked out in one worktree at a time. Not a problem in practice (you wouldn't want `main` in two places), but the tasks must handle the error gracefully.

2. **IDE confusion** — some IDEs may not handle worktrees well (e.g., scanning `.bare` for VCS roots). Mitigated by using `.bare/` (dot-prefix hides it in most file browsers).

3. **Migration from clone to worktree** — users with existing clones need a conversion path. `worktree-init` handles this, but it modifies the `.git` directory which is sensitive.

4. **Disk paths in Gradle cache** — Gradle caches by absolute path. Switching which worktree `clonePath` points to invalidates caches. This is the same cost as the current `opsx-checkout` approach.
