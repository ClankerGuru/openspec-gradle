package zone.clanker.gradle

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import zone.clanker.gradle.exec.AgentRunner
import zone.clanker.gradle.exec.CycleDetector
import zone.clanker.gradle.exec.SpecParser
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExecTest {

    // ── AgentRunner ──

    @Test
    fun `buildCommand copilot`() {
        val cmd = AgentRunner.buildCommand("github-copilot", "hello")
        assertEquals(listOf("copilot", "-p", "hello", "--allow-all", "-s"), cmd)
    }

    @Test
    fun `buildCommand github alias`() {
        val cmd = AgentRunner.buildCommand("github", "hello")
        assertEquals(listOf("copilot", "-p", "hello", "--allow-all", "-s"), cmd)
    }

    @Test
    fun `buildCommand claude`() {
        val cmd = AgentRunner.buildCommand("claude", "refactor")
        assertEquals(listOf("claude", "-p", "refactor", "--dangerously-skip-permissions"), cmd)
    }

    @Test
    fun `buildCommand codex`() {
        val cmd = AgentRunner.buildCommand("codex", "fix tests")
        assertEquals(listOf("codex", "exec", "fix tests", "--full-auto"), cmd)
    }

    @Test
    fun `buildCommand opencode`() {
        val cmd = AgentRunner.buildCommand("opencode", "add docs")
        assertEquals(listOf("opencode", "run", "add docs"), cmd)
    }

    @Test
    fun `buildCommand unknown throws`() {
        assertThrows<IllegalArgumentException> {
            AgentRunner.buildCommand("unknown-agent", "hello")
        }
    }

    @Test
    fun `resolveAgent explicit wins`() {
        val agent = AgentRunner.resolveAgent("claude", listOf("github"))
        assertEquals("claude", agent)
    }

    @Test
    fun `resolveAgent uses first configured`() {
        val agent = AgentRunner.resolveAgent(null, listOf("codex", "claude"))
        assertEquals("codex", agent)
    }

    @Test
    fun `resolveAgent uses first from configured list`() {
        val agent = AgentRunner.resolveAgent(null, listOf("claude", "codex"))
        assertEquals("claude", agent)
    }

    // ── CycleDetector ──

    @Test
    fun `no cycle on first two errors`() {
        val detector = CycleDetector()
        assertFalse(detector.recordAndCheck("error A"))
        assertFalse(detector.recordAndCheck("error B"))
    }

    @Test
    fun `cycle detected when error repeats after gap`() {
        val detector = CycleDetector()
        assertFalse(detector.recordAndCheck("error A"))
        assertFalse(detector.recordAndCheck("error B"))
        assertTrue(detector.recordAndCheck("error A"))
    }

    @Test
    fun `no cycle on consecutive same errors`() {
        // Same error twice in a row is retry, not cycle
        val detector = CycleDetector()
        assertFalse(detector.recordAndCheck("error A"))
        assertFalse(detector.recordAndCheck("error A")) // immediate retry, not cycle
    }

    @Test
    fun `findCycleMatch returns attempt number`() {
        val detector = CycleDetector()
        detector.recordAndCheck("error A")
        detector.recordAndCheck("error B")
        detector.recordAndCheck("error A")
        assertEquals(1, detector.findCycleMatch("error A"))
    }

    @Test
    fun `findCycleMatch returns null for new error`() {
        val detector = CycleDetector()
        detector.recordAndCheck("error A")
        assertNull(detector.findCycleMatch("error C"))
    }

    // ── SpecParser ──

    @Test
    fun `parse spec with all fields`() {
        val content = """
            |# Task: Extract UserService interface
            |
            |agent: copilot
            |max-retries: 5
            |verify: false
            |
            |## Prompt
            |
            |Extract an interface from UserService.kt.
            |Keep all public methods.
        """.trimMargin()

        val spec = SpecParser.parse(content)
        assertEquals("Extract UserService interface", spec.title)
        assertEquals("copilot", spec.agent)
        assertEquals(5, spec.maxRetries)
        assertEquals(false, spec.verify)
        assertEquals("Extract an interface from UserService.kt.\nKeep all public methods.", spec.prompt)
    }

    @Test
    fun `parse spec with no metadata`() {
        val content = """
            |# Task: Simple refactor
            |
            |## Prompt
            |
            |Just do the thing.
        """.trimMargin()

        val spec = SpecParser.parse(content)
        assertEquals("Simple refactor", spec.title)
        assertNull(spec.agent)
        assertNull(spec.maxRetries)
        assertNull(spec.verify)
        assertEquals("Just do the thing.", spec.prompt)
    }

    @Test
    fun `parse spec with no prompt heading uses whole content`() {
        val content = "Refactor everything to use coroutines."
        val spec = SpecParser.parse(content)
        assertEquals("untitled", spec.title)
        assertEquals("Refactor everything to use coroutines.", spec.prompt)
    }

    @Test
    fun `parse spec partial metadata`() {
        val content = """
            |# Task: Add tests
            |
            |agent: claude
            |
            |## Prompt
            |
            |Write tests for UserService.
        """.trimMargin()

        val spec = SpecParser.parse(content)
        assertEquals("claude", spec.agent)
        assertNull(spec.maxRetries)
        assertNull(spec.verify)
    }

    // ── Gradle integration tests ──

    @org.junit.jupiter.api.io.TempDir
    lateinit var testProjectDir: java.io.File

    private fun setupProject() {
        testProjectDir.resolve("settings.gradle.kts").writeText("""
            plugins { id("zone.clanker.gradle") }
        """.trimIndent())
        testProjectDir.resolve("build.gradle.kts").writeText("")
        testProjectDir.resolve("gradle.properties").writeText(
            "zone.clanker.openspec.agents=github\n"
        )
    }

    private fun gradle(vararg args: String) = org.gradle.testkit.runner.GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments(*args)

    @Test
    fun `opsx-exec task is registered`() {
        setupProject()
        try {
            val result = gradle("tasks", "--group=opsx").forwardOutput().build()
            assertTrue(result.output.contains("opsx-exec"), "opsx-exec task should be listed.\nOutput: ${result.output}")
        } catch (e: org.gradle.testkit.runner.UnexpectedBuildFailure) {
            throw AssertionError("Inner build failed:\n${e.buildResult.output}", e)
        }
    }

    @Test
    fun `opsx-exec fails without prompt or spec`() {
        setupProject()
        val result = gradle("opsx-exec").buildAndFail()
        assertTrue(
            result.output.contains("Either -Pprompt") || result.output.contains("prompt"),
            "Should fail with missing prompt error",
        )
    }
}
