Remove a symbol or line range from the codebase. Handles import cleanup.

1. Preview first: `./gradlew srcx-remove -Psymbol=ClassName` (dry-run is default)
2. Review `.opsx/remove.md` — check what will be removed and remaining references
3. Apply: `./gradlew srcx-remove -Psymbol=ClassName -PdryRun=false`
4. Fix any remaining references listed in warnings, then build

**Modes**:
- **Remove class:** `-Psymbol=ClassName`
- **Remove member:** `-Psymbol=ClassName.methodName`
- **Remove lines:** `-Pfile=relative/path -PstartLine=N -PendLine=M` (no import cleanup)

For multi-module: add `-Pmodule=:moduleName`. Run `srcx-usages` first for full impact analysis.
