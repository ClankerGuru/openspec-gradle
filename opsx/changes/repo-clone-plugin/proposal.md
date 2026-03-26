# Proposal: repo-clone-plugin

## Summary

Add an `opsx-clone` task that can be applied via `init.gradle.kts`. It reads a JSON configuration file (default `~/dev/monolith/monolith.json`) that declares repository entries with their full GitHub paths. If a referenced project doesn't already exist on the local machine, the task clones it using `gh repo clone` to a configurable base directory under `~/`.

## Motivation

When setting up a new machine or onboarding, developers often need to clone many repositories into a consistent directory structure. Currently this is a manual process. A declarative JSON file in `init.d/` combined with a single `./gradlew opsx-clone -PdryRun=false` command makes workspace setup reproducible and fast.

The JSON file also serves a dual purpose: it declares dependency substitutions (replacing a published artifact with a local project) and acts as the source of truth for which repos should exist locally.

## Input Format

The configuration file lives at `~/dev/monolith/monolith.json` and contains an array of entries:

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
  }
]
```

Each entry has four fields:
- **`name`** (string) — the full GitHub repository path (`owner/repo`), passed directly to `gh repo clone`
- **`enable`** (boolean) — whether this entry is active
- **`category`** (string) — a grouping label (e.g., `"internal"`, `"libs"`, `"tools"`)
- **`substitutions`** (array of strings) — each string is `"<artifact-coordinate>,<project-name>"` where the left side is the published artifact and the right side is the local project that declares it

## Scope

**In scope:**
- New `opsx-clone` task registered by `MonolithPlugin` (plugin ID `zone.clanker.monolith`)
- JSON config file reader (default `~/dev/monolith/monolith.json`)
- Clone via `gh repo clone` (GitHub CLI)
- Configurable base directory for clones (default `~/dev/monolith`)
- Skip repos that already exist at the target path
- Only process entries where `enable` is `true`
- Dry-run mode to preview what would be cloned

**Out of scope:**
- Applying the dependency substitutions at build time (future enhancement)
- Pull/fetch for existing repos
- Branch checkout after clone
- Authentication management (relies on user's existing `gh auth` session)
