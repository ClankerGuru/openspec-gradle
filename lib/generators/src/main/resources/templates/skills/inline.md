Inline a function — replace call sites with the implementation body.

1. Find the implementation: `./gradlew srcx-find -Psymbol=functionName`
2. Find all call sites: `./gradlew srcx-usages -Psymbol=functionName`
3. For each call site: replace the call with the body, substitute parameters with actual arguments, adjust variable names to avoid conflicts
4. Delete the original function
5. Verify: `./gradlew srcx-usages -Psymbol=functionName` should show zero results
6. Build to verify: `./gradlew build`

Don't inline functions with side effects called from multiple sites. Don't inline virtual/overridden methods.
