!`./gradlew srcx-extract -PsourceFile=$(echo $ARGUMENTS | awk '{print $1}') -PstartLine=$(echo $ARGUMENTS | awk '{print $2}') -PendLine=$(echo $ARGUMENTS | awk '{print $3}') -PnewName=$(echo $ARGUMENTS | awk '{print $4}') 2>/dev/null && cat .opsx/extract.md 2>/dev/null || echo "Usage: /srcx-extract path/File.kt 10 25 newFunctionName"`

## Extraction Preview

The above shows: extracted code, detected free variables, suggested signature, and call site replacement.

The task suggests but does NOT auto-apply. Free variable detection is heuristic -- review the parameter list and adjust types, return type, and visibility as needed.
