!`./gradlew srcx-find -Psymbol=$ARGUMENTS 2>/dev/null && cat .opsx/find.md 2>/dev/null || echo "FAILED: Run manually: ./gradlew srcx-find -Psymbol=YourSymbol"`

Present the results above. Show file paths with line numbers.

If no results, suggest:
- Check spelling and case sensitivity
- Scope to a module: `./gradlew :moduleName:srcx-find -Psymbol=Name`
- Try a partial name or superclass name
