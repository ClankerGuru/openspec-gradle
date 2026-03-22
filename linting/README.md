# OpenSpec Linting

Optional Gradle init scripts for automatic detekt + ktlint enforcement across all projects.

## Quick Install

```bash
# Copy to your Gradle home
cp init.d/*.gradle.kts ~/.gradle/init.d/
```

Or for a custom Gradle distribution, place in the distribution's `init.d/` folder.

## What They Do

| Script | Effect |
|--------|--------|
| `detekt.init.gradle.kts` | Applies detekt to all Kotlin projects |
| `ktlint.init.gradle.kts` | Applies ktlint to all Kotlin projects |

Both hook into the `check` task, so `./gradlew check` runs everything.

## Smart Detection

If a project already has detekt or ktlint configured, the init script **skips** it:

```
🔍 [OpenSpec] detekt already configured for my-project — skipping
🧹 [OpenSpec] ktlint enabled for my-project
```

## Opting Out

Per-project, add to `gradle.properties`:

```properties
openspec.detekt.enabled=false
openspec.ktlint.enabled=false
```

## Config Files

Projects can customize by adding:

- `config/detekt.yml` — custom detekt rules
- `.editorconfig` — ktlint formatting rules

No config? Sensible defaults are used.
