Extract a block of code into a new function or class.

1. Run: `./gradlew srcx-extract -PsourceFile=path/File.kt -PstartLine=10 -PendLine=25 -PnewName=doSomething`
2. Review `.opsx/extract.md` — shows extracted code, detected free variables, suggested signature, and call site replacement
3. Adjust parameter types, return type, visibility as needed
4. Apply the refactoring manually based on the suggestion

The task suggests but does NOT auto-apply. Free variable detection is heuristic — review the parameter list.
