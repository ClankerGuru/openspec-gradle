package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CloneTaskTest {

    @TempDir
    lateinit var testProjectDir: File

    @TempDir
    lateinit var workspaceDir: File

    private fun gradle(vararg args: String) = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments(*args)

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.monolith")
            }
        """.trimIndent())
        File(testProjectDir, "build.gradle.kts").writeText("")
    }

    @Test
    fun `opsx-clone task is registered`() {
        val result = gradle("tasks", "--group=opsx").build()
        assertTrue(result.output.contains("opsx-clone"))
    }

    @Test
    fun `dry-run lists repos without cloning`() {
        val configFile = File(testProjectDir, "monolith.json")
        val cloneDir = File(workspaceDir, "clones")
        configFile.writeText("""
            [
              {"name": "ClankerGuru/my-repo", "enable": true, "category": "libs", "substitutions": []}
            ]
        """.trimIndent())

        val result = gradle(
            "opsx-clone",
            "-Pzone.clanker.openspec.monolithFile=${configFile.absolutePath}",
            "-Pzone.clanker.openspec.monolithDir=${cloneDir.absolutePath}"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-clone")?.outcome)
        assertTrue(result.output.contains("DRY RUN"))
        assertTrue(result.output.contains("CLONE"))
        assertTrue(result.output.contains("ClankerGuru/my-repo"))
        assertFalse(File(cloneDir, "my-repo").exists())
    }

    @Test
    fun `skips disabled entries`() {
        val configFile = File(testProjectDir, "monolith.json")
        val cloneDir = File(workspaceDir, "clones")
        configFile.writeText("""
            [
              {"name": "ClankerGuru/disabled-repo", "enable": false, "category": "internal", "substitutions": []}
            ]
        """.trimIndent())

        val result = gradle(
            "opsx-clone",
            "-Pzone.clanker.openspec.monolithFile=${configFile.absolutePath}",
            "-Pzone.clanker.openspec.monolithDir=${cloneDir.absolutePath}"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-clone")?.outcome)
        assertTrue(result.output.contains("No enabled repos"))
    }

    @Test
    fun `skips existing directories`() {
        val configFile = File(testProjectDir, "monolith.json")
        val cloneDir = File(workspaceDir, "clones")
        File(cloneDir, "existing-repo").mkdirs()

        configFile.writeText("""
            [
              {"name": "ClankerGuru/existing-repo", "enable": true, "category": "libs", "substitutions": []}
            ]
        """.trimIndent())

        val result = gradle(
            "opsx-clone",
            "-Pzone.clanker.openspec.monolithFile=${configFile.absolutePath}",
            "-Pzone.clanker.openspec.monolithDir=${cloneDir.absolutePath}"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-clone")?.outcome)
        assertTrue(result.output.contains("SKIP"))
        assertTrue(result.output.contains("already exists"))
    }

    @Test
    fun `handles missing JSON file gracefully`() {
        val missingFile = File(testProjectDir, "nonexistent.json")

        val result = gradle(
            "opsx-clone",
            "-Pzone.clanker.openspec.monolithFile=${missingFile.absolutePath}"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-clone")?.outcome)
        assertTrue(result.output.contains("Config file not found"))
    }

    @Test
    fun `includeEnabled includes existing repos as composite builds`() {
        val configFile = File(testProjectDir, "monolith.json")
        val cloneDir = File(workspaceDir, "clones")

        // Create a fake repo directory with a settings file so includeBuild works
        val fakeRepo = File(cloneDir, "my-repo")
        fakeRepo.mkdirs()
        File(fakeRepo, "settings.gradle.kts").writeText("rootProject.name = \"my-repo\"")
        File(fakeRepo, "build.gradle.kts").writeText("")

        configFile.writeText("""
            [
              {"name": "ClankerGuru/my-repo", "enable": true, "category": "libs", "substitutions": []}
            ]
        """.trimIndent())

        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.monolith")
            }
            monolith.includeEnabled()
        """.trimIndent())

        val result = gradle(
            "projects",
            "-Pzone.clanker.openspec.monolithFile=${configFile.absolutePath}",
            "-Pzone.clanker.openspec.monolithDir=${cloneDir.absolutePath}"
        ).build()

        // The included build should appear in the projects listing
        assertTrue(result.output.contains("my-repo"))
    }
}
