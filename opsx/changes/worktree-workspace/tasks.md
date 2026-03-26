# Tasks: worktree-workspace

## Implementation Tasks

### Phase 1: Data model

- [ ] `ww-1` Add `mode` field to `RepoEntry` (`core/src/main/kotlin/zone/clanker/gradle/core/RepoEntry.kt`)
  - Add `val mode: String = "clone"` to the `@Serializable` data class (after `ref`, with default `"clone"`)
  - Accepts `"clone"` or `"worktree"`
  > verify: symbol-exists RepoEntry.mode

- [ ] `ww-2` Add `mode` and `bareDir` to `MonolithRepo` (`core/src/main/kotlin/zone/clanker/gradle/core/MonolithExtension.kt`)
  - Add constructor param `val mode: String = "clone"`
  - Add `var bareDir: File = File("")` property
  - Add `val isWorktree: Boolean get() = mode == "worktree"` convenience property
  > verify: symbol-exists MonolithRepo.mode, symbol-exists MonolithRepo.bareDir, symbol-exists MonolithRepo.isWorktree
  → depends: ww-1

- [ ] `ww-3` Update `MonolithPlugin` path resolution for worktree mode (`plugin/src/main/kotlin/zone/clanker/gradle/MonolithPlugin.kt`)
  - Pass `entry.mode` to `MonolithRepo` constructor as `mode = entry.mode`
  - Set `repo.clonePath` conditionally: worktree → `File(monolithDir, "${entry.ref}/${entry.directoryName}")`, clone → `File(monolithDir, entry.directoryName)` (existing)
  - Set `repo.bareDir` for worktree mode: `File(monolithDir, ".bare/${entry.directoryName}.git")`
  - Filter `opsx-clone` and `opsx-checkout` to only operate on `mode == "clone"` repos (`opsx-pull` supports both modes — see ww-14)
  > verify: compile
  → depends: ww-2

- [ ] `ww-4` Unit tests for mode field and path resolution
  - Add test in `core/src/test/kotlin` for `RepoEntry` deserialization with `mode` field (present and absent/default)
  - Add test for `MonolithRepo.isWorktree` property
  - Verify `clonePath` and `bareDir` are set correctly in `MonolithPlugin` for both modes via integration test
  > verify: test
  → depends: ww-3

### Phase 2: Worktree init

- [ ] `ww-5` Create `WorktreeInitTask` (`tasks/src/main/kotlin/zone/clanker/gradle/tasks/execution/WorktreeInitTask.kt`)
  - Group: `"opsx"`, description: `"[tool] Initialize bare repos and main worktrees for worktree-mode repos."`
  - Property: `extensionRepos: MutableList<MonolithRepo>` (same pattern as CloneTask)
  - Filter to `repo.isWorktree && repo.enabled`
  - For each repo in parallel:
    1. If `repo.bareDir` doesn't exist: `git clone --bare <repoName> <bareDir>`
    2. Configure bare repo: `git -C <bareDir> config remote.origin.fetch "+refs/heads/*:refs/remotes/origin/*"` (bare clones don't fetch by default)
    3. `git -C <bareDir> fetch origin`
    4. Create main worktree: `git -C <bareDir> worktree add <baseDir>/main/<dirName> main`
  - If existing regular clone at `<baseDir>/<dirName>`: warn and skip (don't auto-convert for safety)
  - Report: INIT/SKIP/FAIL per repo with summary
  - Reuse same `GitResult` / process pattern as other tasks, 5-minute timeout for clone
  > verify: compile
  → depends: ww-3

- [ ] `ww-6` Register `WorktreeInitTask` in `MonolithPlugin` (`plugin/src/main/kotlin/zone/clanker/gradle/MonolithPlugin.kt`)
  - Register as `"opsx-worktree-init"` in `applyToSettings()`
  - Configure with `extensionRepos.addAll(extension.allEntries())`
  > verify: compile
  → depends: ww-5

### Phase 3: Worktree add/remove

- [ ] `ww-7` Create `WorktreeAddTask` (`tasks/src/main/kotlin/zone/clanker/gradle/tasks/execution/WorktreeAddTask.kt`)
  - Group: `"opsx"`, with `-Pbranch=<name>` input property (required)
  - Filter to `repo.isWorktree && repo.enabled && repo.bareDir.exists()`
  - For each repo in parallel:
    1. Fetch: `git -C <bareDir> fetch --all --tags --prune`
    2. Compute target: `<baseDir>/<branch>/<dirName>`
    3. If target exists: SKIP
    4. Try: `git -C <bareDir> worktree add <target> <branch>`
    5. If branch doesn't exist locally or remote: `git -C <bareDir> worktree add -b <branch> <target>` (create from HEAD)
  - Report: OK/CREATED/SKIP/FAIL per repo
  > verify: compile
  → depends: ww-6

- [ ] `ww-8` Create `WorktreeRemoveTask` (`tasks/src/main/kotlin/zone/clanker/gradle/tasks/execution/WorktreeRemoveTask.kt`)
  - Group: `"opsx"`, with `-Pbranch=<name>` input property (required)
  - Refuse to remove `main` branch (safety check, fail with message)
  - Filter to `repo.isWorktree && repo.enabled && repo.bareDir.exists()`
  - For each repo in parallel:
    1. Compute target: `<baseDir>/<branch>/<dirName>`
    2. If target doesn't exist: SKIP
    3. `git -C <bareDir> worktree remove <target>`
  - Clean up empty `<baseDir>/<branch>/` directory after all repos processed
  - Report: REMOVED/SKIP/FAIL per repo
  > verify: compile
  → depends: ww-6

- [ ] `ww-9` Register `WorktreeAddTask` and `WorktreeRemoveTask` in `MonolithPlugin`
  - Register as `"opsx-worktree-add"` and `"opsx-worktree-remove"`
  - Wire `branch` property from project property: `branch.set(project.provider { project.findProperty("branch")?.toString() ?: "" })`
  - Wire `extensionRepos`
  > verify: compile
  → depends: ww-7, ww-8

### Phase 4: Worktree list/prune

- [ ] `ww-10` Create `WorktreeListTask` (`tasks/src/main/kotlin/zone/clanker/gradle/tasks/execution/WorktreeListTask.kt`)
  - Group: `"opsx"`, description: `"[tool] List all worktrees across worktree-mode repos, grouped by branch."`
  - Filter to `repo.isWorktree && repo.enabled && repo.bareDir.exists()`
  - For each repo: `git -C <bareDir> worktree list --porcelain`
  - Parse and display grouped by branch, then by repo
  - Summary: N worktrees across M repos
  > verify: compile
  → depends: ww-6

- [ ] `ww-11` Create `WorktreeCleanTask` (`tasks/src/main/kotlin/zone/clanker/gradle/tasks/execution/WorktreeCleanTask.kt`)
  - Group: `"opsx"`, description: `"[tool] Prune stale worktree entries across worktree-mode repos."`
  - Filter to `repo.isWorktree && repo.enabled && repo.bareDir.exists()`
  - For each repo: `git -C <bareDir> worktree prune`
  - Report pruned entries per repo
  > verify: compile
  → depends: ww-6

- [ ] `ww-12` Register `WorktreeListTask` and `WorktreeCleanTask` in `MonolithPlugin`
  - Register as `"opsx-worktree-list"` and `"opsx-worktree-prune"`
  - Wire `extensionRepos`
  > verify: compile
  → depends: ww-10, ww-11

### Phase 5: Integration and testing

- [ ] `ww-13` Integration tests for worktree tasks (`plugin/src/test/kotlin/zone/clanker/gradle/WorktreeTaskTest.kt`)
  - Test `opsx-worktree-init` with a local bare repo (create a temp git repo, use it as remote)
  - Test `opsx-worktree-add` creates worktree directory
  - Test `opsx-worktree-remove` removes worktree directory, refuses to remove main
  - Test `opsx-worktree-list` output
  - Test `opsx-worktree-prune` after manual directory deletion
  - Test that `opsx-clone` skips worktree-mode repos
  - Test that `opsx-checkout` skips worktree-mode repos
  > verify: test
  → depends: ww-9, ww-12

- [ ] `ww-14` Update `opsx-pull` to support worktree mode (`tasks/src/main/kotlin/zone/clanker/gradle/tasks/execution/PullTask.kt`)
  - For worktree-mode repos: fetch into bare repo, then pull in the active worktree (at `clonePath`)
  - For clone-mode repos: existing behavior unchanged
  - The worktree pull: `git -C <bareDir> fetch --all` then `git -C <clonePath> pull --ff-only`
  > verify: compile
  → depends: ww-6

- [ ] `ww-15` Update README with worktree mode documentation
  - Add `mode` field to JSON config table
  - Document worktree directory layout
  - Document new tasks: `opsx-worktree-init`, `opsx-worktree-add`, `opsx-worktree-remove`, `opsx-worktree-list`, `opsx-worktree-prune`
  - Add usage example: "Working on a feature across 3 repos"
  > verify: file-exists README.md
  → depends: ww-13
