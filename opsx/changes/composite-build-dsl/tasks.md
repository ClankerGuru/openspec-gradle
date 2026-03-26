# Tasks: composite-build-dsl

## Implementation Tasks

### Phase 0: Validate assumption — does flat substitution propagate?

- [x] `cbd-1` Spike test: transitive substitution with flat `includeBuild` (`plugin/src/test/kotlin/zone/clanker/gradle/TransitiveSubstitutionTest.kt`)
  - Create 3 temp projects: `host-app`, `lib-a`, `lib-b`
  - `lib-b` publishes `com.test:lib-b` with no dependencies
  - `lib-a` publishes `com.test:lib-a` and depends on `com.test:lib-b:1.0`
  - `host-app` applies monolith plugin, includes both `lib-a` and `lib-b` flat via `includeBuild`
  - `lib-b`'s `includeBuild` declares `substitute(module("com.test:lib-b")).using(project(":lib-b"))`
  - Verify: when `lib-a` resolves `com.test:lib-b`, it gets the local project, not the Maven artifact
  - **Use project names with spaces**: `lib-a` directory = `"Lib A Project"`, `lib-b` directory = `"Lib B Core"`
  - This test determines whether we need init-script injection or flat is enough
  > verify: test

### Phase 1: Build name sanitization

- [ ] `cbd-2` Add `sanitizedBuildName` to `MonolithRepo` (`core/src/main/kotlin/zone/clanker/gradle/core/MonolithExtension.kt`)
  - Add computed property: replaces spaces/special chars/underscores with hyphens, lowercases, collapses hyphens, trims
  - `"My Cool Project"` → `"my-cool-project"`, `"foo (v2)"` → `"foo-v2"`, `"foo__bar"` → `"foo-bar"`
  - Add `require(sanitized.isNotBlank())` validation — fail fast with descriptive error if input like `"---"` sanitizes to empty
  > verify: symbol-exists MonolithRepo.sanitizedBuildName
  → depends: cbd-1

- [ ] `cbd-3` Unit tests for `sanitizedBuildName` (`core/src/test/kotlin`)
  - Test spaces, special chars, underscores, uppercase, consecutive hyphens, leading/trailing hyphens
  - Test that normal names pass through unchanged
  - Test that all-symbol inputs (e.g., `"---"`, `"___"`) throw with descriptive error
  > verify: test
  → depends: cbd-2

- [ ] `cbd-4` Apply `sanitizedBuildName` in `MonolithPlugin.includeAction` and fix accessor names (`plugin/src/main/kotlin/zone/clanker/gradle/MonolithPlugin.kt`)
  - Set `name = repo.sanitizedBuildName` inside `settings.includeBuild(repo.clonePath) { ... }`
  - Update `MonolithExtension.toCamelCase()` (or add `toSafeCamelCase()`) to handle spaces in addition to hyphens, so repos like `"Core Models Lib"` produce accessor `coreModelsLib` instead of failing
  - Use the updated sanitization when computing `propertyName` in `MonolithPlugin.apply()`
  - Existing `includeEnabled()` path benefits automatically
  > verify: compile
  → depends: cbd-2

- [ ] `cbd-5` Integration test: `includeBuild` with space-in-name project (`plugin/src/test/kotlin/zone/clanker/gradle/BuildNameSanitizationTest.kt`)
  - Create temp project with directory name `"My Library Project"`
  - Include via monolith plugin
  - Verify `settings.includeBuild` is called with `name = "my-library-project"`
  - Verify dependency accessors work (no compilation error from spaces)
  > verify: test
  → depends: cbd-4

### Phase 2: Duplicate build name validation

- [ ] `cbd-6` Add duplicate name detection to `MonolithExtension` (`core/src/main/kotlin/zone/clanker/gradle/core/MonolithExtension.kt`)
  - In `includeEnabled()`, after collecting repos, check for duplicate `sanitizedBuildName` values
  - Throw `GradleException` with clear message listing the colliding repos and their sanitized names
  > verify: compile
  → depends: cbd-4

- [ ] `cbd-7` Test duplicate name detection (`plugin/src/test/kotlin/zone/clanker/gradle/`)
  - Create two repos whose directory names sanitize to the same build name (e.g., `"my-lib"` and `"My Lib"`)
  - Verify `includeEnabled()` throws with helpful error
  > verify: test
  → depends: cbd-6

### Phase 3: Tree DSL — `includeBuild()` on `MonolithRepo`

- [ ] `cbd-8` Add `includeBuild()` method and `includedBuilds` to `MonolithRepo` (`core/src/main/kotlin/zone/clanker/gradle/core/MonolithExtension.kt`)
  - Add `private val _includes = mutableListOf<MonolithRepo>()`
  - Add `fun includeBuild(vararg repos: MonolithRepo): MonolithRepo` — appends to `_includes`, returns `this`
  - Add `val includedBuilds: List<MonolithRepo> get() = _includes.toList()`
  > verify: symbol-exists MonolithRepo.includeBuild, symbol-exists MonolithRepo.includedBuilds

- [ ] `cbd-9` Unit tests for `includeBuild()` chaining (`core/src/test/kotlin`)
  - Test single level: `a.includeBuild(b, c)` → `a.includedBuilds == [b, c]`
  - Test nested: `a.includeBuild(b.includeBuild(c))` → `a.includedBuilds == [b]`, `b.includedBuilds == [c]`
  - Test dedup: same repo added twice → appears twice in list (dedup is caller's job)
  > verify: test
  → depends: cbd-8

- [ ] `cbd-10` Add `includeTree()` to `MonolithExtension` (`core/src/main/kotlin/zone/clanker/gradle/core/MonolithExtension.kt`)
  - Collects all unique repos from a root's `includedBuilds` tree (depth-first, deduplicated)
  - Validates no duplicate `sanitizedBuildName` across collected repos
  - Calls `includeAction` for each unique repo
  - Public API: `fun includeTree(root: MonolithRepo)` — called from DSL as `monolith { includeTree(fooApp) }`
  > verify: symbol-exists MonolithExtension.includeTree
  → depends: cbd-8, cbd-6

- [ ] `cbd-11` Wire `includeTree()` in `MonolithPlugin` (`plugin/src/main/kotlin/zone/clanker/gradle/MonolithPlugin.kt`)
  - No changes to `includeAction` lambda needed — it already calls `settings.includeBuild`
  - `includeTree` just calls `includeAction` for each collected repo
  - Ensure `includeAction` uses `sanitizedBuildName`
  > verify: compile
  → depends: cbd-10, cbd-4

### Phase 4: Integration tests

- [ ] `cbd-12` Integration test: tree DSL with transitive substitution (`plugin/src/test/kotlin/zone/clanker/gradle/TreeDslTest.kt`)
  - 3 projects: `host-app`, `core-lib`, `feature-lib` (feature depends on core)
  - `monolith.json` with all 3 repos, substitutions declared
  - DSL: `monolith { hostApp.includeBuild(coreLib, featureLib.includeBuild(coreLib)) }`
  - Verify all 3 are included as composite builds
  - Verify `feature-lib` resolves `core-lib` dependency to local project
  > verify: test
  → depends: cbd-11

- [ ] `cbd-13` Integration test: tree DSL with duplicate `:app` subprojects (`plugin/src/test/kotlin/zone/clanker/gradle/TreeDslTest.kt`)
  - 2 projects both containing an `:app` subproject
  - Verify they get unique build names and don't conflict
  - Verify substitutions target the correct `:app` in each build
  > verify: test
  → depends: cbd-12

- [ ] `cbd-14` Integration test: `includeEnabled()` still works (backward compat) (`plugin/src/test/kotlin/zone/clanker/gradle/TreeDslTest.kt`)
  - Use `includeEnabled()` with sanitized names
  - Verify existing flat behavior is unchanged
  - Verify names are sanitized even in flat mode
  > verify: test
  → depends: cbd-11

### Phase 5: Documentation

- [ ] `cbd-15` Update README with tree DSL docs
  - Document `includeBuild()` DSL syntax with examples
  - Document `includeTree()` call
  - Document name sanitization behavior
  - Add "Working with transitive dependencies" section
  - Add troubleshooting for duplicate build names
  > verify: file-exists README.md
  → depends: cbd-13, cbd-14
