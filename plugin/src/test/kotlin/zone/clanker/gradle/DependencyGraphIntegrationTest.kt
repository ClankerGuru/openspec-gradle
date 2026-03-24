package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencyGraphIntegrationTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun setup() {
        File(projectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.gradle")
            }
        """.trimIndent())
        File(projectDir, "build.gradle.kts").writeText("")
        File(projectDir, "gradle.properties").writeText("zone.clanker.openspec.agents=github\n")
    }

    private fun gradle(vararg args: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*args)
        .withPluginClasspath()
        .forwardOutput()

    private fun createProposal(name: String, tasks: String) {
        val dir = File(projectDir, "opsx/changes/$name")
        dir.mkdirs()
        File(dir, "tasks.md").writeText(tasks)
    }

    @Test
    fun `setting done on task with incomplete deps fails`() {
        createProposal("dep-test", """
            - [ ] `dt-1` First task
            - [ ] `dt-2` Second task → depends: dt-1
        """.trimIndent())

        // Setting to progress with unmet deps should fail
        val result = gradle("opsx-dt-2", "--set=progress").buildAndFail()
        assertTrue(result.output.contains("blocked") || result.output.contains("dependencies"))
        assertTrue(result.output.contains("dt-1"))
    }

    @Test
    fun `marking deps done then dependent succeeds`() {
        createProposal("dep-test", """
            - [ ] `dt-1` First task
            - [ ] `dt-2` Second task → depends: dt-1
        """.trimIndent())

        // Mark dependency done (progress → done with --force)
        gradle("opsx-dt-1", "--set=progress").build()
        val r1 = gradle("opsx-dt-1", "--set=done", "--force=true").build()
        assertEquals(TaskOutcome.SUCCESS, r1.task(":opsx-dt-1")?.outcome)

        // Now the dependent task should succeed
        gradle("opsx-dt-2", "--set=progress").build()
        val r2 = gradle("opsx-dt-2", "--set=done", "--force=true").build()
        assertEquals(TaskOutcome.SUCCESS, r2.task(":opsx-dt-2")?.outcome)
    }

    @Test
    fun `cycle detection blocks status change`() {
        createProposal("cycle-test", """
            - [ ] `ct-1` Task one → depends: ct-2
            - [ ] `ct-2` Task two → depends: ct-1
        """.trimIndent())

        // Try to set progress — should fail on cycle detection
        val result = gradle("opsx-ct-1", "--set=progress").buildAndFail()
        assertTrue(result.output.contains("cycle"), "Should mention cycle in output: ${result.output}")
    }

    @Test
    fun `status dashboard warns about cycles`() {
        createProposal("cycle-test", """
            - [ ] `ct-1` Task one → depends: ct-2
            - [ ] `ct-2` Task two → depends: ct-1
        """.trimIndent())

        val result = gradle("opsx-status").build()
        assertTrue(result.output.contains("cycle") || result.output.contains("WARNING"),
            "Dashboard should warn about cycles: ${result.output}")
    }
}
