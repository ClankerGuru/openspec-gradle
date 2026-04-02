!`./gradlew srcx-usages -Psymbol=$ARGUMENTS 2>/dev/null && cat .opsx/usages.md 2>/dev/null || echo "FAILED: Run manually: ./gradlew srcx-usages -Psymbol=YourSymbol"`

Present each usage: **file:line**, **kind** (import, call, type-ref, supertype), and **context** (the line of code).

For multi-module projects: `./gradlew :moduleName:srcx-usages -Psymbol=Name`.
