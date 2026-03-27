# Proposal: Location-based repo schema

## Summary

Replace the `name` field in `monolith.json` with a `location` field that accepts any source: GitHub shorthand, git SSH/HTTPS URLs, or local filesystem paths. Add an optional `alias` field to let users control the DSL property name.

While `RepoEntry.directoryName` can technically parse non-GitHub names, the actual clone step is hard-coded to use `gh repo clone`, and the `Org/Repo` convention is baked into the expected format. The `name` field does triple duty — clone source, directory name, and DSL identifier — which creates coupling that breaks when any of those need to differ.

## Motivation

1. **Clone tooling lock-in**: While the schema can represent non-GitHub names, the clone execution (`gh repo clone`) only works with GitHub. Teams using GitLab, Bitbucket, or self-hosted git have no way to clone repos through the monolith plugin.

2. **Local-only repos**: Some projects are just directories on disk that will never be pushed to a remote. There's no way to include them today.

3. **Naming collisions**: Two orgs with the same repo name (e.g. `AliceOrg/core-lib` and `BobOrg/core-lib`) produce the same DSL property `coreLib`, causing a crash at registration time. There's no way to disambiguate.

4. **Name overloading**: `name` currently drives clone behavior, directory naming, and DSL property generation. These concerns should be separable.

5. **Simplicity**: Users shouldn't need to understand GitHub URL conventions to point at a directory on their machine.

## Scope

### In scope

- Replace `name` with `location` in `monolith.json`
- Auto-detect location type: GitHub shorthand, git URL, or local path
- Add optional `alias` field for DSL property name override
- Derive DSL property name from last segment of `location` when no alias
- Update `RepoEntry` parsing and `MonolithPlugin` to handle all location types
- Update clone task to support git URL cloning (not just `gh`)
- Skip clone for local paths (just validate directory exists)

### Out of scope

- Remote registry / package manager integration
- Auto-discovery of repos on disk
- Monorepo support (multiple projects within a single location)
- Migration tooling for existing `monolith.json` files (manual migration is fine)

## Examples

```json
// GitHub shorthand (most common case, same ergonomics as today)
{ "location": "ClankerGuru/gort" }
// → DSL: gort
// → clone: gh repo clone ClankerGuru/gort ~/dev/clanker/gort

// Git SSH URL
{ "location": "git@gitlab.internal:team/core-lib.git" }
// → DSL: coreLib
// → clone: git clone git@gitlab.internal:team/core-lib.git ~/dev/clanker/core-lib

// Git HTTPS URL
{ "location": "https://gitlab.internal/team/core-lib.git" }
// → DSL: coreLib
// → clone: git clone https://gitlab.internal/team/core-lib.git ~/dev/clanker/core-lib

// Local absolute path (no clone, used directly)
{ "location": "/opt/libs/shared-utils" }
// → DSL: sharedUtils
// → path: /opt/libs/shared-utils

// Local relative path (resolved against monolithDir)
{ "location": "../my-lib" }
// → DSL: myLib
// → path: ~/dev/clanker/../my-lib → ~/dev/my-lib

// Alias to avoid collision or pick a better name
{ "location": "ClankerGuru/core-lib", "alias": "clankerCore" }
{ "location": "SomeOrg/core-lib", "alias": "orgCore" }
// → DSL: clankerCore, orgCore

// Full entry with all fields
{
  "location": "ClankerGuru/feature-lib",
  "alias": "features",
  "enable": true,
  "substitute": true,
  "category": "libs",
  "substitutions": ["zone.clanker:feature-lib,:"],
  "ref": "develop"
}
// → DSL: features
```

## Location type detection

Detection follows a strict precedence order to avoid misclassification:

| Priority | Pattern | Type | Example |
|----------|---------|------|---------|
| 1 | Starts with `git@` | Git SSH | `git@gitlab.internal:team/lib.git` |
| 2 | Contains `://` | Git URL | `https://github.com/Org/Repo.git` |
| 3 | Starts with `/` or `~` | Absolute local path | `/opt/libs/foo` |
| 4 | Starts with `./` or `../` | Relative local path | `../my-lib` |
| 5 | Matches `^[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+$` | GitHub shorthand | `ClankerGuru/gort` |
| 6 | Everything else | Relative local path | `my-lib` |

The GitHub shorthand rule (priority 5) uses a strict regex: exactly two segments of alphanumeric characters, hyphens, underscores, or dots, separated by a single `/`. This prevents local paths like `foo/bar/baz` or `./foo/bar` from being misclassified.

## DSL property name derivation

1. If `alias` is present → use it **verbatim** (no transformation). Must be a valid Kotlin identifier: `^[a-zA-Z_][a-zA-Z0-9_]*$`. Registration fails with a clear error if the alias is invalid or collides with another entry.
2. If `alias` is absent → take last path segment of `location`, strip `.git` suffix, camelCase it (e.g. `core-lib` → `coreLib`). If the derived name collides with an existing entry, registration fails with an error suggesting the user add an `alias` field.

| Location | Alias | DSL property |
|----------|-------|-------------|
| `ClankerGuru/gort` | — | `gort` |
| `git@gitlab:team/core-lib.git` | — | `coreLib` |
| `/opt/libs/shared-utils` | — | `sharedUtils` |
| `ClankerGuru/core-lib` | `clankerCore` | `clankerCore` |
