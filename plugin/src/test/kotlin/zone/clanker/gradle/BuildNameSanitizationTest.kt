package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildNameSanitizationTest {

    @TempDir
    lateinit var testProjectDir: File

    @TempDir
    lateinit var workspaceDir: File

    private fun gradle(vararg args: String) = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments(*args)

    private fun esc(path: String) = path.replace("\\", "/")

    @Test
    fun `includeBuild sets sanitized name for space-in-name project`() {
        val libDir = File(workspaceDir, "My Library Project")
        libDir.mkdirs()
        File(libDir, "settings.gradle.kts").writeText("rootProject.name = \"my-library-project\"")
        File(libDir, "build.gradle.kts").writeText("""
            plugins { id("java-library") }
            group = "com.test"
        """.trimIndent())

        val configFile = File(testProjectDir, "monolith.json")
        configFile.writeText("""
            [
              {"name": "MyOrg/My Library Project", "enable": true, "category": "libs", "substitutions": [], "substitute": false}
            ]
        """.trimIndent())

        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.monolith")
            }
            monolith.includeEnabled()
        """.trimIndent())
        File(testProjectDir, "build.gradle.kts").writeText("")

        val result = gradle(
            "projects",
            "-Pzone.clanker.openspec.monolithFile=${esc(configFile.absolutePath)}",
            "-Pzone.clanker.openspec.monolithDir=${esc(workspaceDir.absolutePath)}"
        ).build()

        // The sanitized name should appear in included builds
        assertTrue(
            result.output.contains("my-library-project"),
            "Expected sanitized build name 'my-library-project' in output:\n${result.output}"
        )
    }

    @Test
    fun `dependency substitution works with sanitized build name`() {
        // Create a lib with spaces in directory name
        val libDir = File(workspaceDir, "Core Models Lib")
        libDir.mkdirs()
        File(libDir, "settings.gradle.kts").writeText("rootProject.name = \"core-models\"")
        File(libDir, "build.gradle.kts").writeText("""
            plugins { id("java-library") }
            group = "com.test"
            version = "1.0.0"
        """.trimIndent())
        val srcDir = File(libDir, "src/main/java/com/test")
        srcDir.mkdirs()
        File(srcDir, "Model.java").writeText("""
            package com.test;
            public class Model {}
        """.trimIndent())

        // Host depends on the published artifact
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { id("java-library") }
            dependencies {
                implementation("com.test:core-models:1.0.0")
            }
        """.trimIndent())

        val configFile = File(testProjectDir, "monolith.json")
        configFile.writeText("""
            [
              {
                "name": "MyOrg/Core Models Lib",
                "enable": true,
                "category": "core",
                "substitute": true,
                "substitutions": ["com.test:core-models,:"]
              }
            ]
        """.trimIndent())

        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.monolith")
            }
            monolith.includeEnabled()
        """.trimIndent())

        val result = gradle(
            "dependencies", "--configuration", "compileClasspath",
            "-Pzone.clanker.openspec.monolithFile=${esc(configFile.absolutePath)}",
            "-Pzone.clanker.openspec.monolithDir=${esc(workspaceDir.absolutePath)}"
        ).build()

        assertTrue(
            result.output.contains("project :core-models-lib") ||
                result.output.contains("-> project :core-models"),
            "Expected substitution to local project. Output:\n${result.output}"
        )
    }

    @Test
    fun `DSL accessor works with space-in-name repo`() {
        val libDir = File(workspaceDir, "Core Models Lib")
        libDir.mkdirs()
        File(libDir, "settings.gradle.kts").writeText("rootProject.name = \"core-models\"")
        File(libDir, "build.gradle.kts").writeText("")

        val configFile = File(testProjectDir, "monolith.json")
        configFile.writeText("""
            [
              {"name": "MyOrg/Core Models Lib", "enable": true, "category": "core", "substitutions": []}
            ]
        """.trimIndent())

        // Use DSL accessor — toCamelCase("Core Models Lib") should produce "coreModelsLib"
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.monolith")
            }
            monolith {
                coreModelsLib.ref("develop")
            }
        """.trimIndent())
        File(testProjectDir, "build.gradle.kts").writeText("")

        val result = gradle(
            "opsx-repos",
            "-Pzone.clanker.openspec.monolithFile=${esc(configFile.absolutePath)}",
            "-Pzone.clanker.openspec.monolithDir=${esc(workspaceDir.absolutePath)}"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-repos")?.outcome)
        // If the DSL accessor worked, the ref should be "develop"
        val reposFile = File(testProjectDir, ".opsx/repos.md")
        assertTrue(reposFile.exists(), "Expected .opsx/repos.md to exist")
        assertTrue(reposFile.readText().contains("develop"), "Expected ref 'develop' from DSL accessor")
    }

    @Test
    fun `underscored directory names get sanitized accessor`() {
        val libDir = File(workspaceDir, "my_cool_lib")
        libDir.mkdirs()
        File(libDir, "settings.gradle.kts").writeText("rootProject.name = \"my-cool-lib\"")
        File(libDir, "build.gradle.kts").writeText("")

        val configFile = File(testProjectDir, "monolith.json")
        configFile.writeText("""
            [
              {"name": "MyOrg/my_cool_lib", "enable": true, "category": "libs", "substitutions": []}
            ]
        """.trimIndent())

        // toCamelCase("my_cool_lib") should produce "myCoolLib"
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.monolith")
            }
            monolith {
                myCoolLib.ref("feature-x")
            }
        """.trimIndent())
        File(testProjectDir, "build.gradle.kts").writeText("")

        val result = gradle(
            "opsx-repos",
            "-Pzone.clanker.openspec.monolithFile=${esc(configFile.absolutePath)}",
            "-Pzone.clanker.openspec.monolithDir=${esc(workspaceDir.absolutePath)}"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-repos")?.outcome)
        val reposFile = File(testProjectDir, ".opsx/repos.md")
        assertTrue(reposFile.readText().contains("feature-x"), "Expected ref 'feature-x' from DSL accessor")
    }
}
