## Current Workspace

!`cat .opsx/repos.md 2>/dev/null || echo "No workspace info -- run ./gradlew wrkx-repos"`

## Tasks

| Task | What it does |
|------|-------------|
| `wrkx-repos` | List workspace repos, branches, and dirty status |
| `wrkx-pull` | Pull latest for all repos |
| `wrkx-checkout -Pbranch=name` | Switch branches across all repos |
| `wrkx-clone` | Clone all repos from `workspace.json` |

## Daily Workflow

1. **Start of day**: `./gradlew wrkx-pull`
2. **Check status**: `./gradlew wrkx-repos`
3. **Switch context**: `./gradlew wrkx-checkout -Pbranch=feat/my-feature`

## Included Builds

Never `cd` into sibling repos. Always use `:<build>:<task>` syntax:
```bash
./gradlew :gort:srcx-find -Psymbol=MyClass
./gradlew :catalog:srcx-usages -Psymbol=Button
```

Proposals live at workspace level. Code changes happen inside individual repos via `./gradlew :<repo>:opsx-apply`.
