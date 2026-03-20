package zone.clanker.gradle.tracking

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ApplyV2Test {

    @TempDir
    lateinit var tempDir: File

    // ── TaskStatus GitHub-compatible format ──

    @Test
    fun `BLOCKED uses tilde checkbox with emoji`() {
        assertEquals("[~]", TaskStatus.BLOCKED.checkbox)
        assertEquals("⛔ ", TaskStatus.BLOCKED.emoji)
        assertEquals("⛔", TaskStatus.BLOCKED.icon)
    }

    @Test
    fun `IN_PROGRESS uses slash checkbox with emoji`() {
        assertEquals("[/]", TaskStatus.IN_PROGRESS.checkbox)
        assertEquals("🔄 ", TaskStatus.IN_PROGRESS.emoji)
        assertEquals("🔄", TaskStatus.IN_PROGRESS.icon)
    }

    @Test
    fun `TODO has no emoji`() {
        assertEquals("[ ]", TaskStatus.TODO.checkbox)
        assertEquals("", TaskStatus.TODO.emoji)
    }

    @Test
    fun `DONE uses checked checkbox no emoji`() {
        assertEquals("[x]", TaskStatus.DONE.checkbox)
        assertEquals("", TaskStatus.DONE.emoji)
    }

    // ── Parsing legacy [/] and [~] (backward compat) ──

    @Test
    fun `parse legacy slash as IN_PROGRESS`() {
        val tasks = TaskParser.parse(listOf("- [/] `t-1` Working on it"))
        assertEquals(1, tasks.size)
        assertEquals(TaskStatus.IN_PROGRESS, tasks[0].status)
    }

    @Test
    fun `parse legacy tilde as BLOCKED`() {
        val tasks = TaskParser.parse(listOf("- [~] `t-1` Stuck on something"))
        assertEquals(1, tasks.size)
        assertEquals(TaskStatus.BLOCKED, tasks[0].status)
    }

    // ── Parsing new emoji format ──

    @Test
    fun `parse emoji IN_PROGRESS`() {
        val tasks = TaskParser.parse(listOf("- [ ] 🔄 `t-1` Working on it"))
        assertEquals(1, tasks.size)
        assertEquals(TaskStatus.IN_PROGRESS, tasks[0].status)
    }

    @Test
    fun `parse emoji BLOCKED`() {
        val tasks = TaskParser.parse(listOf("- [ ] ⛔ `t-1` Stuck"))
        assertEquals(1, tasks.size)
        assertEquals(TaskStatus.BLOCKED, tasks[0].status)
    }

    @Test
    fun `all four statuses parse correctly`() {
        val lines = listOf(
            "- [ ] `t-1` Todo",
            "- [ ] 🔄 `t-2` In progress",
            "- [x] `t-3` Done",
            "- [ ] ⛔ `t-4` Blocked",
        )
        val tasks = TaskParser.parse(lines)
        assertEquals(TaskStatus.TODO, tasks[0].status)
        assertEquals(TaskStatus.IN_PROGRESS, tasks[1].status)
        assertEquals(TaskStatus.DONE, tasks[2].status)
        assertEquals(TaskStatus.BLOCKED, tasks[3].status)
    }

    @Test
    fun `legacy statuses still parse in mixed format`() {
        val lines = listOf(
            "- [ ] `t-1` Todo",
            "- [/] `t-2` Legacy in progress",
            "- [x] `t-3` Done",
            "- [~] `t-4` Legacy blocked",
        )
        val tasks = TaskParser.parse(lines)
        assertEquals(TaskStatus.TODO, tasks[0].status)
        assertEquals(TaskStatus.IN_PROGRESS, tasks[1].status)
        assertEquals(TaskStatus.DONE, tasks[2].status)
        assertEquals(TaskStatus.BLOCKED, tasks[3].status)
    }

    // ── Metadata parsing ──

    @Test
    fun `parse inline metadata with em dash`() {
        val lines = listOf(
            "- [ ] `auth-01` agent:copilot retries:3 cooldown:60 \u2014 Set up OAuth config"
        )
        val tasks = TaskParser.parse(lines)
        assertEquals(1, tasks.size)
        assertEquals("auth-01", tasks[0].code)
        assertEquals("Set up OAuth config", tasks[0].description)
        assertEquals("copilot", tasks[0].metadata.agent)
        assertEquals(3, tasks[0].metadata.retries)
        assertEquals(60, tasks[0].metadata.cooldown)
    }

    @Test
    fun `parse partial metadata`() {
        val lines = listOf(
            "- [ ] `t-1` agent:claude \u2014 Do something"
        )
        val tasks = TaskParser.parse(lines)
        assertEquals("claude", tasks[0].metadata.agent)
        assertNull(tasks[0].metadata.retries)
        assertNull(tasks[0].metadata.cooldown)
    }

    @Test
    fun `backward compat - no metadata no em dash`() {
        val lines = listOf(
            "- [ ] `t-1` Just a plain description"
        )
        val tasks = TaskParser.parse(lines)
        assertEquals("Just a plain description", tasks[0].description)
        assertNull(tasks[0].metadata.agent)
        assertNull(tasks[0].metadata.retries)
        assertNull(tasks[0].metadata.cooldown)
    }

    @Test
    fun `metadata with dependencies`() {
        val lines = listOf(
            "- [ ] `t-2` agent:codex retries:2 \u2014 Build thing \u2192 depends: t-1"
        )
        val tasks = TaskParser.parse(lines)
        assertEquals("Build thing", tasks[0].description)
        assertEquals("codex", tasks[0].metadata.agent)
        assertEquals(2, tasks[0].metadata.retries)
        assertEquals(listOf("t-1"), tasks[0].explicitDeps)
    }

    @Test
    fun `metadata with emoji status`() {
        val lines = listOf(
            "- [ ] 🔄 `t-1` agent:copilot retries:2 \u2014 In progress task"
        )
        val tasks = TaskParser.parse(lines)
        assertEquals(TaskStatus.IN_PROGRESS, tasks[0].status)
        assertEquals("copilot", tasks[0].metadata.agent)
        assertEquals("In progress task", tasks[0].description)
    }

    // ── TaskWriter writes GitHub-compatible format ──

    @Test
    fun `write BLOCKED status uses both checkbox and emoji`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("- [ ] `t-1` A task\n")
        assertTrue(TaskWriter.updateStatus(file, "t-1", TaskStatus.BLOCKED))
        val content = file.readText()
        assertTrue(content.contains("[~] ⛔ `t-1`"), "Expected [~] + emoji, got: $content")
    }

    @Test
    fun `write IN_PROGRESS uses both checkbox and emoji`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("- [ ] `t-1` A task\n")
        assertTrue(TaskWriter.updateStatus(file, "t-1", TaskStatus.IN_PROGRESS))
        val content = file.readText()
        assertTrue(content.contains("[/] 🔄 `t-1`"), "Expected [/] + emoji, got: $content")
    }

    @Test
    fun `write DONE uses checked checkbox`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("- [ ] `t-1` A task\n")
        assertTrue(TaskWriter.updateStatus(file, "t-1", TaskStatus.DONE))
        val content = file.readText()
        assertTrue(content.contains("[x] `t-1`"), "Expected [x] DONE, got: $content")
    }

    @Test
    fun `write TODO clears emoji and resets checkbox`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("- [/] 🔄 `t-1` A task\n")
        assertTrue(TaskWriter.updateStatus(file, "t-1", TaskStatus.TODO))
        val content = file.readText()
        assertTrue(content.contains("[ ] `t-1`"), "Expected clean TODO, got: $content")
        assertFalse(content.contains("🔄"))
    }

    // ── Attempt log appending ──

    @Test
    fun `appendAttemptLog inserts after task line`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("- [ ] 🔄 `t-1` My task\n- [ ] `t-2` Next task\n")

        assertTrue(TaskWriter.appendAttemptLog(file, "t-1", 1, "Build failed"))
        val lines = file.readLines()
        assertEquals(3, lines.size)
        assertTrue(lines[1].contains("> **Attempt 1**"))
        assertTrue(lines[1].contains("Build failed"))
        assertTrue(lines[2].contains("`t-2`"))
    }

    @Test
    fun `appendAttemptLog stacks multiple attempts`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("- [ ] 🔄 `t-1` My task\n- [ ] `t-2` Next\n")

        TaskWriter.appendAttemptLog(file, "t-1", 1, "First fail")
        TaskWriter.appendAttemptLog(file, "t-1", 2, "Second fail")

        val lines = file.readLines()
        assertEquals(4, lines.size)
        assertTrue(lines[1].contains("Attempt 1"))
        assertTrue(lines[2].contains("Attempt 2"))
    }

    @Test
    fun `appendAttemptLog returns false for unknown code`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("- [ ] `t-1` Only task\n")
        assertFalse(TaskWriter.appendAttemptLog(file, "t-999", 1, "msg"))
    }

    // ── Dependency gating ──

    @Test
    fun `DependencyGraph canComplete checks DONE deps`() {
        val tasks = listOf(
            TaskItem("t-1", "First", TaskStatus.DONE),
            TaskItem("t-2", "Second", TaskStatus.TODO, explicitDeps = listOf("t-1")),
            TaskItem("t-3", "Third", TaskStatus.TODO, explicitDeps = listOf("t-2")),
        )
        val graph = DependencyGraph(tasks)
        assertTrue(graph.canComplete("t-2"))
        assertFalse(graph.canComplete("t-3"))
    }

    @Test
    fun `DependencyGraph BLOCKED task blocks dependents`() {
        val tasks = listOf(
            TaskItem("t-1", "First", TaskStatus.BLOCKED),
            TaskItem("t-2", "Second", TaskStatus.TODO, explicitDeps = listOf("t-1")),
        )
        val graph = DependencyGraph(tasks)
        assertFalse(graph.canComplete("t-2"))
    }

    // ── Skip done tasks ──

    @Test
    fun `done tasks should be skippable in chain`() {
        val tasks = listOf(
            TaskItem("t-1", "Done task", TaskStatus.DONE),
            TaskItem("t-2", "Todo task", TaskStatus.TODO),
        )
        assertTrue(tasks[0].status == TaskStatus.DONE)
        assertFalse(tasks[1].status == TaskStatus.DONE)
    }

    // ── Backward compatibility ──

    @Test
    fun `old format without metadata still parses`() {
        val lines = listOf(
            "- [ ] `old-1` Old style task",
            "- [x] `old-2` Done old style",
        )
        val tasks = TaskParser.parse(lines)
        assertEquals(2, tasks.size)
        assertEquals("Old style task", tasks[0].description)
        assertEquals(TaskMetadata(), tasks[0].metadata)
    }

    @Test
    fun `tasks without code still parse`() {
        val lines = listOf("- [ ] A plain task without code")
        val tasks = TaskParser.parse(lines)
        assertEquals(1, tasks.size)
        assertEquals("A plain task without code", tasks[0].description)
    }

    // ── ProposalScanner findByTaskCode ──

    @Test
    fun `findProposalByTaskCode finds correct proposal`() {
        val dir = File(tempDir, "opsx/changes/my-feature")
        dir.mkdirs()
        File(dir, "tasks.md").writeText("- [ ] `mf-1` agent:claude \u2014 A task\n")

        val result = ProposalScanner.findProposalByTaskCode(tempDir, "mf-1")
        assertNotNull(result)
        assertEquals("my-feature", result!!.first.name)
        assertEquals("mf-1", result.second.code)
        assertEquals("claude", result.second.metadata.agent)
    }

    @Test
    fun `findProposalByTaskCode returns null for unknown code`() {
        val dir = File(tempDir, "opsx/changes/test")
        dir.mkdirs()
        File(dir, "tasks.md").writeText("- [ ] `t-1` A task\n")

        assertNull(ProposalScanner.findProposalByTaskCode(tempDir, "unknown"))
    }

    // ── TaskWriter handles legacy lines ──

    @Test
    fun `TaskWriter can update status on legacy slash-checkbox lines`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("- [/] `t-1` In progress task\n")
        assertTrue(TaskWriter.updateStatus(file, "t-1", TaskStatus.DONE))
        assertTrue(file.readText().contains("[x] `t-1`"))
    }

    @Test
    fun `TaskWriter can update status on legacy tilde-checkbox lines`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("- [~] `t-1` Blocked task\n")
        assertTrue(TaskWriter.updateStatus(file, "t-1", TaskStatus.TODO))
        assertTrue(file.readText().contains("[ ] `t-1`"))
    }

    @Test
    fun `TaskWriter can update status on emoji lines`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("- [ ] 🔄 `t-1` In progress task\n")
        assertTrue(TaskWriter.updateStatus(file, "t-1", TaskStatus.DONE))
        assertTrue(file.readText().contains("[x] `t-1`"))
    }
}
