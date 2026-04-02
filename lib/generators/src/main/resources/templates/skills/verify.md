!`./gradlew srcx-verify 2>/dev/null && cat .opsx/verify.md 2>/dev/null || echo "FAILED: Run manually: ./gradlew srcx-verify"`

Present the verification results above.

- **PASS**: All architecture rules satisfied. Report cleanly.
- **FAIL**: Show each violation with file path, line, and the rule it broke. Suggest fixes.

To verify a specific module: `./gradlew :moduleName:srcx-verify`.
