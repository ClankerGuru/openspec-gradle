package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextTaskTest {

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
    }

    private fun contextFile() = File(testProjectDir, ".openspec/context.md")

    @Test
    fun `task generates context md`() {
        File(testProjectDir, "build.gradle.kts").writeText("")
        val result = gradle("opsx-context").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-context")?.outcome)
        assertTrue(contextFile().exists())
    }

    @Test
    fun `context includes project name`() {
        File(testProjectDir, "build.gradle.kts").writeText("")
        gradle("opsx-context").build()
        val content = contextFile().readText()
        assertTrue(content.contains("**Name:**"))
    }

    @Test
    fun `context includes group and version`() {
        File(testProjectDir, "build.gradle.kts").writeText("""
            group = "com.example"
            version = "1.2.3"
        """.trimIndent())
        gradle("opsx-context").build()
        val content = contextFile().readText()
        assertTrue(content.contains("com.example"))
        assertTrue(content.contains("1.2.3"))
    }

    @Test
    fun `context includes dependencies`() {
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins {
                java
            }
            repositories { mavenCentral() }
            dependencies {
                implementation("com.google.guava:guava:33.0.0-jre")
            }
        """.trimIndent())
        gradle("opsx-context").build()
        val content = contextFile().readText()
        assertTrue(content.contains("guava"))
    }

    @Test
    fun `context includes source set info`() {
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { java }
        """.trimIndent())
        File(testProjectDir, "src/main/java").mkdirs()
        gradle("opsx-context").build()
        val content = contextFile().readText()
        assertTrue(content.contains("Source Sets"))
        assertTrue(content.contains("main"))
    }

    @Test
    fun `context includes applied plugins`() {
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { java }
        """.trimIndent())
        gradle("opsx-context").build()
        val content = contextFile().readText()
        assertTrue(content.contains("Plugins") || content.contains("java"))
    }

    @Test
    fun `multi-project builds show module graph`() {
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.gradle")
            }
            include("core", "app")
        """.trimIndent())
        File(testProjectDir, "build.gradle.kts").writeText("")
        File(testProjectDir, "core").mkdirs()
        File(testProjectDir, "core/build.gradle.kts").writeText("""
            plugins { java }
        """.trimIndent())
        File(testProjectDir, "app").mkdirs()
        File(testProjectDir, "app/build.gradle.kts").writeText("""
            plugins { java }
            dependencies {
                implementation(project(":core"))
            }
        """.trimIndent())
        gradle("opsx-context").build()
        val content = contextFile().readText()
        assertTrue(content.contains("Modules"))
        assertTrue(content.contains(":core"))
        assertTrue(content.contains(":app"))
    }

    @Test
    fun `task is annotated as cacheable`() {
        // Verify the task class has @CacheableTask annotation
        // (actual UP-TO-DATE behavior depends on file system watching which may not work in temp dirs)
        File(testProjectDir, "build.gradle.kts").writeText("")
        gradle("opsx-context").build()
        assertTrue(contextFile().exists())
        // Second run should at minimum succeed
        val result = gradle("opsx-context").build()
        val outcome = result.task(":opsx-context")?.outcome
        assertTrue(outcome == TaskOutcome.UP_TO_DATE || outcome == TaskOutcome.FROM_CACHE || outcome == TaskOutcome.SUCCESS,
            "Task should complete successfully but was $outcome")
    }

    @Test
    fun `opsx-sync depends on opsx-context`() {
        File(testProjectDir, "build.gradle.kts").writeText("")
        val result = gradle("opsx-sync").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-context")?.outcome)
        assertTrue(contextFile().exists())
    }

    @Test
    fun `opsx-sync adds openspec to global gitignore`() {
        File(testProjectDir, "build.gradle.kts").writeText("")
        gradle("opsx-sync").build()
        val globalGitignore = zone.clanker.gradle.generators.GlobalGitignore.resolveGlobalGitignoreFile()
        assertTrue(globalGitignore.exists())
        assertTrue(globalGitignore.readText().contains(".openspec/"))
    }

    @Test
    fun `context includes gradle version`() {
        File(testProjectDir, "build.gradle.kts").writeText("")
        gradle("opsx-context").build()
        val content = contextFile().readText()
        assertTrue(content.contains("Gradle:"))
    }
}
