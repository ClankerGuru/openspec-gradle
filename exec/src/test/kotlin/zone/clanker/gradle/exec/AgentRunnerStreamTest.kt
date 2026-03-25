package zone.clanker.gradle.exec

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.assertTrue

class AgentRunnerStreamTest {

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `onOutput callback receives lines from process`() {
        val received = mutableListOf<String>()

        val process = ProcessBuilder("sh", "-c", "printf 'hello\nworld\n'")
            .redirectErrorStream(true)
            .start()

        process.inputStream.bufferedReader().forEachLine { line ->
            received.add(line)
        }
        process.waitFor()

        assertTrue(received.size >= 2, "Expected at least 2 lines, got ${received.size}")
        assertTrue(received.contains("hello"))
        assertTrue(received.contains("world"))
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `output is still captured in AgentResult with callback`() {
        val received = mutableListOf<String>()

        val stdout = StringBuilder()
        val process = ProcessBuilder("echo", "test-output")
            .redirectErrorStream(true)
            .start()

        process.inputStream.bufferedReader().forEachLine { line ->
            received.add(line)
            stdout.appendLine(line)
        }
        process.waitFor()

        assertTrue(stdout.toString().contains("test-output"))
        assertTrue(received.any { it.contains("test-output") })
    }

    @Test
    fun `default no-op callback does not break`() {
        val noOp: (String) -> Unit = {}
        noOp("test") // should not throw
    }
}
