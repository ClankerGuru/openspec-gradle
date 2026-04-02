Find all usages of a symbol with exact file:line locations.

**Input**: The argument is the symbol name.

```bash
./gradlew srcx-usages -Psymbol=<name>
```

Read `.opsx/usages.md`. Each usage shows **file:line**, **kind** (import, call, type-ref, supertype), and **context** (the line of code).

For multi-module projects: add `-Pmodule=:moduleName` to scope the search.
