package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DepsTaskTest {

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

    private fun outputFile() = File(testProjectDir, ".openspec/deps.md")

    @Test
    fun `generates deps md with external dependencies`() {
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { java }
            repositories { mavenCentral() }
            dependencies {
                implementation("com.google.guava:guava:33.0.0-jre")
            }
        """.trimIndent())
        val result = gradle("opsx-deps").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-deps")?.outcome)
        assertTrue(outputFile().exists())
        val content = outputFile().readText()
        assertTrue(content.contains("guava"))
        assertTrue(content.contains("External Dependencies"))
    }

    @Test
    fun `classifies local module deps`() {
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
        val result = gradle("opsx-deps").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-deps")?.outcome)
        val content = outputFile().readText()
        assertTrue(content.contains("Local Module Dependencies"))
        assertTrue(content.contains(":core"))
    }
}
