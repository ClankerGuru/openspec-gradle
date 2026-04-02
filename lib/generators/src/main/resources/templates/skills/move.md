!`./gradlew srcx-move -Psymbol=$(echo $ARGUMENTS | awk '{print $1}') -PtargetPackage=$(echo $ARGUMENTS | awk '{print $2}') 2>/dev/null && cat .opsx/move.md 2>/dev/null || echo "Usage: /srcx-move ClassName new.package.name"`

## Dry-run Preview

The above shows the planned file move and import rewrites. Review carefully.

To apply: `./gradlew srcx-move -Psymbol=Name -PtargetPackage=new.pkg -PdryRun=false`

After applying, verify with `./gradlew srcx-usages -Psymbol=Name` to confirm no broken references. Check for wildcard imports (`import old.package.*`) manually.
