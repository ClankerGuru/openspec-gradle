Remove a symbol or line range from the codebase safely. Handles import cleanup across all files.

## Steps

1. Always preview first: `./gradlew srcx-remove -Psymbol=ClassName` (dry-run is default)
2. Review the output in `.opsx/remove.md` — check what will be removed and remaining references
3. If correct, apply: `./gradlew srcx-remove -Psymbol=ClassName -PdryRun=false`
4. Fix any remaining references listed in the warnings
5. Run the build to catch any compile errors

## Modes

- **Remove class:** `-Psymbol=ClassName` — removes the entire class declaration and cleans imports
- **Remove member:** `-Psymbol=ClassName.methodName` — removes a specific function/property from a class
- **Remove lines:** `-Pfile=relative/path -PstartLine=N -PendLine=M` — removes a line range (no import cleanup)

## Notes

- Dry-run is the default — you must pass `-PdryRun=false` to actually modify files
- The task warns about remaining references that will break after removal
- For multi-module projects, add `-Pmodule=:moduleName` to scope the search
- Run `srcx-usages` first for a complete impact analysis before removing
