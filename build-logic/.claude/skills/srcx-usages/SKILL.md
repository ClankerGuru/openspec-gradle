---
name: srcx-usages
description: "Find all usages of a symbol with file:line locations. Use when asking 'where is X referenced?' or before renaming/removing."
argument-hint: "[symbol-name]"
---

<!-- openspec-gradle:0.33.0 -->

Find all usages of a symbol with exact file:line locations.

## Steps

1. Run: `./gradlew srcx-usages -Psymbol=ClassName`
2. Review `.opsx/usages.md` for the full usage report

## Output Format

Each usage shows:
- **file:line** — exact location
- **kind** — import, call, type-ref, supertype, self-reference
- **context** — the line of code

## Use Cases

- Before rename/move: understand impact
- Dead code detection: symbols with no usages
- API surface analysis: who depends on this?
- For multi-module projects: `-Pmodule=:moduleName`

