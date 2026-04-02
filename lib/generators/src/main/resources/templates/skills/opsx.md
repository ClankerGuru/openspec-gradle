!`./gradlew opsx 2>/dev/null | head -60 || echo "Run ./gradlew opsx to see available tasks"`

## Cached Context

!`cat .opsx/context.md 2>/dev/null | head -20`

!`cat .opsx/status.md 2>/dev/null | head -20 || echo "No active changes"`

Present a concise summary: available task categories, active changes with progress, and included builds. For a specific build: `./gradlew :<build>:opsx`.
