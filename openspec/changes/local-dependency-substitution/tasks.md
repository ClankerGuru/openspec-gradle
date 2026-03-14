# Tasks: Local Dependency Substitution

## Registry Parser

- [ ] `lds-1` Create `RepositoryEntry` data class
- [ ] `lds-2` Build `RepositoryRegistryParser` → depends: lds-1
  - [ ] `lds-2.1` Parse Markdown table (pipe-delimited, skip header/separator rows)
  - [ ] `lds-2.2` Handle empty fields (local path, GAV)
  - [ ] `lds-2.3` Render entries back to Markdown table
  - [ ] `lds-2.4` Validate entries (unique keys, valid paths)

## Init Script Generation

- [ ] `lds-3` Build self-contained `repositories.init.gradle.kts` generator → depends: lds-2
  - [ ] `lds-3.1` Inline Markdown table parser (no plugin dependency)
  - [ ] `lds-3.2` Property checks: global `zone.clanker.substitute`, per-repo overrides
  - [ ] `lds-3.3` Project-level opt-out via direct file read of project `gradle.properties`
  - [ ] `lds-3.4` `includeBuild()` calls with validation (dir exists, has settings file)
  - [ ] `lds-3.5` Logging: which repos are being substituted

## Gradle Tasks

- [ ] `lds-4` Implement `openspecRepos` task — print registry status table → depends: lds-2
  - [ ] `lds-4.1` Read and parse `~/.gradle/repositories.md`
  - [ ] `lds-4.2` Check local path existence for each entry
  - [ ] `lds-4.3` Print formatted status table
  - [ ] `lds-4.4` Show global substitution status
- [ ] `lds-5` Integrate into `openspecInstallGlobal` → depends: lds-3
  - [ ] `lds-5.1` Generate and write `repositories.init.gradle.kts` to `~/.gradle/init.d/`
  - [ ] `lds-5.2` Create template `repositories.md` if not present
  - [ ] `lds-5.3` Add `zone.clanker.substitute=false` to `gradle.properties` (default off)

## Auto-discovery

- [ ] `lds-6` Workspace convention: auto-discover repos at `${zone.clanker.workspace}/<key>` → depends: lds-2
  - [ ] `lds-6.1` Read `zone.clanker.workspace` property (default: `~/workspace`)
  - [ ] `lds-6.2` If entry has no local path but `${workspace}/${key}` exists, use it

## Tests

- [ ] `lds-7` Tests → depends: lds-2, lds-3
  - [ ] `lds-7.1` RegistryParser unit tests (parse, render, empty fields, validation)
  - [ ] `lds-7.2` Init script generator output validation
  - [ ] `lds-7.3` Property hierarchy tests (global on, project off)
  - [ ] `lds-7.4` Auto-discovery tests
  - [ ] `lds-7.5` Integration test: `includeBuild` called for enabled local repos
