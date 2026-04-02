!`./gradlew srcx-calls -Psymbol=$ARGUMENTS 2>/dev/null && cat .opsx/calls.md 2>/dev/null || echo "FAILED: Run manually: ./gradlew srcx-calls -Psymbol=YourSymbol"`

Present the call graph above. Organize into **callers** (what calls this) and **callees** (what this calls).

If the symbol is not found, run `./gradlew srcx-find -Psymbol=$ARGUMENTS` first to locate it. For multi-module projects: `./gradlew :moduleName:srcx-calls -Psymbol=Name`.
