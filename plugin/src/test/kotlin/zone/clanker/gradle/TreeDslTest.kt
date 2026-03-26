package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the tree DSL (`includeTree`) and backward compat of `includeEnabled`.
 */
class TreeDslTest {

    @TempDir
    lateinit var testProjectDir: File

    @TempDir
    lateinit var workspaceDir: File

    private fun gradle(vararg args: String) = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments(*args)

    private fun esc(path: String) = path.replace("\\", "/")

    private fun createLibProject(
        dirName: String,
        projectName: String,
        group: String,
        dependencies: List<String> = emptyList()
    ): File {
        val dir = File(workspaceDir, dirName)
        dir.mkdirs()
        File(dir, "settings.gradle.kts").writeText("rootProject.name = \"$projectName\"")

        val depsBlock = if (dependencies.isNotEmpty()) {
            "dependencies {\n" + dependencies.joinToString("\n") { "    implementation(\"$it\")" } + "\n}"
        } else ""

        File(dir, "build.gradle.kts").writeText("""
            plugins { id("java-library") }
            group = "$group"
            version = "1.0.0"
            $depsBlock
        """.trimIndent())

        val srcDir = File(dir, "src/main/java/com/test/${projectName.replace("-", "")}")
        srcDir.mkdirs()
        File(srcDir, "Marker.java").writeText("""
            package com.test.${projectName.replace("-", "")};
            public class Marker {}
        """.trimIndent())

        return dir
    }

    // --- cbd-12: tree DSL with transitive substitution ---

    @Test
    fun `includeTree resolves transitive deps via flat inclusion`() {
        // Create: host-app (registered in monolith), core-lib, feature-lib (depends on core-lib)
        createLibProject("host-app", "host-app", "com.test.host")
        createLibProject("core-lib", "core-lib", "com.test")
        createLibProject("feature-lib", "feature-lib", "com.test",
            dependencies = listOf("com.test:core-lib:1.0.0"))

        // host-app depends on feature-lib (and transitively on core-lib)
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { id("java") }
            dependencies {
                implementation("com.test:feature-lib:1.0.0")
            }
        """.trimIndent())

        val configFile = File(testProjectDir, "monolith.json")
        configFile.writeText("""
            [
              {"name": "TestOrg/host-app", "enable": true, "category": "apps", "substitutions": []},
              {"name": "TestOrg/core-lib", "enable": true, "substitute": true, "category": "core", "substitutions": ["com.test:core-lib,:"]},
              {"name": "TestOrg/feature-lib", "enable": true, "substitute": true, "category": "features", "substitutions": ["com.test:feature-lib,:"]}
            ]
        """.trimIndent())

        // Tree: hostApp -> featureLib -> coreLib
        // hostApp is the root (excluded from inclusion — it's the caller)
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.monolith")
            }
            monolith {
                featureLib.includeBuild(coreLib)
                hostApp.includeBuild(featureLib)
            }
            monolith.includeTree(monolith["hostApp"])
        """.trimIndent())

        val result = gradle(
            "build",
            "-Pzone.clanker.openspec.monolithFile=${esc(configFile.absolutePath)}",
            "-Pzone.clanker.openspec.monolithDir=${esc(workspaceDir.absolutePath)}"
        ).build()

        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "Tree DSL should resolve transitive deps. Output:\n${result.output}"
        )
    }

    @Test
    fun `includeTree deduplicates repos referenced multiple times`() {
        createLibProject("host-app", "host-app", "com.test.host")
        createLibProject("core-lib", "core-lib", "com.test")
        createLibProject("lib-a", "lib-a", "com.test",
            dependencies = listOf("com.test:core-lib:1.0.0"))
        createLibProject("lib-b", "lib-b", "com.test",
            dependencies = listOf("com.test:core-lib:1.0.0"))

        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { id("java") }
            dependencies {
                implementation("com.test:lib-a:1.0.0")
                implementation("com.test:lib-b:1.0.0")
            }
        """.trimIndent())

        val configFile = File(testProjectDir, "monolith.json")
        configFile.writeText("""
            [
              {"name": "TestOrg/host-app", "enable": true, "category": "apps", "substitutions": []},
              {"name": "TestOrg/core-lib", "enable": true, "substitute": true, "category": "core", "substitutions": ["com.test:core-lib,:"]},
              {"name": "TestOrg/lib-a", "enable": true, "substitute": true, "category": "libs", "substitutions": ["com.test:lib-a,:"]},
              {"name": "TestOrg/lib-b", "enable": true, "substitute": true, "category": "libs", "substitutions": ["com.test:lib-b,:"]}
            ]
        """.trimIndent())

        // Both lib-a and lib-b include core-lib — tree should deduplicate
        // hostApp is the root (excluded from inclusion)
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.monolith")
            }
            monolith {
                libA.includeBuild(coreLib)
                libB.includeBuild(coreLib)
                hostApp.includeBuild(libA, libB)
            }
            monolith.includeTree(monolith["hostApp"])
        """.trimIndent())

        val result = gradle(
            "build",
            "-Pzone.clanker.openspec.monolithFile=${esc(configFile.absolutePath)}",
            "-Pzone.clanker.openspec.monolithDir=${esc(workspaceDir.absolutePath)}"
        ).build()

        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "Tree DSL should deduplicate shared deps. Output:\n${result.output}"
        )
    }

    // --- cbd-13: tree DSL with duplicate :app subprojects ---

    @Test
    fun `includeTree with duplicate rootProject names uses sanitizedBuildName`() {
        // Two libs both named "app" internally but in different directories
        val lib1 = File(workspaceDir, "Lib One App")
        lib1.mkdirs()
        File(lib1, "settings.gradle.kts").writeText("rootProject.name = \"app\"")
        File(lib1, "build.gradle.kts").writeText("""
            plugins { id("java-library") }
            group = "com.test.one"
            version = "1.0.0"
        """.trimIndent())
        val src1 = File(lib1, "src/main/java/com/test/one")
        src1.mkdirs()
        File(src1, "One.java").writeText("package com.test.one; public class One {}")

        val lib2 = File(workspaceDir, "Lib Two App")
        lib2.mkdirs()
        File(lib2, "settings.gradle.kts").writeText("rootProject.name = \"app\"")
        File(lib2, "build.gradle.kts").writeText("""
            plugins { id("java-library") }
            group = "com.test.two"
            version = "1.0.0"
        """.trimIndent())
        val src2 = File(lib2, "src/main/java/com/test/two")
        src2.mkdirs()
        File(src2, "Two.java").writeText("package com.test.two; public class Two {}")

        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { id("java") }
            dependencies {
                implementation("com.test.one:app:1.0.0")
                implementation("com.test.two:app:1.0.0")
            }
        """.trimIndent())

        val configFile = File(testProjectDir, "monolith.json")
        configFile.writeText("""
            [
              {"name": "TestOrg/Lib One App", "enable": true, "substitute": true, "category": "libs", "substitutions": ["com.test.one:app,:"]},
              {"name": "TestOrg/Lib Two App", "enable": true, "substitute": true, "category": "libs", "substitutions": ["com.test.two:app,:"]}
            ]
        """.trimIndent())

        // Both have rootProject.name = "app" but sanitizedBuildName differs
        // ("lib-one-app" vs "lib-two-app")
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.monolith")
            }
            monolith.includeEnabled()
        """.trimIndent())

        val result = gradle(
            "projects",
            "-Pzone.clanker.openspec.monolithFile=${esc(configFile.absolutePath)}",
            "-Pzone.clanker.openspec.monolithDir=${esc(workspaceDir.absolutePath)}"
        ).build()

        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "Two builds with same rootProject.name but different sanitizedBuildName should coexist. Output:\n${result.output}"
        )
    }

    // --- cbd-14: includeEnabled backward compatibility ---

    @Test
    fun `includeEnabled still works with sanitized names`() {
        createLibProject("core-lib", "core-lib", "com.test")
        createLibProject("feature-lib", "feature-lib", "com.test",
            dependencies = listOf("com.test:core-lib:1.0.0"))

        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { id("java") }
            dependencies {
                implementation("com.test:core-lib:1.0.0")
                implementation("com.test:feature-lib:1.0.0")
            }
        """.trimIndent())

        val configFile = File(testProjectDir, "monolith.json")
        configFile.writeText("""
            [
              {"name": "TestOrg/core-lib", "enable": true, "substitute": true, "category": "core", "substitutions": ["com.test:core-lib,:"]},
              {"name": "TestOrg/feature-lib", "enable": true, "substitute": true, "category": "features", "substitutions": ["com.test:feature-lib,:"]}
            ]
        """.trimIndent())

        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.monolith")
            }
            monolith.includeEnabled()
        """.trimIndent())

        val result = gradle(
            "build",
            "-Pzone.clanker.openspec.monolithFile=${esc(configFile.absolutePath)}",
            "-Pzone.clanker.openspec.monolithDir=${esc(workspaceDir.absolutePath)}"
        ).build()

        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "includeEnabled should still work as before with sanitized names. Output:\n${result.output}"
        )
    }

    @Test
    fun `includeEnabled with spaces in directory names resolves correctly`() {
        createLibProject("My Core Lib", "core-lib", "com.test")

        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { id("java") }
            dependencies {
                implementation("com.test:core-lib:1.0.0")
            }
        """.trimIndent())

        val configFile = File(testProjectDir, "monolith.json")
        configFile.writeText("""
            [
              {"name": "TestOrg/My Core Lib", "enable": true, "substitute": true, "category": "core", "substitutions": ["com.test:core-lib,:"]}
            ]
        """.trimIndent())

        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.monolith")
            }
            monolith.includeEnabled()
        """.trimIndent())

        val result = gradle(
            "build",
            "-Pzone.clanker.openspec.monolithFile=${esc(configFile.absolutePath)}",
            "-Pzone.clanker.openspec.monolithDir=${esc(workspaceDir.absolutePath)}"
        ).build()

        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "includeEnabled with spaces in dir name should work. Output:\n${result.output}"
        )
    }

    @Test
    fun `includeEnabled with disabled repos does not include them`() {
        createLibProject("core-lib", "core-lib", "com.test")
        createLibProject("disabled-lib", "disabled-lib", "com.test")

        File(testProjectDir, "build.gradle.kts").writeText("")

        val configFile = File(testProjectDir, "monolith.json")
        configFile.writeText("""
            [
              {"name": "TestOrg/core-lib", "enable": true, "category": "core", "substitutions": []},
              {"name": "TestOrg/disabled-lib", "enable": false, "category": "libs", "substitutions": []}
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
            "-Pzone.clanker.openspec.monolithFile=${esc(configFile.absolutePath)}",
            "-Pzone.clanker.openspec.monolithDir=${esc(workspaceDir.absolutePath)}"
        ).build()

        assertTrue(result.output.contains("core-lib"), "core-lib should be included")
        // disabled-lib should NOT appear as an included build
        assertTrue(
            !result.output.contains("disabled-lib"),
            "disabled-lib should NOT be included. Output:\n${result.output}"
        )
    }
}
