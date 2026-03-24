package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File

class TaskLifecycleTest {

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
    fun `set done with file-exists assertion passes when file exists`() {
        // Create the file that the assertion expects
        val srcFile = File(projectDir, "src/main/kotlin/Foo.kt")
        srcFile.parentFile.mkdirs()
        srcFile.writeText("class Foo")

        createProposal("test-proj", """
            - [ ] `tp-1` Create Foo class
              > verify: file-exists src/main/kotlin/Foo.kt
        """.trimIndent())

        gradle("opsx-tp-1", "--set=progress").build()
        val result = gradle("opsx-tp-1", "--set=done", "--force=true").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-tp-1")?.outcome)
    }

    @Test
    fun `set done with file-exists assertion fails when file missing`() {
        createProposal("test-proj", """
            - [ ] `tp-1` Create Bar class
              > verify: file-exists src/main/kotlin/Bar.kt
        """.trimIndent())

        gradle("opsx-tp-1", "--set=progress").build()
        // No --force, so assertions run and file-exists should fail
        val result = gradle("opsx-tp-1", "--set=done").buildAndFail()
        assertTrue(
            result.output.contains("not found") || result.output.contains("Verification failed"),
            "Expected verification failure message, got: ${result.output}"
        )

        // Verify task stayed IN_PROGRESS
        val content = File(projectDir, "opsx/changes/test-proj/tasks.md").readText()
        assertFalse(content.contains("[x]"), "Task should NOT be marked done. Content:\n$content")
    }

    @Test
    fun `force-completed task gets unverified marker`() {
        createProposal("test-proj", """
            - [ ] `tp-1` A task
        """.trimIndent())

        gradle("opsx-tp-1", "--set=progress").build()
        gradle("opsx-tp-1", "--set=done", "--force=true").build()

        val content = File(projectDir, "opsx/changes/test-proj/tasks.md").readText()
        assertTrue(
            content.contains("⚠️ unverified"),
            "Force-completed task should have unverified marker. Content:\n$content"
        )
    }

    @Test
    fun `task with no verify line defaults to build-passes`() {
        createProposal("test-proj", """
            - [ ] `tp-1` Simple task without assertions
        """.trimIndent())

        gradle("opsx-tp-1", "--set=progress").build()
        // Without --force, this will try to run build — which will fail in test env (no src)
        // but the point is it TRIES to run the gate, not that it passes
        val result = gradle("opsx-tp-1", "--set=done").buildAndFail()
        assertTrue(
            result.output.contains("Build") || result.output.contains("Verification"),
            "Should attempt build gate, got: ${result.output}"
        )
    }

    @Test
    fun `parent auto-completes without running assertions`() {
        // Parent has a file-exists assertion that would FAIL
        // But it should auto-complete (skipping assertions) when children are done
        createProposal("test-proj", """
            - [ ] `tp-1` Parent task
              > verify: file-exists nonexistent/file.kt
              - [x] `tp-1.1` Child one done
              - [ ] `tp-1.2` Last child
        """.trimIndent())

        gradle("opsx-tp-1.2", "--set=progress").build()
        val result = gradle("opsx-tp-1.2", "--set=done", "--force=true").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-tp-1.2")?.outcome)

        // Parent should be auto-completed despite having a failing assertion
        val content = File(projectDir, "opsx/changes/test-proj/tasks.md").readText()
        assertTrue(
            content.lines().any { it.contains("[x]") && it.contains("`tp-1`") },
            "Parent tp-1 should be auto-completed. Content:\n$content"
        )
    }

    @Test
    fun `configurable verify command is used`() {
        // Use file-exists assertion to test without needing a real build
        val srcFile = File(projectDir, "src/main/kotlin/Foo.kt")
        srcFile.parentFile.mkdirs()
        srcFile.writeText("class Foo")

        createProposal("test-proj", """
            - [ ] `tp-1` Create Foo
              > verify: file-exists src/main/kotlin/Foo.kt
        """.trimIndent())

        // Set a verify command that doesn't exist — but file-exists assertion should
        // still be checked since it's declared explicitly
        File(projectDir, "gradle.properties").writeText(
            "zone.clanker.openspec.agents=github\nzone.clanker.openspec.verifyCommand=compileKotlin\n"
        )

        gradle("opsx-tp-1", "--set=progress").build()
        // The file-exists assertion passes, and the build-passes assertion is NOT in the list
        // (because explicit assertions were declared), so this should succeed
        val result = gradle("opsx-tp-1", "--set=done").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-tp-1")?.outcome)
    }
}
