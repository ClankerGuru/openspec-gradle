package zone.clanker.gradle.exec

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ExecStatusReaderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `read returns null for missing file`() {
        val result = ExecStatusReader.read(File(tempDir, "nonexistent.json"))
        assertNull(result)
    }

    @Test
    fun `read returns null for corrupt JSON`() {
        val file = File(tempDir, "status.json")
        file.writeText("not json at all {{{")
        assertNull(ExecStatusReader.read(file))
    }

    @Test
    fun `read parses valid status JSON`() {
        val file = File(tempDir, "status.json")
        file.writeText("""
            {
                "proposal": "test-change",
                "startedAt": "2026-03-25T14:30:00",
                "currentLevel": 1,
                "tasks": {
                    "tc-1": { "status": "DONE", "agent": "claude", "duration": "12s", "level": 0 },
                    "tc-2": { "status": "RUNNING", "attempt": 1, "maxAttempts": 3, "agent": "claude", "startedAt": "2026-03-25T14:30:30", "level": 1 }
                },
                "parallel": true,
                "totalThreads": 4,
                "activeThreads": 1,
                "verifyMode": "compile"
            }
        """.trimIndent())

        val status = ExecStatusReader.read(file)
        assertNotNull(status)
        assertEquals("test-change", status.proposal)
        assertEquals(2, status.tasks.size)
        assertTrue(status.parallel)
        assertEquals(4, status.totalThreads)
        assertEquals(1, status.activeThreads)
        assertEquals("compile", status.verifyMode)
    }

    @Test
    fun `renderDashboard shows progress bar with correct percentage`() {
        val status = ExecStatus(
            proposal = "my-feature",
            startedAt = "2026-03-25T14:30:00",
            currentLevel = 0,
            tasks = mapOf(
                "mf-1" to TaskExecStatus(status = TaskExecStatus.DONE, level = 0),
                "mf-2" to TaskExecStatus(status = TaskExecStatus.DONE, level = 0),
                "mf-3" to TaskExecStatus(status = TaskExecStatus.PENDING, level = 1),
            ),
        )
        val output = ExecStatusReader.renderDashboard(status)
        assertTrue(output.contains("66%"), "Expected 66% in output:\n$output")
        assertTrue(output.contains("2/3"), "Expected 2/3 in output:\n$output")
        assertTrue(output.contains("my-feature"), "Expected proposal name in output:\n$output")
    }

    @Test
    fun `renderDashboard shows running tasks with status icon`() {
        val status = ExecStatus(
            proposal = "test",
            startedAt = "2026-03-25T14:30:00",
            currentLevel = 0,
            tasks = mapOf(
                "t-1" to TaskExecStatus(status = TaskExecStatus.RUNNING, agent = "claude", attempt = 2, maxAttempts = 3, level = 0),
            ),
        )
        val output = ExecStatusReader.renderDashboard(status)
        assertTrue(output.contains("claude"), "Expected agent name in output:\n$output")
        assertTrue(output.contains("2/3"), "Expected attempt info in output:\n$output")
    }

    @Test
    fun `renderDashboard shows parallel info when parallel`() {
        val status = ExecStatus(
            proposal = "test",
            startedAt = "2026-03-25T14:30:00",
            currentLevel = 0,
            tasks = mapOf(
                "t-1" to TaskExecStatus(status = TaskExecStatus.RUNNING, level = 0),
            ),
            parallel = true,
            totalThreads = 4,
            activeThreads = 2,
        )
        val output = ExecStatusReader.renderDashboard(status)
        assertTrue(output.contains("Parallel"), "Expected 'Parallel' in output:\n$output")
        assertTrue(output.contains("2/4"), "Expected '2/4' threads in output:\n$output")
    }

    @Test
    fun `renderDashboard hides parallel line when not parallel`() {
        val status = ExecStatus(
            proposal = "test",
            startedAt = "2026-03-25T14:30:00",
            currentLevel = 0,
            tasks = mapOf(
                "t-1" to TaskExecStatus(status = TaskExecStatus.DONE, level = 0),
            ),
            parallel = false,
        )
        val output = ExecStatusReader.renderDashboard(status)
        assertTrue(!output.contains("Parallel"), "Expected no 'Parallel' in output:\n$output")
    }

    @Test
    fun `renderDashboard shows all status icons`() {
        val status = ExecStatus(
            proposal = "test",
            startedAt = "2026-03-25T14:30:00",
            currentLevel = 0,
            tasks = mapOf(
                "t-1" to TaskExecStatus(status = TaskExecStatus.DONE, level = 0),
                "t-2" to TaskExecStatus(status = TaskExecStatus.RUNNING, level = 0),
                "t-3" to TaskExecStatus(status = TaskExecStatus.PENDING, level = 1),
                "t-4" to TaskExecStatus(status = TaskExecStatus.FAILED, level = 1),
                "t-5" to TaskExecStatus(status = TaskExecStatus.CANCELLED, level = 2),
            ),
        )
        val output = ExecStatusReader.renderDashboard(status)
        // Verify icons are present
        assertTrue(output.contains("\u2705"), "Expected done icon")
        assertTrue(output.contains("\ud83d\udd04"), "Expected running icon")
        assertTrue(output.contains("\u23f3"), "Expected pending icon")
        assertTrue(output.contains("\u274c"), "Expected failed icon")
        assertTrue(output.contains("\ud83d\udeab"), "Expected cancelled icon")
    }
}
