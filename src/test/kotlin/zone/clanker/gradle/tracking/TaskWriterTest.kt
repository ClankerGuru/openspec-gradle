package zone.clanker.gradle.tracking

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File

class TaskWriterTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `updateStatus changes checkbox`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("""
            - [ ] `t-1` First task
            - [ ] `t-2` Second task
            - [x] `t-3` Third task
        """.trimIndent() + "\n")

        assertTrue(TaskWriter.updateStatus(file, "t-2", TaskStatus.DONE))

        val lines = file.readLines()
        assertTrue(lines[1].contains("[x]"))
        assertTrue(lines[1].contains("`t-2`"))
    }

    @Test
    fun `updateStatus to IN_PROGRESS`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("- [ ] `t-1` My task\n")

        assertTrue(TaskWriter.updateStatus(file, "t-1", TaskStatus.IN_PROGRESS))

        val content = file.readText()
        assertTrue(content.contains("[/] 🔄 `t-1`"), "Expected [/] + 🔄, got: $content")
    }

    @Test
    fun `updateStatus to TODO resets checkbox`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("- [x] `t-1` Done task\n")

        assertTrue(TaskWriter.updateStatus(file, "t-1", TaskStatus.TODO))

        val content = file.readText()
        assertTrue(content.contains("[ ]"))
    }

    @Test
    fun `updateStatus returns false for unknown code`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("- [ ] `t-1` Only task\n")

        assertFalse(TaskWriter.updateStatus(file, "t-999", TaskStatus.DONE))
    }

    @Test
    fun `updateStatus on nested task`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("""
            - [ ] `t-1` Parent
              - [ ] `t-1.1` Child one
              - [ ] `t-1.2` Child two
        """.trimIndent() + "\n")

        assertTrue(TaskWriter.updateStatus(file, "t-1.2", TaskStatus.DONE))

        val lines = file.readLines()
        assertTrue(lines[2].contains("[x]"))
        assertTrue(lines[2].contains("`t-1.2`"))
        // Parent and sibling unchanged
        assertTrue(lines[0].contains("[ ]"))
        assertTrue(lines[1].contains("[ ]"))
    }

    @Test
    fun `updateStatusInLines modifies list in place`() {
        val lines = mutableListOf(
            "- [ ] `t-1` Task one",
            "- [ ] `t-2` Task two"
        )

        assertTrue(TaskWriter.updateStatusInLines(lines, "t-1", TaskStatus.DONE))
        assertTrue(lines[0].contains("[x]"))
        // t-2 unchanged
        assertTrue(lines[1].contains("[ ]"))
    }

    @Test
    fun `propagateCompletion marks parent done when all children done`() {
        val file = File(tempDir, "tasks.md")
        file.writeText("""
            - [ ] `t-1` Parent
              - [x] `t-1.1` Child one
              - [x] `t-1.2` Child two
            - [ ] `t-2` Another parent
              - [x] `t-2.1` Done
              - [ ] `t-2.2` Not done
        """.trimIndent() + "\n")

        val tasks = TaskParser.parse(file)
        val completed = TaskWriter.propagateCompletion(file, tasks)

        assertEquals(listOf("t-1"), completed)

        val content = file.readText()
        // t-1 should now be done
        assertTrue(content.lines()[0].contains("[x]"))
        // t-2 should still be todo (t-2.2 is not done)
        assertTrue(content.lines()[3].contains("[ ]"))
    }
}
