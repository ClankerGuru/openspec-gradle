package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoveTaskTest {

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
    fun `remove class removes entire declaration`() {
        val result = setup(
            "src/main/kotlin/com/example/Foo.kt" to """
                package com.example
                class Foo {
                    fun doStuff(): String = "hello"
                }
            """.trimIndent(),
            "src/main/kotlin/com/example/Bar.kt" to """
                package com.example
                class Bar {
                    fun use() = Foo().doStuff()
                }
            """.trimIndent()
        ).withArguments("opsx-remove", "-Psymbol=Foo", "-PdryRun=false").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-remove")?.outcome)
        val output = projectDir.resolve(".opsx/remove.md").readText()
        assertTrue(output.contains("Applied"))
        assertTrue(output.contains("Foo"))
        // The class declaration should be removed from the file
        val fooContent = projectDir.resolve("src/main/kotlin/com/example/Foo.kt").readText()
        assertFalse(fooContent.contains("class Foo"))
    }

    @Test
    fun `remove method removes specific function`() {
        val result = setup(
            "src/main/kotlin/com/example/Service.kt" to """
                package com.example
                class Service {
                    fun keep(): String = "keep"
                    fun remove(): String = "remove"
                }
            """.trimIndent()
        ).withArguments("opsx-remove", "-Psymbol=Service.remove", "-PdryRun=false").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-remove")?.outcome)
        val content = projectDir.resolve("src/main/kotlin/com/example/Service.kt").readText()
        assertFalse(content.contains("fun remove()"))
        assertTrue(content.contains("fun keep()"))
    }

    @Test
    fun `remove lines removes specific line range`() {
        val file = "src/main/kotlin/com/example/Foo.kt"
        val result = setup(
            file to """
                package com.example
                class Foo {
                    val a = 1
                    val b = 2
                    val c = 3
                }
            """.trimIndent()
        ).withArguments("opsx-remove", "-Pfile=$file", "-PstartLine=4", "-PendLine=4", "-PdryRun=false").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-remove")?.outcome)
        val content = projectDir.resolve(file).readText()
        assertFalse(content.contains("val b = 2"))
        assertTrue(content.contains("val a = 1"))
        assertTrue(content.contains("val c = 3"))
    }

    @Test
    fun `dry run shows preview without modifying files`() {
        val file = "src/main/kotlin/com/example/Foo.kt"
        val originalContent = """
            package com.example
            class Foo {
                fun doStuff(): String = "hello"
            }
        """.trimIndent()
        val result = setup(
            file to originalContent
        ).withArguments("opsx-remove", "-Psymbol=Foo").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-remove")?.outcome)
        val output = projectDir.resolve(".opsx/remove.md").readText()
        assertTrue(output.contains("DRY RUN"))
        // File should not be modified
        assertEquals(originalContent, projectDir.resolve(file).readText())
    }

    @Test
    fun `remove class cleans up imports in other files`() {
        val result = setup(
            "src/main/kotlin/com/example/Foo.kt" to """
                package com.example
                class Foo {
                    fun doStuff(): String = "hello"
                }
            """.trimIndent(),
            "src/main/kotlin/com/example/Bar.kt" to """
                package com.example
                import com.example.Foo
                class Bar {
                    fun use() = Foo().doStuff()
                }
            """.trimIndent()
        ).withArguments("opsx-remove", "-Psymbol=Foo", "-PdryRun=false").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-remove")?.outcome)
        val barContent = projectDir.resolve("src/main/kotlin/com/example/Bar.kt").readText()
        assertFalse(barContent.contains("import com.example.Foo"))
    }

    @Test
    fun `remove warns about remaining references`() {
        val result = setup(
            "src/main/kotlin/com/example/Foo.kt" to """
                package com.example
                class Foo {
                    fun doStuff(): String = "hello"
                }
            """.trimIndent(),
            "src/main/kotlin/com/example/Bar.kt" to """
                package com.example
                class Bar {
                    fun use() = Foo().doStuff()
                }
            """.trimIndent()
        ).withArguments("opsx-remove", "-Psymbol=Foo").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-remove")?.outcome)
        val output = projectDir.resolve(".opsx/remove.md").readText()
        assertTrue(output.contains("Remaining References"))
        assertTrue(output.contains("Bar.kt"))
    }
}
