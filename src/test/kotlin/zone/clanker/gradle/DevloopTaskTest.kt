package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevloopTaskTest {

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

    private fun outputFile() = File(testProjectDir, ".opsx/devloop.md")

    @Test
    fun `generates devloop md`() {
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { java }
        """.trimIndent())
        val result = gradle("opsx-devloop").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-devloop")?.outcome)
        assertTrue(outputFile().exists())
        val content = outputFile().readText()
        assertTrue(content.contains("Build"))
        assertTrue(content.contains("Test"))
        assertTrue(content.contains("./gradlew"))
    }

    @Test
    fun `multi-module has no malformed double-colon prefixes`() {
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins { id("zone.clanker.gradle") }
            include("core", "app")
        """.trimIndent())
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { java }
        """.trimIndent())
        File(testProjectDir, "core").mkdirs()
        File(testProjectDir, "core/build.gradle.kts").writeText("plugins { java }")
        File(testProjectDir, "app").mkdirs()
        File(testProjectDir, "app/build.gradle.kts").writeText("""
            plugins {
                java
                application
            }
            application { mainClass.set("com.example.Main") }
        """.trimIndent())
        val result = gradle("opsx-devloop").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-devloop")?.outcome)
        val content = outputFile().readText()
        // Root project commands should not have :: prefix
        assertTrue(!content.contains("::"), "Output should not contain '::' malformed prefixes, got:\n$content")
        // Should list modules
        assertTrue(content.contains(":core"))
        assertTrue(content.contains(":app"))
    }

    @Test
    fun `includes application run command`() {
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins {
                java
                application
            }
            application { mainClass.set("com.example.Main") }
        """.trimIndent())
        val result = gradle("opsx-devloop").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-devloop")?.outcome)
        val content = outputFile().readText()
        assertTrue(content.contains("Run"))
        assertTrue(content.contains("run"))
    }
}
