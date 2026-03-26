# Proposal: worktree-workspace

## Summary

Replace full git clones with bare-repo + git-worktree layout for the monolith workspace plugin. Instead of one clone per repo (locked to one branch), each repo gets a bare clone and lightweight worktrees per branch. This lets you have `main` and `feature-x` checked out simultaneously across 60 repos, with no stash/pop dance and shared object stores.

## Motivation

1. **Branch switching is destructive** — `opsx-checkout` stashes, checks out, pops. At 60 repos this is slow, error-prone, and you lose context in every repo.
2. **No parallel branches** — you can't compare `main` vs `feature-x` side by side, or run tests on one while editing the other.
3. **Disk waste** — 60 full clones duplicate object stores. Bare + worktrees share objects.
4. **Manual worktree management doesn't scale** — running `git worktree add/remove/list` across 60 repos by hand is not viable.

## Scope

### In scope
- `mode` field in `monolith.json` — `"worktree"` or `"clone"` (default `"clone"`)
- `opsx-worktree-init` — clone bare repos and create initial worktrees (warns and skips existing regular clones for safety)
- `opsx-worktree-add -Pbranch=<name>` — create worktrees for a branch across all enabled repos
- `opsx-worktree-remove -Pbranch=<name>` — remove worktrees for a branch
- `opsx-worktree-list` — list all worktrees across all repos
- `opsx-worktree-prune` — clean up stale/orphaned worktrees
- Directory layout convention with stable paths for `includeBuild`
- `MonolithRepo.clonePath` resolution aware of worktree layout
- Update `MonolithPlugin` to resolve active worktree path for `includeBuild`

### Out of scope
- Changing existing clone-based workflow (stays as-is)
- Per-repo mode mixing (all repos use same mode)
- Automatic merge/conflict resolution
- IDE-specific worktree integration
