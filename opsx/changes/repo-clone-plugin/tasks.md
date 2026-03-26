# Tasks: repo-clone-plugin

## Implementation Tasks

- [x] `rcp-1` Create RepoEntry model with kotlinx.serialization in `:core`
  → **Files:** `core/src/main/kotlin/zone/clanker/gradle/core/RepoEntry.kt` (new)
  → **Verify:** `./gradlew :core:compileKotlin`

- [x] `rcp-2` Unit tests for RepoEntry
  → depends: rcp-1
  → **Files:** `core/src/test/kotlin/zone/clanker/gradle/core/RepoEntryTest.kt` (new)
  → **Verify:** `./gradlew :core:test`

- [x] `rcp-3` Create CloneTask in `:tasks`
  → depends: rcp-1
  → **Files:** `tasks/src/main/kotlin/zone/clanker/gradle/tasks/execution/CloneTask.kt` (new)
  → **Verify:** `./gradlew :tasks:compileKotlin`

- [x] `rcp-4` Create MonolithPlugin settings plugin in `:plugin`
  → depends: rcp-3
  → **Files:** `plugin/src/main/kotlin/zone/clanker/gradle/MonolithPlugin.kt` (new), `plugin/build.gradle.kts`
  → New `MonolithPlugin : Plugin<Settings>` that applies to root project via `settings.gradle.rootProject {}`. Registers `opsx-clone` as `CloneTask`. Wire properties from project properties: `zone.clanker.openspec.monolithFile` (default `~/.gradle/init.d/monolith.json`), `zone.clanker.openspec.monolithDir` (default `~/dev/monolith`), `zone.clanker.openspec.githubOrg`, `dryRun`. Register plugin ID `zone.clanker.monolith` in `gradlePlugin {}` block in `build.gradle.kts`.
  → **Verify:** `./gradlew :plugin:compileKotlin`

- [x] `rcp-5` Integration test for MonolithPlugin + CloneTask
  → depends: rcp-4
  → **Files:** `plugin/src/test/kotlin/zone/clanker/gradle/CloneTaskTest.kt` (new)
  → GradleRunner tests using `zone.clanker.monolith` plugin ID: task is registered, dry-run lists repos without cloning, skips existing directories, skips disabled entries, handles missing JSON file gracefully, clones a local bare git repo with `dryRun=false`.
  → **Verify:** `./gradlew :plugin:test`
