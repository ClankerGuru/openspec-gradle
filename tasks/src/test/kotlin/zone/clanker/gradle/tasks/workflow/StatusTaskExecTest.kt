package zone.clanker.gradle.tasks.workflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import zone.clanker.gradle.exec.ExecStatus
import zone.clanker.gradle.exec.ExecStatusReader
import zone.clanker.gradle.exec.TaskExecStatus
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusTaskExecTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `dashboard renders from status json fixture`() {
        val statusFile = File(tempDir, "status.json")
        val status = ExecStatus(
            proposal = "add-user-auth",
            startedAt = "2026-03-25T14:32:05",
            currentLevel = 1,
            tasks = mapOf(
                "aua-1" to TaskExecStatus(status = TaskExecStatus.DONE, agent = "claude", duration = "12s", level = 0),
                "aua-2" to TaskExecStatus(status = TaskExecStatus.DONE, agent = "claude", duration = "8s", level = 0),
                "aua-3" to TaskExecStatus(status = TaskExecStatus.RUNNING, attempt = 1, maxAttempts = 3, agent = "claude", startedAt = "2026-03-25T14:32:50", level = 1),
                "aua-4" to TaskExecStatus(status = TaskExecStatus.RUNNING, attempt = 2, maxAttempts = 3, agent = "copilot", startedAt = "2026-03-25T14:32:50", level = 1),
                "aua-5" to TaskExecStatus(status = TaskExecStatus.PENDING, level = 2),
            ),
            parallel = true,
            totalThreads = 4,
            activeThreads = 2,
            verifyMode = "compile",
        )

        // Write via ExecStatus.write (atomic) and read back
        ExecStatus.write(statusFile, status)
        val read = ExecStatusReader.read(statusFile)
        assertNotNull(read)

        val output = ExecStatusReader.renderDashboard(read)

        // Verify structure
        assertTrue(output.contains("add-user-auth"), "Proposal name missing")
        assertTrue(output.contains("40%"), "Progress percentage missing")
        assertTrue(output.contains("2/5"), "Task count missing")

        // Verify table has all task codes
        assertTrue(output.contains("aua-1"), "Task code aua-1 missing")
        assertTrue(output.contains("aua-2"), "Task code aua-2 missing")
        assertTrue(output.contains("aua-3"), "Task code aua-3 missing")
        assertTrue(output.contains("aua-4"), "Task code aua-4 missing")
        assertTrue(output.contains("aua-5"), "Task code aua-5 missing")

        // Verify agents shown
        assertTrue(output.contains("claude"), "Agent 'claude' missing")
        assertTrue(output.contains("copilot"), "Agent 'copilot' missing")

        // Verify parallel info
        assertTrue(output.contains("Parallel"), "Parallel info missing")
        assertTrue(output.contains("2/4"), "Thread count missing")

        // Verify verify mode
        assertTrue(output.contains("compile"), "Verify mode missing")
    }

    @Test
    fun `no dashboard when status file missing`() {
        val result = ExecStatusReader.read(File(tempDir, "missing.json"))
        assertNull(result)
    }

    @Test
    fun `dashboard handles all-done state`() {
        val status = ExecStatus(
            proposal = "done-change",
            startedAt = "2026-03-25T10:00:00",
            currentLevel = 0,
            tasks = mapOf(
                "dc-1" to TaskExecStatus(status = TaskExecStatus.DONE, duration = "5s", level = 0),
                "dc-2" to TaskExecStatus(status = TaskExecStatus.DONE, duration = "3s", level = 0),
            ),
        )
        val output = ExecStatusReader.renderDashboard(status)
        assertTrue(output.contains("100%"), "Expected 100% for all-done")
        assertTrue(output.contains("2/2"), "Expected 2/2")
    }
}
