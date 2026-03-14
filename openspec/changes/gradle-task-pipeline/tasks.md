# Tasks: Gradle Task Pipeline

## New Discovery Tasks

- [ ] `gtp-1` Implement `openspecTree` task → depends: ttd-1
  - [ ] `gtp-1.1` Walk source sets, build ASCII tree per module
  - [ ] `gtp-1.2` `--module` and `--scope` options via `@Option`
  - [ ] `gtp-1.3` Output to `.openspec/tree.md`
  - [ ] `gtp-1.4` `@CacheableTask` with source files as inputs
- [ ] `gtp-2` Implement `openspecDeps` task
  - [ ] `gtp-2.1` Resolve all configurations, extract GAV + version
  - [ ] `gtp-2.2` Classify: local (own modules), org (own group), external
  - [ ] `gtp-2.3` Output to `.openspec/deps.md` as Markdown table
  - [ ] `gtp-2.4` `@CacheableTask` with build files as inputs
- [ ] `gtp-3` Implement `openspecModules` task
  - [ ] `gtp-3.1` Walk multi-project + included builds, build module graph
  - [ ] `gtp-3.2` Show inter-module dependencies
  - [ ] `gtp-3.3` Output to `.openspec/modules.md`
- [ ] `gtp-4` Implement `openspecDevLoop` task
  - [ ] `gtp-4.1` Per-module: build command, test command, run command (from task graph)
  - [ ] `gtp-4.2` Output to `.openspec/devloop.md`
- [ ] `gtp-5` Implement `openspecStatus` task → depends: ttd-5
  - [ ] `gtp-5.1` Dashboard output to `.openspec/status.md`

## Mutation Tasks

- [ ] `gtp-6` Implement `openspecPatch` task
  - [ ] `gtp-6.1` `--file`, `--find`, `--replace` options
  - [ ] `gtp-6.2` Log patch to `.openspec/patches.md`
  - [ ] `gtp-6.3` Dry-run mode (`--dry-run`)
- [ ] `gtp-7` Implement `openspecScaffold` task
  - [ ] `gtp-7.1` Create files from inline or resource templates
  - [ ] `gtp-7.2` `--template`, `--output`, `--vars` options

## Task Description Convention

- [ ] `gtp-11` Define description schema: `[tool]`, `Output:`, `Options:`, `Use when:`, `Chain:`
- [ ] `gtp-12` Rewrite all existing task descriptions to follow the convention → depends: gtp-11
  - [ ] `gtp-12.1` `openspecSync` description
  - [ ] `gtp-12.2` `openspecContext` description
  - [ ] `gtp-12.3` `openspecPropose` description
  - [ ] `gtp-12.4` `openspecApply` description
  - [ ] `gtp-12.5` `openspecArchive` description
  - [ ] `gtp-12.6` `openspecClean` description
  - [ ] `gtp-12.7` `openspecInstallGlobal` description
- [ ] `gtp-13` Apply convention to all new tasks as they're built → depends: gtp-11

## Template Updates

- [ ] `gtp-8` Rewrite all skill templates — remove bash, reference `.openspec/*.md` files
  - [ ] `gtp-8.1` Update `openspec-explore` skill
  - [ ] `gtp-8.2` Update `openspec-propose` skill
  - [ ] `gtp-8.3` Update `openspec-apply-change` skill
  - [ ] `gtp-8.4` Update all command templates (7 files)
- [ ] `gtp-9` Update `openspecSync` to depend on all discovery tasks
  - [ ] `gtp-9.1` Wire `dependsOn` for context, tree, deps, modules, devloop, status
  - [ ] `gtp-9.2` Single `./gradlew openspecSync` refreshes everything

## Tests

- [ ] `gtp-10` Tests
  - [ ] `gtp-10.1` Each discovery task: verify output file content
  - [ ] `gtp-10.2` Cacheability: verify UP-TO-DATE on second run
  - [ ] `gtp-10.3` Scoping: `--module` filters correctly
  - [ ] `gtp-10.4` Patch task: find/replace works, dry-run doesn't modify
  - [ ] `gtp-10.5` Sync depends on all discovery tasks
