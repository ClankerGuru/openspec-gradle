Query project dependencies — resolved versions, transitive deps, and module relationships.

## Steps

1. Run: `./gradlew opsx-deps` for the full dependency report
2. Check `.opsx/deps.md` for auto-generated dependency tree

## Use Cases

- "What version of library X are we using?" — check deps.md
- "What depends on module Y?" — check `.opsx/modules.md` for module graph
- "Are there dependency conflicts?" — look for version annotations in the report
- Upgrading a library: check deps.md to understand the dependency chain

## Notes

- Dependencies are resolved (actual versions after conflict resolution)
- Grouped by Gradle configuration (implementation, api, testImplementation, etc.)
- For multi-module projects, each module's deps are shown separately
