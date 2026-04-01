---
name: opsx-calls
description: "Show the call graph for a symbol — what calls it and what it calls. Use when asking 'what uses this?' or tracing execution flow."
argument-hint: "[symbol-name]"
---

Show the call graph for a symbol — what it calls and what calls it.

---

**Input**: The argument after the command is the symbol name to analyze.

**Steps**

1. Run the calls task:
   ```bash
   ./gradlew opsx-calls -Psymbol=<name>
   ```

2. Read the output at `.opsx/calls.md`

3. Present the call graph to the user.

**Output**

Show the call graph with callers and callees clearly organized.

