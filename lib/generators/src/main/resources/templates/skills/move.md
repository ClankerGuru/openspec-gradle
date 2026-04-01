Move a class or file to a different package safely. Handles package declaration updates and all import rewrites.

## Steps

1. Always preview first: `./gradlew srcx-move -Psymbol=ClassName -PtargetPackage=new.pkg -PdryRun=true`
2. Review the output in `.opsx/move.md` — check file move path and import updates
3. If correct, apply: `./gradlew srcx-move -Psymbol=ClassName -PtargetPackage=new.pkg`
4. Verify with: `./gradlew srcx-usages -Psymbol=ClassName` to confirm no broken references
5. Run the build to catch any compile errors

## Notes

- The task handles: file relocation, package declaration update, import rewrites
- It does NOT handle: wildcard imports that might mask the symbol, re-exports, reflection-based references
- For multi-module projects, add `-Pmodule=:moduleName` to scope the search
- After moving, check for any `import old.package.*` that might need updating manually
