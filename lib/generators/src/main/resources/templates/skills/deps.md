Query project dependencies — resolved versions, transitive deps, and module relationships.

```bash
./gradlew srcx-deps
```

Read `.opsx/deps.md` for the full dependency tree. Dependencies are resolved (actual versions after conflict resolution) and grouped by Gradle configuration. For multi-module projects, each module's deps are shown separately.

For module relationships, also check `.opsx/modules.md`.
