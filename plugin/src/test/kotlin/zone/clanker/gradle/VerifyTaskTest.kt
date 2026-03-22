package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerifyTaskTest {

    @TempDir
    lateinit var projectDir: File

    private fun setup(vararg sources: Pair<String, String>): GradleRunner {
        projectDir.resolve("settings.gradle.kts").writeText("""
            plugins { id("zone.clanker.gradle") }
        """.trimIndent())
        projectDir.resolve("build.gradle.kts").writeText("")

        for ((path, content) in sources) {
            val file = projectDir.resolve(path)
            file.parentFile.mkdirs()
            file.writeText(content)
        }

        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .forwardOutput()
    }

    @Test
    fun `verify passes on clean project`() {
        val result = setup(
            "src/main/kotlin/com/example/Foo.kt" to """
                package com.example
                class Foo {
                    fun doStuff(): String = "hello"
                }
            """.trimIndent()
        ).withArguments("opsx-verify").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-verify")?.outcome)
        val output = projectDir.resolve(".opsx/verify.md").readText()
        assertTrue(output.contains("All checks passed"))
    }

    @Test
    fun `verify detects god class`() {
        val methods = (1..30).joinToString("\n") { "    fun method$it() {}" }
        val result = setup(
            "src/main/kotlin/com/example/GodClass.kt" to """
                package com.example
                ${(1..35).joinToString("\n") { "import java.util.${"List".repeat(1)}" }}
                class GodClass {
                $methods
                }
            """.trimIndent()
        ).withArguments("opsx-verify").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-verify")?.outcome)
        val output = projectDir.resolve(".opsx/verify.md").readText()
        assertTrue(output.contains("finding"))
    }

    @Test
    fun `verify fails with failOnWarning on smell class`() {
        val result = setup(
            "src/main/kotlin/com/example/UserManager.kt" to """
                package com.example

                class UserManager {
                    fun createUser() {}
                    fun deleteUser() {}
                    fun updateUser() {}
                }
            """.trimIndent()
        ).withArguments("opsx-verify", "-PfailOnWarning=true").buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":opsx-verify")?.outcome)
    }

    @Test
    fun `verify detects smell classes with noSmells`() {
        val result = setup(
            "src/main/kotlin/com/example/UserManager.kt" to """
                package com.example

                class UserManager {
                    fun createUser() {}
                    fun deleteUser() {}
                    fun updateUser() {}
                }
            """.trimIndent()
        ).withArguments("opsx-verify", "-PnoSmells=true").buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":opsx-verify")?.outcome)
        val output = projectDir.resolve(".opsx/verify.md").readText()
        assertTrue(output.contains("Code Smell"))
    }

    @Test
    fun `verify with maxWarnings threshold`() {
        val result = setup(
            "src/main/kotlin/com/example/DataHelper.kt" to """
                package com.example

                class DataHelper {
                    fun help() {}
                }
            """.trimIndent()
        ).withArguments("opsx-verify", "-PmaxWarnings=0").buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":opsx-verify")?.outcome)
    }

    @Test
    fun `verify passes within maxWarnings`() {
        val result = setup(
            "src/main/kotlin/com/example/HelperOne.kt" to """
                package com.example
                class HelperOne { fun help() {} }
            """.trimIndent()
        ).withArguments("opsx-verify", "-PmaxWarnings=10").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-verify")?.outcome)
    }
}
