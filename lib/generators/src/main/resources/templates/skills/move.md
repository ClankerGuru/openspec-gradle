Move a class or file to a different package. Handles package declaration updates and import rewrites.

1. Preview: `./gradlew srcx-move -Psymbol=ClassName -PtargetPackage=new.pkg -PdryRun=true`
2. Review `.opsx/move.md` — check file path and import updates
3. Apply: `./gradlew srcx-move -Psymbol=ClassName -PtargetPackage=new.pkg`
4. Verify: `./gradlew srcx-usages -Psymbol=ClassName` to confirm no broken references

Does NOT handle: wildcard imports, re-exports, or reflection-based references. Check for `import old.package.*` manually after moving. For multi-module: add `-Pmodule=:moduleName`.
