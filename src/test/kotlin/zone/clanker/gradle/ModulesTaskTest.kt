package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModulesTaskTest {

    @TempDir
    lateinit var testProjectDir: File

    private fun gradle(vararg args: String) = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments(*args)

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins { id("zone.clanker.gradle") }
        """.trimIndent())
    }

    private fun outputFile() = File(testProjectDir, ".openspec/modules.md")

    @Test
    fun `single module project`() {
        File(testProjectDir, "build.gradle.kts").writeText("")
        val result = gradle("opsx-modules").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-modules")?.outcome)
        assertTrue(outputFile().exists())
        val content = outputFile().readText()
        assertTrue(content.contains("Single-module"))
    }

    @Test
    fun `nested modules with duplicate leaf names show full paths`() {
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins { id("zone.clanker.gradle") }
            include("libs:core", "apps:core")
        """.trimIndent())
        File(testProjectDir, "build.gradle.kts").writeText("")
        File(testProjectDir, "libs/core").mkdirs()
        File(testProjectDir, "libs/core/build.gradle.kts").writeText("plugins { java }")
        File(testProjectDir, "apps/core").mkdirs()
        File(testProjectDir, "apps/core/build.gradle.kts").writeText("""
            plugins { java }
            dependencies { implementation(project(":libs:core")) }
        """.trimIndent())
        val result = gradle("opsx-modules").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-modules")?.outcome)
        val content = outputFile().readText()
        assertTrue(content.contains(":libs:core"), "Should contain :libs:core path")
        assertTrue(content.contains(":apps:core"), "Should contain :apps:core path")
    }

    @Test
    fun `multi-module shows graph`() {
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins { id("zone.clanker.gradle") }
            include("core", "app")
        """.trimIndent())
        File(testProjectDir, "build.gradle.kts").writeText("")
        File(testProjectDir, "core").mkdirs()
        File(testProjectDir, "core/build.gradle.kts").writeText("plugins { java }")
        File(testProjectDir, "app").mkdirs()
        File(testProjectDir, "app/build.gradle.kts").writeText("""
            plugins { java }
            dependencies { implementation(project(":core")) }
        """.trimIndent())
        val result = gradle("opsx-modules").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-modules")?.outcome)
        val content = outputFile().readText()
        assertTrue(content.contains(":core"))
        assertTrue(content.contains(":app"))
        assertTrue(content.contains("Dependency Graph"))
    }
}
