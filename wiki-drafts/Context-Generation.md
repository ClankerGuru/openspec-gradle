# Context Generation

The `openspecContext` task extracts structured project information from your Gradle build and writes it into agent instruction files. This page covers what it extracts and why.

## Running it

```bash
./gradlew openspecContext
```

This is also called internally by `openspecSync` — you don't usually need to run it separately.

## What it extracts

### Project metadata

Basic project identity: name, group, version, description, Gradle version, Java/Kotlin target versions.

### Module dependency graph

In multi-module builds, the full graph of inter-module dependencies. Which modules depend on which, using what configurations (`implementation`, `api`, `testImplementation`, etc.).

This gives AI assistants awareness of project structure — they can reason about what code changes might affect which modules.

### Resolved dependency versions

All resolved external dependencies with their actual versions (after conflict resolution and BOM/platform constraints). Not just what's declared — what's actually on the classpath.

This matters because AI assistants frequently suggest wrong dependency versions or miss version conflicts. With resolved versions in context, suggestions are grounded in reality.

### Framework detection

Detects 30+ frameworks and libraries by inspecting both applied plugins and the dependency graph:

- **Android:** Android Application/Library, Jetpack Compose, Material 2/3, Navigation Compose, Hilt, Dagger, Room, DataStore, WorkManager, ViewModel, LiveData
- **Kotlin:** KMP, Compose Multiplatform, Coroutines, Serialization, KSP, KAPT
- **Networking:** Ktor Client/Server, Retrofit, OkHttp
- **Images:** Coil, Glide, Picasso
- **JSON:** Moshi, Gson, Kotlin Serialization
- **DI:** Hilt, Dagger, Koin
- **Persistence:** Room, Exposed, jOOQ, Spring Data JPA/MongoDB/Redis
- **Reactive:** RxJava 2/3, RxAndroid, Spring WebFlux
- **Backend:** Spring Boot, Ktor Server, Flyway, Liquibase, Protobuf/gRPC
- **Quality:** Detekt, Konsist
- **Testing:** Testcontainers
- **Logging:** Timber

Detection is dependency-based, not source-scanning — it's fast and reliable.

Why this matters: when an AI assistant knows you're using Spring Boot 3.x with JUnit 5 and jOOQ, it generates idiomatic code for *your* stack instead of generic examples.

### Architecture and migration hints

Based on detected frameworks and versions, the context engine surfaces:

- Architectural patterns in use (e.g. reactive vs. servlet, multi-module structure)
- Potential migration paths (e.g. JUnit 4 → 5, Java 11 → 17, javax → jakarta)

These are informational — they give AI assistants awareness of modernization opportunities without prescribing action.

### Git info

Current branch, remote URL, dirty/clean state. Helps AI assistants understand the development context (feature branch vs. main, etc.).

### Source sets

Source set layout for each module — where main sources, test sources, and resources live. Especially useful in non-standard layouts or when Kotlin and Java sources coexist.

## Output

Context is written to `.openspec/context.md` — a single Markdown file at the project root. This file is automatically added to `.gitignore`.

AI assistants can reference this file directly, and the generated command/skill templates point to it. The context is tool-agnostic; the tool-specific formatting happens in the command and skill files that `openspecSync` generates.

## Why not just write it manually?

You can. But:

1. **It drifts.** Manual docs go stale the moment someone adds a dependency or module.
2. **It's incomplete.** Nobody manually lists 200 resolved dependencies with versions.
3. **It's per-tool.** Maintaining the same context in 3 different file formats is tedious.
4. **Gradle already knows.** The build model has all of this. Extracting it is cheap and always accurate.

`openspecContext` runs in seconds and produces context that would take hours to write and maintain by hand.
