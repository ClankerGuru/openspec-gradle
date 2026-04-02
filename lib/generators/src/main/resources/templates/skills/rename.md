!`./gradlew srcx-rename -Pfrom=$(echo $ARGUMENTS | awk '{print $1}') -Pto=$(echo $ARGUMENTS | awk '{print $2}') 2>/dev/null && cat .opsx/rename.md 2>/dev/null || echo "Usage: /srcx-rename OldName NewName"`

## Dry-run Preview

The above shows what **would** change. Review the affected files.

To apply the rename:
```bash
./gradlew srcx-rename -Pfrom=OldName -Pto=NewName -PdryRun=false
```

For multi-module: add `-Pmodule=:moduleName`.
