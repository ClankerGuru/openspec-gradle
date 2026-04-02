Show available OPSX tasks, active changes, and included builds for this project.

**Input**: Optional build name to scope to (e.g., `gort`, `openspec-gradle`).

1. Run: `./gradlew opsx`
2. If a build name was given, also run: `./gradlew :<build>:opsx`
3. Read cached context if available: `.opsx/context.md`, `.opsx/modules.md`, `.opsx/status.md`

Present a concise summary:
- Available tool categories with key tasks
- Active changes with progress
- Included builds with their access pattern
