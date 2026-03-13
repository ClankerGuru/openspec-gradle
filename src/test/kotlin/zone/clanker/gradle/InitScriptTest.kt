package zone.clanker.gradle

import zone.clanker.gradle.tasks.OpenSpecInstallGlobalTask.Companion.generateInitScript
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class InitScriptTest {

    @TempDir
    lateinit var testProjectDir: File

    private fun gradle(vararg args: String) = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments(*args)

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.gradle")
            }
        """.trimIndent())
        File(testProjectDir, "build.gradle.kts").writeText("")
    }

    @Test
    fun `generated init script contains correct plugin version`() {
        val script = generateInitScript("0.1.0")
        assertTrue(script.contains("zone.clanker:openspec-gradle:0.1.0"))
    }

    @Test
    fun `generated init script uses settings plugin`() {
        val script = generateInitScript("0.1.0")
        assertTrue(script.contains("beforeSettings {"))
        assertTrue(script.contains("apply<zone.clanker.gradle.OpenSpecSettingsPlugin>()"))
    }

    @Test
    fun `generated init script has initscript dependencies`() {
        val script = generateInitScript("0.1.0")
        assertTrue(script.contains("initscript {"))
        assertTrue(script.contains("mavenLocal()"))
        assertTrue(script.contains("mavenCentral()"))
    }

    @Test
    fun `generated init script documents property config`() {
        val script = generateInitScript("0.1.0")
        assertTrue(script.contains("zone.clanker.openspec.agents"))
    }

    @Test
    fun `openspecInstallGlobal task is registered`() {
        val result = gradle("tasks", "--group=openspec").build()
        assertTrue(result.output.contains("openspecInstallGlobal"))
    }
}
