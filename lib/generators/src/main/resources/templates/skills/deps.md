!`cat .opsx/deps.md 2>/dev/null || (./gradlew srcx-deps 2>/dev/null && cat .opsx/deps.md 2>/dev/null) || echo "FAILED: Run manually: ./gradlew srcx-deps"`

Present the dependency tree above. Dependencies are resolved (actual versions after conflict resolution) and grouped by Gradle configuration.

For module relationships, also check `.opsx/modules.md`. For a specific module: `./gradlew :moduleName:srcx-deps`.
