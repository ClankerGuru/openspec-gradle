package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertTrue

class ExecIntegrationTest {

    @TempDir
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

    private fun gradle(vararg args: String) = GradleRunner.create()
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
