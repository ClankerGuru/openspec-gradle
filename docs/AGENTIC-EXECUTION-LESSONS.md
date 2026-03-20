# Agentic Execution: Real-World Lessons from OpenSpec

> Lessons learned from building and running a Gradle-based agentic execution framework.
> These apply to anyone building AI agent orchestration — whether on Gradle, Make, Bazel, or custom tooling.

---

## 1. The Context Buffet Problem

**What happened:** We gave an agent a task ("add Navigation 3 dependency") and pointed it to the full proposal document for context. The proposal described a 7-phase overhaul. The agent read the entire vision and decided to implement all 7 phases in one go — touching 49 files when the task only required 3-4.

**Root cause:** Agents are eager. If you show them a roadmap, they'll try to drive the whole route. They don't distinguish between "context for understanding" and "scope of work." A proposal that says "Phase 4: seed-based color generation" reads to an agent as "TODO: implement seed-based color generation."

**The fix:**
- **Never reference the full proposal in task prompts.** Reference only `tasks.md` (the scoped task list) and any design docs specific to the current task.
- **Generate per-task context files** that extract only the relevant section of a proposal. The agent gets a narrow briefing, not the master plan.
- **Add explicit scope constraints** in the prompt: "Only implement this specific task. Do not make changes beyond what is described."
- **The task description IS the scope.** Keep it precise. "Add Nav 3 dependency to catalog/build.gradle.kts and replace CatalogSection enum with sealed class routes" is better than "Replace navigation with Nav 3."

**Principle:** _Agents should see what they need to do, not what the project wants to become._

---

## 2. The Prompt Size Problem

**What happened (first attempt):** We embedded the entire `proposal.md` content (9KB) directly into the CLI prompt argument. The agent CLI received a shell command with a 9KB string argument.

**Why it's bad:**
- Shell argument limits (ARG_MAX) can truncate long arguments silently
- The agent spends tokens re-reading context it already has on disk
- CLI tools may have their own prompt size limits
- Logging becomes unreadable (9KB prompt in process listings)

**The fix:**
- **Reference files, don't embed content.** The prompt says "Read `opsx/changes/name/proposal.md` for context" instead of containing the file contents.
- **Agents can read files.** That's literally their job. Copilot, Claude Code, Codex — they all have file-reading capabilities. Let them use those capabilities.
- **Project context is already on disk.** `opsx-sync` generates `.opsx/context.md`, `.opsx/arch.md`, `.opsx/tree.md` before every execution. The agent has structured project understanding without us duplicating it into the prompt.

**Principle:** _The prompt is a pointer, not a payload. Agents read files — let them._

---

## 3. The Timeout Problem

**What happened:** Default timeout was 300 seconds (5 minutes). Copilot in autopilot mode on a real KMP project with 90+ source files needed more time. It made all its file changes within 3 minutes but was still generating its summary response when the timeout hit. `process.destroyForcibly()` sent SIGKILL, and the output was lost.

**The nuance:** Agent execution has two phases:
1. **Tool use** — reading files, making changes, running commands (fast, bounded by disk I/O)
2. **Response generation** — the LLM generating its summary/explanation (slow, bounded by API throughput)

Phase 1 completed fine. Phase 2 is what timed out. The work was done, but the receipt was lost.

**The fix:**
- **Default timeout should be generous** — 600s minimum for real projects. Complex tasks on large codebases can easily take 10+ minutes.
- **Make timeout configurable per-task** via metadata: `timeout:900` in tasks.md
- **Consider the timeout a safety net, not a performance target.** If a task finishes in 30 seconds, great. If it needs 8 minutes, that should be fine too.
- **Agent output is secondary.** The real output is the file changes on disk. If the agent times out during response generation, the work may still be usable. Check `git diff` before discarding.

**Principle:** _Timeout protects against runaway processes, not slow work. Set it high, check the work even on timeout._

---

## 4. The Scope Creep Problem

**What happened:** Task `co-1` was "Add Navigation 3 and replace enum navigation." Copilot also rewrote the chat interface, modified theme files, updated components, changed typography — because it read the proposal and wanted to help with everything.

**Why agents do this:**
- They're trained to be maximally helpful
- They see related work in context files and assume it's in scope
- They can't distinguish between "this is context" and "this is a TODO"
- Some agents (especially Copilot in `--allow-all` mode) will proactively "fix" things they notice

**The fix:**
- **Explicit scope boundary in every prompt:** "IMPORTANT: Only implement this specific task. Do not make changes beyond what is described above."
- **Narrow the context window:** Don't reference documents that describe out-of-scope work
- **Verification should catch scope creep:** A verify step that checks `git diff --stat` against expected file patterns could flag when an agent touched files it shouldn't have
- **Consider a file allowlist per task:** "This task should only modify: `catalog/build.gradle.kts`, `gradle/libs.versions.toml`, `catalog/src/**/CatalogApp.kt`"

**Principle:** _Agents will do everything they can see. Show them only what's in scope._

---

## 5. The Dependency Graph Is Not Just for Ordering

**What happened:** We designed the dependency graph for execution ordering — "run T1 before T2." But in practice, the graph serves three purposes:

1. **Execution ordering** — don't start T2 until T1 is done
2. **Failure isolation** — if T1 fails, don't run T2 (it depends on T1's changes)
3. **Scope definition** — T1's completion defines the starting state for T2

The third one is subtle. When T1 adds Navigation 3 and T2 wires the bottom nav, T2 expects Nav 3 to already be set up. If T1 is only partially complete (some files changed, some not), T2 will work against an inconsistent state.

**The fix:**
- **Verify between every task**, not just at the end. `./gradlew build` must pass after each task before the next one starts.
- **Fresh `opsx-sync` between tasks** so the next agent sees the codebase as it actually is, not as it was planned to be.
- **If verify fails, the task is not done** — regardless of what the agent claims.

**Principle:** _The build is the only source of truth. If it doesn't compile, the task isn't done._

---

## 6. The Token Budget Reality

**Real-world constraint:** In corporate environments, AI coding agents have hourly or daily token limits. An agent that burns through 100K tokens per task attempt will exhaust the budget after 3-4 tasks.

**What this means for task design:**
- **Smaller tasks = fewer tokens per attempt.** "Add dependency to build.gradle.kts" is cheaper than "implement authentication."
- **Cooldown between retries matters.** If you're rate-limited to N tokens/hour, spacing retries by 5-10 minutes avoids hitting the ceiling.
- **Context size directly impacts token cost.** Every file the agent reads costs tokens. Narrow context = cheaper execution.
- **Retry with context is cheaper than retry without.** Appending "Attempt 1 failed because X" to the next attempt avoids the agent rediscovering the same dead end.

**The fix:**
- **Task metadata carries budget hints:** `retries:1 cooldown:300` for expensive tasks in rate-limited environments
- **Track cumulative token usage across a chain** and warn/stop if approaching limits
- **The engine should estimate cost before executing** — "This chain has 5 tasks × ~50K tokens = ~250K tokens. Your hourly limit is 200K. Proceed?"

**Principle:** _Token budget is a first-class constraint. Design tasks to be token-efficient, not just correct._

---

## 7. The Dynamic Plan Problem

**What happened (design discussion):** We recognized that plans change mid-execution. A task might discover a missing prerequisite that wasn't in the original plan. The agent needs to adapt, but unconstrained adaptation leads to infinite task spawning.

**The rule we adopted:** Agents can propose new tasks (add to `tasks.md`) but cannot auto-execute them. The new task blocks the current one, and a human decides whether to proceed.

**Why this matters:**
- Agents are optimistic planners — they'll create subtasks forever
- Each new task costs tokens and time
- A human can spot "this new task is actually a sign we should rethink the approach"
- The plan is a living document, but the human is the editor

**The fix:**
- **`tasks.md` is re-read from disk before every attempt** (`@UntrackedTask` ensures no caching)
- **New tasks added by an agent are detected** on the next `opsx-sync`
- **Blocking status (`[~]`) + reason** gives the human enough context to decide next steps
- **Never auto-execute a task an agent created.** That's how you get infinite loops.

**Principle:** _Agents propose, humans approve. The plan adapts, but always under human control._

---

## 8. The Zombie Process Problem

**What happened:** The Gradle JVM process timed out and called `destroyForcibly()` on the agent. The agent's child processes (language servers, file watchers) survived as orphans. The Gradle JVM became a zombie waiting for pipe cleanup.

**The fix:**
- **Use process groups** to kill the entire process tree, not just the parent
- **Clean up agent state directories** after timeout (`.copilot/`, `.claude/`, etc.)
- **Check for zombies** before starting a new execution
- **Don't pipe through `tail`/`grep`** in the outer shell — it creates additional pipe dependencies that can prevent process cleanup

**Principle:** _Killing an agent is messy. Kill the process group, clean the state, verify the corpse._

---

## 9. Agent-Specific Behaviors

Different agents behave differently under the same prompt. Knowing their tendencies helps design better tasks.

| Behavior | Copilot | Claude Code | Codex | OpenCode |
|----------|---------|-------------|-------|----------|
| Scope discipline | Low — reads everything, touches everything | High — follows instructions literally | Medium | Unknown |
| File reading strategy | Reads broadly, explores | Reads what's referenced | Reads broadly | Reads referenced |
| Token efficiency | Medium — verbose summaries | High — concise | High | Unknown |
| Non-interactive maturity | Mature (`--allow-all`) | Mature (`--dangerously-skip-permissions`) | Mature (`--full-auto`) | Early (`opencode run`, no YOLO) |
| Timeout risk | High — slow summaries | Low — fast responses | Medium | Unknown |
| Error recovery | Good — retries internally | Good | Good | Unknown |

**Implication:** You might want different prompt strategies per agent. Copilot needs stronger scope constraints. Claude Code can handle more context without going off-script. Codex is fast but may need more explicit instructions.

---

## 10. The Verification Gap

**What we learned:** "Agent exited 0" does not mean the task is done correctly. The agent might have:
- Made syntactically correct but semantically wrong changes
- Partially implemented the task
- Changed the right files but broken unrelated tests
- "Fixed" a build error by deleting the offending code

**The fix:**
- **`./gradlew build` is the minimum bar.** If it doesn't compile, it's not done.
- **`opsx-verify` for architecture rules.** Catches things like circular dependencies, oversized classes, too many imports.
- **Custom verify commands per task** — e.g., "Run `./gradlew :catalog:compileKotlinDesktop` to verify" for a UI task.
- **Human review is still required.** Automated verification catches crashes, not design decisions.

**Principle:** _Trust but verify. The build passing is necessary but not sufficient._

---

## Summary: Design Principles for Agentic Execution

1. **Agents see scope, not vision.** Narrow the context to the current task.
2. **Prompts are pointers, not payloads.** Reference files, don't embed them.
3. **Timeouts protect, they don't optimize.** Set them generous.
4. **The build is truth.** Verify after every task, not just at the end.
5. **Tokens are money.** Design tasks to be token-efficient.
6. **Agents propose, humans approve.** Never auto-execute agent-created tasks.
7. **Kill the tree, not the branch.** Process cleanup needs process groups.
8. **Different agents, different strategies.** One prompt doesn't fit all.
9. **Trust but verify.** Exit code 0 ≠ task complete.
10. **The plan is alive.** Re-read from disk, never cache, always expect change.
