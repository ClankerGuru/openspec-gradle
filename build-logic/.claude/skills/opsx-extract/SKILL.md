---
name: opsx-extract
description: "Extract code into a new function or class. Use when the user says 'extract this' or wants to refactor by pulling out code."
---

Extract a block of code into a new function or class.

## Steps

1. Identify the code to extract (file path, start line, end line)
2. Run: `./gradlew opsx-extract -PsourceFile=path/File.kt -PstartLine=10 -PendLine=25 -PnewName=doSomething`
3. Review `.opsx/extract.md` — it shows:
   - The extracted code block
   - Detected free variables (potential parameters)
   - Suggested function signature
   - Call site replacement
4. Adjust the suggestion: fix parameter types, return type, visibility
5. Apply the refactoring manually based on the suggestion
6. Run `./gradlew opsx-usages -Psymbol=doSomething` to verify

## Notes

- The task suggests but does NOT auto-apply (extraction needs human judgment)
- Free variable detection is heuristic — review the parameter list
- Works best with Kotlin; Java support is basic

