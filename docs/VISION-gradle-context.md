# Vision: Gradle as the AI's Eyes

## The Problem

AI coding assistants are blind. Every session starts the same way:

```
AI: "Let me explore the project structure..."
    $ find . -name "*.kt" | head -50
    $ cat build.gradle.kts
    $ cat settings.gradle.kts
    "Hmm, looks like there are 3 modules..."
    $ cat core/build.gradle.kts
    $ cat api/build.gradle.kts
    ...
    [5-10 minutes of stumbling around]
    "OK I think I understand the project now"
```

Meanwhile, Gradle resolved ALL of this in milliseconds during configuration. It knows the full graph. It has permission to read everything. It already ran.

**We should give the AI what Gradle already knows.**

---

## The Three Pillars

### 1. Context — "What am I?"

Auto-generated from the Gradle build. Cached. The AI reads one file and knows everything.

#### Identity
```markdown
## Project: my-platform
- Group: com.acme
- Version: 3.1.0
- Repo: git@github.com:acme-corp/my-platform.git
- Branch: main (clean)
- Gradle: 9.4 + Kotlin DSL
- Java: 17 (Temurin)
- Kotlin: 2.1.0
```

#### Module Map
```markdown
## Modules (4)

### :core (com.acme:core:3.1.0)
- Path: core/
- Type: Kotlin JVM library
- Sources: src/main/kotlin/ (47 files), src/test/kotlin/ (23 files)
- Depended on by: :api, :web, :worker

### :api (com.acme:api:3.1.0)
- Path: api/
- Type: Spring Boot application
- Sources: src/main/kotlin/ (82 files), src/test/kotlin/ (41 files)
- Depends on: :core (project), :shared-models (included build)
```

#### Dependency Classification

This is the key insight. Not all dependencies are equal:

```markdown
## Dependencies

### 🏠 Local (included/composite builds — you can modify these)
- :shared-models (included from ../shared-models)
  → Source available at ../shared-models
  → You can navigate into it, modify code, run tests
  → Git: git@github.com:acme-corp/shared-models.git (branch: main)

### 🏢 Organization (com.acme.* — your company, from artifact repo)
- com.acme:auth-sdk:2.4.0 (from https://maven.acme.com)
  → Internal library, source likely at github.com/acme-corp/auth-sdk
  → Can request changes, can't modify directly
  → Used by: :api, :web

### 🌍 External (third-party — read-only)
- org.springframework.boot:spring-boot-starter-web:3.2.4
- org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0
- com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1
```

**Why this matters:** When the AI encounters an issue in `shared-models`, it can fix it directly (it's an included build). When it encounters an issue in `auth-sdk`, it knows to work around it or suggest filing a ticket. When it encounters a Spring issue, it knows to check docs, not source.

#### Framework Detection → Architecture Map
```markdown
## Architecture (inferred)

Detected: Spring Boot + JPA + Security + Flyway + Kotlin

### Patterns in use:
- REST controllers in `controller/` packages
- Service layer in `service/` packages
- JPA entities in `model/` or `entity/` packages
- Repositories in `repository/` packages

### Constraints:
- Schema changes REQUIRE Flyway migrations (db/migration/)
- Security config exists → new endpoints may need auth rules
- Kotlin → use data classes, coroutines where appropriate

### Integration points:
- :core ← shared domain model (modify carefully, affects all modules)
- :api ← REST endpoints (external contract)
- :worker ← async job processing (reads from same DB)
```

#### Errors as Context
```markdown
## ⚠️ Build Issues (current)

### Unresolved dependency
- com.acme:legacy-utils:1.0.0 NOT FOUND in any repository
  → Referenced by :web/build.gradle.kts line 14
  → This will cause compilation failures in :web

### Deprecation warning  
- `kotlin-android-extensions` plugin is deprecated
  → Used in :mobile/build.gradle.kts
  → Migrate to View Binding or Compose
```

---

### 2. Exploration — "What can I learn?"

Everything the AI needs for exploration should be auto-generated, not discovered via bash.

#### `openspecContext` (cached, fast)
Generates the full context file. Re-runs only when build files change.

#### `openspecDeps --module=:api` (focused)
Deep-dive into one module's dependency tree. Useful when working on a specific area.

#### `openspecGraph` (visual)
Outputs a module dependency graph in a format the AI can reason about:
```
:core ◄─── :api ◄─── :web
  ▲                    
  └──── :worker        
  
Included builds:
  ../shared-models ◄── :api, :core
```

#### Future: `openspecEndpoints` (framework-specific)
If Spring detected, scan for `@RequestMapping`, `@GetMapping` etc. and map all HTTP endpoints. If Ktor, scan `routing {}` blocks.

---

### 3. Development Loop — "How do I work here?"

This is the REPL-but-better idea. Tailored to Gradle, specialized for composite builds.

#### The Problem with Composite Builds

In a composite build, when you change code in an included build:
```
my-platform/          ← main build
  └── uses ../shared-models (included build)
  
shared-models/        ← included build
  └── src/main/kotlin/Model.kt  ← AI changes this
```

The AI needs to know:
1. Change in `shared-models` affects `my-platform` (both must recompile)
2. Tests in `my-platform` should run against the local change (not the published artifact)
3. The included build has its own test suite that should pass first

#### `openspecLoop` — The Tight Dev Loop

A task (or guidance in context) that tells the AI the correct build/test cycle:

```markdown
## Development Loop

### For changes in :api
1. ./gradlew :api:compileKotlin (fast feedback — does it compile?)
2. ./gradlew :api:test (unit tests)
3. ./gradlew :api:integrationTest (if exists)
4. ./gradlew check (full project verification)

### For changes in included build ../shared-models
1. cd ../shared-models && ./gradlew test (verify isolated)
2. cd /path/to/my-platform && ./gradlew :api:test (verify integration)
3. ./gradlew check (full project)

### For changes spanning :core and :api
1. ./gradlew :core:test (core first — it's the dependency)
2. ./gradlew :api:test (api uses core)
3. ./gradlew check (everything)
```

**The AI doesn't figure this out.** Gradle generates it from the actual task graph.

#### Composite Build Awareness

The context file explicitly tells the AI about included builds:

```markdown
## Included Builds

### ../shared-models
- **Substitutes:** com.acme:shared-models:2.0.0 → local source
- **Source:** ../shared-models/src/main/kotlin/
- **Tests:** ../shared-models/src/test/kotlin/
- **Git:** git@github.com:acme-corp/shared-models.git
- **Branch:** feature/new-api (2 commits ahead of main)
- **Impact:** Changes here affect :api and :core in this build
- **Loop:** Test here first, then test dependents
```

The AI knows it can navigate there, make changes, and what the ripple effects are.

---

## Task Set (Revised)

### Context & Exploration
| Task | What | Cached? |
|---|---|---|
| `openspecContext` | Full project context dump | ✅ Yes |
| `openspecSync` | Generate AI tool files + context | ✅ Partially |

### Change Workflow  
| Task | What | Cached? |
|---|---|---|
| `openspecNew --name=X` | Scaffold change directory + YAML | No (creates files) |
| `openspecStatus` | List active changes + completion | No (reads current state) |
| `openspecArchive --name=X` | Move to archive with date prefix | No (moves files) |
| `openspecVerify --name=X` | Check task completion in tasks.md | No (reads current state) |

### Utility
| Task | What | Cached? |
|---|---|---|
| `openspecClean` | Remove all generated files | No |
| `openspecInstallGlobal` | Install init script | No |

### Removed
| Old Task | Why |
|---|---|
| `openspecPropose` | AI behavior, not a build task |
| `openspecApply` | AI behavior, not a build task |

---

## Dependency Classification Rules

How we determine if a dependency is local, org, or external:

```kotlin
fun classify(dep: ResolvedDependency, project: Project): DependencyType {
    // 1. Is it an included/composite build?
    val includedBuilds = project.gradle.includedBuilds
    if (includedBuilds.any { it.name == dep.moduleName }) {
        return LOCAL  // Full source access, modifiable
    }
    
    // 2. Is it a project dependency (same multi-project build)?
    if (dep is ProjectDependency) {
        return LOCAL  // Same build, full access
    }
    
    // 3. Same organization? (group prefix match)
    val orgGroup = project.group.toString()
    val orgPrefix = orgGroup.split(".").take(2).joinToString(".")
    if (dep.moduleGroup.startsWith(orgPrefix)) {
        return ORGANIZATION  // Same org, source likely available
    }
    
    // 4. Everything else
    return EXTERNAL  // Third-party, read-only
}
```

Users can customize the org detection via property:
```properties
# ~/.gradle/gradle.properties
zone.clanker.openspec.orgGroups=com.acme,com.acme.internal
```

---

## VCS Integration

Gradle can read git info without the AI running shell commands:

```kotlin
// In the context task
fun getGitInfo(projectDir: File): GitInfo {
    val gitDir = File(projectDir, ".git")
    if (!gitDir.exists()) return GitInfo.NONE
    
    // Read HEAD
    val headRef = File(gitDir, "HEAD").readText().trim()
    val branch = if (headRef.startsWith("ref: ")) {
        headRef.removePrefix("ref: refs/heads/")
    } else "detached"
    
    // Read remote
    val configFile = File(gitDir, "config")
    val remoteUrl = parseGitConfig(configFile)["remote.origin.url"]
    
    // Dirty check
    // ... git status via ProcessBuilder
    
    return GitInfo(branch, remoteUrl, isDirty)
}
```

No `exec("git")` needed for basic info. For status, a quick `git status --porcelain` is fine.

---

## Open Questions

1. **How to handle monorepo vs multi-repo?** Context should work for both, but monorepo context could be huge.
2. **Token budget:** Context file for a 50-module project might be big. Need a summary mode + deep-dive per module?
3. **Refresh frequency:** Context on every `openspecSync`? Or separate explicit step?
4. **Build variant awareness:** Android projects have build variants (debug/release). Include all or just active?
5. **Custom architecture rules:** Should users be able to define their own architecture patterns in a config file?

---

## Implementation Order

1. **`openspecContext` task** — the foundation (sub-agent building this now)
2. **Dependency classification** — local/org/external with included build awareness
3. **VCS integration** — repo URL, branch, dirty status
4. **Development loop generation** — build/test commands per module
5. **Composite build awareness** — included builds, substitutions, impact analysis
6. **Framework-specific analysis** — endpoint scanning, migration detection (Phase 3)
