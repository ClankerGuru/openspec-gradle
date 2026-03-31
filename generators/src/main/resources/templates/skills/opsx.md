Show available OPSX tasks, active changes, and included builds for this project.

---

**Input**: No arguments required. Optional: a build name to scope to (e.g., `gort`, `openspec-gradle`).

**Steps**

1. Run the OPSX catalog:
   ```bash
   ./gradlew opsx
   ```

2. Read and present the output, grouped by category:
   - **Tools** — discovery, intelligence, refactoring, workflow tasks
   - **Active changes** — tasks with `[task]` markers showing progress (⬜/🔄/✅)
   - **Included builds** — composite builds accessible via `./gradlew :<build>:<task>`

3. If the user specified a build name, also run:
   ```bash
   ./gradlew :<build>:opsx
   ```

4. Read cached context if available:
   - `.opsx/context.md` — project metadata and included builds
   - `.opsx/modules.md` — module graph
   - `.opsx/status.md` — change status

**Output**

Present a concise summary:
- Available tool categories with key tasks
- Active changes with progress (e.g., "gort-v2: 33/33 tasks done")
- Included builds with their access pattern

**When to use this**

- At the start of a conversation to orient yourself
- When switching context to a different build
- When unsure what OPSX tasks are available
- Before reaching for raw tools (Grep, Read, Bash) — check here first
