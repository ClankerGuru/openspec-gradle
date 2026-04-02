## Implementation

!`./gradlew srcx-find -Psymbol=$ARGUMENTS 2>/dev/null && cat .opsx/find.md 2>/dev/null || echo "Symbol not found"`

!`./gradlew srcx-usages -Psymbol=$ARGUMENTS 2>/dev/null && cat .opsx/usages.md 2>/dev/null || echo "No usages found"`

Using the definition and call sites above:

1. For each call site: replace the call with the body, substituting parameters with actual arguments
2. Adjust variable names to avoid conflicts at each site
3. Delete the original function
4. Verify: `./gradlew srcx-usages -Psymbol=$ARGUMENTS` should show zero results
5. Build: `./gradlew build`

Do NOT inline functions with side effects called from multiple sites. Do NOT inline virtual/overridden methods.
