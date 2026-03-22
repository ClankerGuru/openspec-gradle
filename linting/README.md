# OpenSpec Linting

Gradle plugins + init script for automatic detekt/ktlint enforcement.

## Quick Install (Machine-Wide)

```bash
cp init.d/openspec-linting.init.gradle.kts ~/.gradle/init.d/
```

Now every Kotlin project on your machine gets linting automatically.

## Plugins

| Plugin ID | Description |
|-----------|-------------|
| `zone.clanker.gradle.linting` | Applies both detekt + ktlint |
| `zone.clanker.gradle.detekt` | Applies detekt only |
| `zone.clanker.gradle.ktlint` | Applies ktlint only |

## Usage

```kotlin
plugins {
    id("zone.clanker.gradle.linting")
}
```

Or apply individually:

```kotlin
plugins {
    id("zone.clanker.gradle.detekt")
    id("zone.clanker.gradle.ktlint")
}
```

## Smart Detection

If detekt or ktlint is already applied to the project, the plugin **skips** it:

```
🔍 [OpenSpec] detekt already configured for my-project — skipping
🧹 [OpenSpec] ktlint enabled for my-project
```

## System Properties

Disable at build time:

```bash
# Disable all linting
./gradlew build -Dopenspec.linting.enabled=false

# Disable individually
./gradlew build -Dopenspec.detekt.enabled=false
./gradlew build -Dopenspec.ktlint.enabled=false

# Custom detekt config
./gradlew build -Dopenspec.detekt.config=/path/to/detekt.yml

# Custom ktlint version
./gradlew build -Dopenspec.ktlint.version=1.4.0
```

## Project Properties

Add to `gradle.properties`:

```properties
# Disable for this project
openspec.detekt.enabled=false
openspec.ktlint.enabled=false

# Or both
openspec.linting.enabled=false
```

## Config Files

Projects can customize:

- `config/detekt.yml` — detekt rules (auto-detected)
- `.editorconfig` — ktlint formatting rules

## Integration with Check

Both plugins hook into the `check` task:

```bash
./gradlew check  # Runs detekt + ktlintCheck + tests
```
