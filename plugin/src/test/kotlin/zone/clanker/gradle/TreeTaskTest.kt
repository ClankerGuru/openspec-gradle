package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeTaskTest {

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
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { java }
        """.trimIndent())
    }

    private fun outputFile() = File(testProjectDir, ".opsx/tree.md")

    @Test
    fun `generates tree md`() {
        File(testProjectDir, "src/main/java/com/example").mkdirs()
        File(testProjectDir, "src/main/java/com/example/Main.java").writeText("class Main {}")
        val result = gradle("opsx-tree").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-tree")?.outcome)
        assertTrue(outputFile().exists())
        val content = outputFile().readText()
        assertTrue(content.contains("Main.java"))
        assertTrue(content.contains("files"))
    }

    @Test
    fun `scope option filters source sets`() {
        File(testProjectDir, "src/main/java/com/example").mkdirs()
        File(testProjectDir, "src/main/java/com/example/Main.java").writeText("class Main {}")
        File(testProjectDir, "src/test/java/com/example").mkdirs()
        File(testProjectDir, "src/test/java/com/example/MainTest.java").writeText("class MainTest {}")
        val result = gradle("opsx-tree", "-Pscope=main").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-tree")?.outcome)
        val content = outputFile().readText()
        assertTrue(content.contains("Main.java"))
        assertTrue(!content.contains("MainTest.java"))
    }

    @Test
    fun `module option filters modules`() {
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins { id("zone.clanker.gradle") }
            include("core", "app")
        """.trimIndent())
        File(testProjectDir, "build.gradle.kts").writeText("")
        File(testProjectDir, "core").mkdirs()
        File(testProjectDir, "core/build.gradle.kts").writeText("plugins { java }")
        File(testProjectDir, "core/src/main/java").mkdirs()
        File(testProjectDir, "core/src/main/java/Core.java").writeText("class Core {}")
        File(testProjectDir, "app").mkdirs()
        File(testProjectDir, "app/build.gradle.kts").writeText("plugins { java }")
        File(testProjectDir, "app/src/main/java").mkdirs()
        File(testProjectDir, "app/src/main/java/App.java").writeText("class App {}")
        val result = gradle("opsx-tree", "-Pmodule=core").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-tree")?.outcome)
        val content = outputFile().readText()
        assertTrue(content.contains("Core.java"))
        assertTrue(!content.contains("App.java"))
    }
}
