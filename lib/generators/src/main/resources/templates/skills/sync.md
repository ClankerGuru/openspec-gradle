Run: `./gradlew opsx-sync`

This regenerates skills, instructions, and context files. If `~/.clkx/` exists, creates symlinks instead of per-project files.

For global refresh: `./gradlew opsx-sync -Pglobal=true`

After sync, verify with `ls .claude/skills/` or `ls .opsx/`.
