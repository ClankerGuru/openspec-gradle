!`./gradlew srcx-remove -Psymbol=$ARGUMENTS 2>/dev/null && cat .opsx/remove.md 2>/dev/null || echo "Usage: /srcx-remove ClassName or /srcx-remove ClassName.methodName"`

## Dry-run Preview

The above shows what would be removed and any remaining references.

To apply: `./gradlew srcx-remove -Psymbol=$ARGUMENTS -PdryRun=false`

**Modes**: `-Psymbol=ClassName` (whole class), `-Psymbol=ClassName.methodName` (member), or `-Pfile=path -PstartLine=N -PendLine=M` (line range, no import cleanup).

Run `srcx-usages` first for full impact analysis. Fix remaining references after applying, then build.
