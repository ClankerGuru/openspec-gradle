Daily workspace management workflow using wrkx tasks.

## Tasks

| Task | What it does |
|------|-------------|
| `wrkx-repos` | List workspace repos, branches, and dirty status |
| `wrkx-pull` | Pull latest for all repos in the workspace |
| `wrkx-checkout -Pbranch=name` | Switch branches across all workspace repos |
| `wrkx-clone` | Clone all repos defined in `workspace.json` |

## Daily Workflow

1. **Start of day**: `./gradlew wrkx-pull` to sync everything
2. **Check status**: `./gradlew wrkx-repos` to see branches and uncommitted changes
3. **Switch context**: `./gradlew wrkx-checkout -Pbranch=feat/my-feature`
4. **New workspace**: `./gradlew wrkx-clone` after editing `workspace.json`

## Working with Included Builds

Repos in the workspace are Gradle included builds. Run tasks across builds from the root:

```bash
./gradlew :gort:srcx-find -Psymbol=MyClass
./gradlew :catalog:srcx-usages -Psymbol=Button
./gradlew :shared:opsx-tree
```

Never `cd` into sibling repos. Always use `:<build>:<task>` syntax from root.

## Proposals and Repos

Proposals (`opsx/changes/`) live at the workspace level. Code changes happen inside individual repos. A single proposal can span multiple repos — tasks reference which repo each change targets.

```bash
./gradlew opsx-status          # see all proposals
./gradlew :gort:opsx-apply     # implement tasks in gort repo
```
