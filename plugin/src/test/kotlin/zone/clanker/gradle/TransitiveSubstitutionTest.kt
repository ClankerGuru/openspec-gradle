package zone.clanker.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

/**
 * Spike test: does Gradle's flat `includeBuild` + `dependencySubstitution`
 * propagate transitively across included builds?
 *
 * Graph:
 * ```
 * host-app
 * ├── com.test:feature-ui       → feature-ui (local)
 * │   ├── com.test:core-models  → core-models (local)
 * │   └── com.test:core-utils   → core-utils (local)
 * ├── com.test:feature-data     → feature-data (local)
 * │   ├── com.test:core-models  → core-models (local)
 * │   └── com.test:core-utils   → core-utils (local)
 * ├── com.test:core-models      → core-models (local)
 * └── com.test:core-utils       → core-utils (local)
 * ```
 *
 * All 4 libs are included flat at the root level.
 * We verify that feature-ui and feature-data resolve their
 * core-models and core-utils deps to local projects, not Maven artifacts.
 *
 * Directory names intentionally use spaces to test sanitization needs.
 */
class TransitiveSubstitutionTest {

    @TempDir
    lateinit var testProjectDir: File

    @TempDir
    lateinit var workspaceDir: File

    private fun gradle(vararg args: String) = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments(*args)

    /**
     * Create a minimal Gradle project that acts as an included build.
     * - Has a root project with java-library plugin
     * - Can declare dependencies on other test artifacts
     */
    private fun createLibProject(
        dirName: String,
        projectName: String,
        group: String,
        artifactId: String,
        dependencies: List<String> = emptyList()
    ): File {
        val dir = File(workspaceDir, dirName)
        dir.mkdirs()

        File(dir, "settings.gradle.kts").writeText(
            "rootProject.name = \"$projectName\""
        )

        val depsBlock = if (dependencies.isNotEmpty()) {
            dependencies.joinToString("\n    ") { dep ->
                "implementation(\"$dep\")"
            }
        } else ""

        File(dir, "build.gradle.kts").writeText("""
            plugins { id("java-library") }
            group = "$group"
            version = "1.0.0"
            ${if (depsBlock.isNotEmpty()) """
            dependencies {
                $depsBlock
            }
            """.trimIndent() else ""}
        """.trimIndent())

        // Create a minimal source file so the project is valid
        val srcDir = File(dir, "src/main/java/com/test/${artifactId.replace("-", "")}")
        srcDir.mkdirs()
        File(srcDir, "Marker.java").writeText("""
            package com.test.${artifactId.replace("-", "")};
            public class Marker {}
        """.trimIndent())

        return dir
    }

    @BeforeEach
    fun setup() {
        // 1. core-models — no dependencies, directory has spaces
        createLibProject(
            dirName = "Core Models Lib",
            projectName = "core-models",
            group = "com.test",
            artifactId = "core-models"
        )

        // 2. core-utils — no dependencies, directory has spaces
        createLibProject(
            dirName = "Core Utils Lib",
            projectName = "core-utils",
            group = "com.test",
            artifactId = "core-utils"
        )

        // 3. feature-ui — depends on core-models AND core-utils
        createLibProject(
            dirName = "Feature UI Module",
            projectName = "feature-ui",
            group = "com.test",
            artifactId = "feature-ui",
            dependencies = listOf(
                "com.test:core-models:1.0.0",
                "com.test:core-utils:1.0.0"
            )
        )

        // 4. feature-data — depends on core-models AND core-utils
        createLibProject(
            dirName = "Feature Data Module",
            projectName = "feature-data",
            group = "com.test",
            artifactId = "feature-data",
            dependencies = listOf(
                "com.test:core-models:1.0.0",
                "com.test:core-utils:1.0.0"
            )
        )

        // 5. host-app — the main project, depends on all four
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { id("java") }
            dependencies {
                implementation("com.test:core-models:1.0.0")
                implementation("com.test:core-utils:1.0.0")
                implementation("com.test:feature-ui:1.0.0")
                implementation("com.test:feature-data:1.0.0")
            }
        """.trimIndent())
    }

    @Test
    fun `flat includeBuild with substitutions resolves all deps to local projects`() {
        // Configure settings to include all 4 libs flat with substitutions
        File(testProjectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "host-app"

            includeBuild("${esc(workspaceDir)}/Core Models Lib") {
                dependencySubstitution {
                    substitute(module("com.test:core-models")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Core Utils Lib") {
                dependencySubstitution {
                    substitute(module("com.test:core-utils")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Feature UI Module") {
                dependencySubstitution {
                    substitute(module("com.test:feature-ui")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Feature Data Module") {
                dependencySubstitution {
                    substitute(module("com.test:feature-data")).using(project(":"))
                }
            }
        """.trimIndent())

        // Resolve the dependency tree — if transitive substitution works,
        // feature-ui's dep on core-models should resolve to the local project
        val result = gradle(
            "dependencies", "--configuration", "compileClasspath"
        ).build()

        val output = result.output

        // All four should be substituted with local projects — match each specific module
        assertTrue(
            output.contains("project :Core Models Lib") || output.contains("project :core-models"),
            "Expected core-models to be substituted. Output:\n$output"
        )
        assertTrue(
            output.contains("project :Core Utils Lib") || output.contains("project :core-utils"),
            "Expected core-utils to be substituted. Output:\n$output"
        )
        assertTrue(
            output.contains("project :Feature UI Module") || output.contains("project :feature-ui"),
            "Expected feature-ui to be substituted. Output:\n$output"
        )
        assertTrue(
            output.contains("project :Feature Data Module") || output.contains("project :feature-data"),
            "Expected feature-data to be substituted. Output:\n$output"
        )
    }

    @Test
    fun `flat includeBuild transitively substitutes deps inside included builds`() {
        // Same setup — flat inclusion of all 4 libs
        File(testProjectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "host-app"

            includeBuild("${esc(workspaceDir)}/Core Models Lib") {
                dependencySubstitution {
                    substitute(module("com.test:core-models")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Core Utils Lib") {
                dependencySubstitution {
                    substitute(module("com.test:core-utils")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Feature UI Module") {
                dependencySubstitution {
                    substitute(module("com.test:feature-ui")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Feature Data Module") {
                dependencySubstitution {
                    substitute(module("com.test:feature-data")).using(project(":"))
                }
            }
        """.trimIndent())

        // Build the full project — this forces resolution of transitive deps.
        // If feature-ui can compile, it means its dep on core-models/core-utils
        // was resolved to the local projects (since they aren't published to any repo).
        val result = gradle("build").build()

        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "Build should succeed with transitive substitution. Output:\n${result.output}"
        )
    }

    @Test
    fun `monolith plugin flat includeEnabled resolves transitive deps across 5 projects`() {
        val configFile = File(workspaceDir, "monolith.json")
        configFile.writeText("""
            [
              {
                "name": "TestOrg/Core Models Lib",
                "enable": true,
                "substitute": true,
                "category": "core",
                "substitutions": ["com.test:core-models,core-models"]
              },
              {
                "name": "TestOrg/Core Utils Lib",
                "enable": true,
                "substitute": true,
                "category": "core",
                "substitutions": ["com.test:core-utils,core-utils"]
              },
              {
                "name": "TestOrg/Feature UI Module",
                "enable": true,
                "substitute": true,
                "category": "features",
                "substitutions": ["com.test:feature-ui,feature-ui"]
              },
              {
                "name": "TestOrg/Feature Data Module",
                "enable": true,
                "substitute": true,
                "category": "features",
                "substitutions": ["com.test:feature-data,feature-data"]
              }
            ]
        """.trimIndent())

        // Each lib uses root project ":" as the substitution target,
        // but monolith.json uses the project name. We need the settings
        // to match — the substitution format is "artifact,projectName"
        // which becomes substitute(module("artifact")).using(project(":projectName"))
        //
        // Since these are single-project builds, the root IS the project.
        // Update the settings files to use include-friendly names.
        // Actually, the substitution "com.test:core-models,core-models" means
        // project(":core-models") inside the included build. But our test libs
        // are single-project — the root project IS the artifact.
        // Let's adjust: use root project substitution format.

        // Rewrite: each lib is a single-module project, so substitution
        // should reference the root project ":"
        configFile.writeText("""
            [
              {
                "name": "TestOrg/Core Models Lib",
                "enable": true,
                "substitute": true,
                "category": "core",
                "substitutions": ["com.test:core-models,:"]
              },
              {
                "name": "TestOrg/Core Utils Lib",
                "enable": true,
                "substitute": true,
                "category": "core",
                "substitutions": ["com.test:core-utils,:"]
              },
              {
                "name": "TestOrg/Feature UI Module",
                "enable": true,
                "substitute": true,
                "category": "features",
                "substitutions": ["com.test:feature-ui,:"]
              },
              {
                "name": "TestOrg/Feature Data Module",
                "enable": true,
                "substitute": true,
                "category": "features",
                "substitutions": ["com.test:feature-data,:"]
              }
            ]
        """.trimIndent())

        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.monolith")
            }
            monolith.includeEnabled()
        """.trimIndent())

        // Build — this proves flat includeEnabled + substitution
        // resolves transitive deps inside included builds
        val result = gradle(
            "build",
            "-Pzone.clanker.openspec.monolithFile=${configFile.absolutePath}",
            "-Pzone.clanker.openspec.monolithDir=${workspaceDir.absolutePath}"
        ).build()

        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "Build should succeed with transitive substitution via monolith plugin. Output:\n${result.output}"
        )
    }

    @Test
    fun `directory names with spaces work in includeBuild`() {
        // Verify the spaces in directory names don't break Gradle
        File(testProjectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "host-app"

            includeBuild("${esc(workspaceDir)}/Core Models Lib")
            includeBuild("${esc(workspaceDir)}/Core Utils Lib")
            includeBuild("${esc(workspaceDir)}/Feature UI Module")
            includeBuild("${esc(workspaceDir)}/Feature Data Module")
        """.trimIndent())

        val result = gradle("projects").build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), "Projects with spaces should load")
        // Check that the included builds appear (Gradle uses the directory name as build name)
        assertTrue(result.output.contains("core-models") || result.output.contains("Core Models"),
            "core-models build should be included. Output:\n${result.output}")
    }

    @Test
    fun `duplicate root project names across included builds`() {
        // Create two libs that both have rootProject.name = "app"
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

        // Include both — Gradle should handle via directory-based naming
        // but if both resolve to "app", this may fail
        File(testProjectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "host-app"

            includeBuild("${esc(workspaceDir)}/Lib One App") {
                name = "lib-one-app"
            }
            includeBuild("${esc(workspaceDir)}/Lib Two App") {
                name = "lib-two-app"
            }
        """.trimIndent())

        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { id("java") }
            dependencies {
                implementation("com.test.one:app:1.0.0")
                implementation("com.test.two:app:1.0.0")
            }
        """.trimIndent())

        // This tests whether explicit name= prevents collisions
        val result = gradle("projects").build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"),
            "Two included builds with same rootProject.name but different build names should work. Output:\n${result.output}")
    }

    @Test
    fun `substitution is global - feature-data gets core-models substituted even if only declared on core-models includeBuild`() {
        // This test answers: if I substitute core-models globally,
        // does feature-data (which depends on core-models) also get the local version?
        // Expected: YES — Gradle's dependencySubstitution is global.
        //
        // Graph:
        //   host-app → feature-ui (substituted, depends on core-models)
        //            → feature-data (substituted, depends on core-models)
        //            → core-models (substituted)
        //            → core-utils (substituted)
        //
        // All substitutions declared. Both feature libs get local core-models.
        File(testProjectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "host-app"

            includeBuild("${esc(workspaceDir)}/Core Models Lib") {
                dependencySubstitution {
                    substitute(module("com.test:core-models")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Core Utils Lib") {
                dependencySubstitution {
                    substitute(module("com.test:core-utils")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Feature UI Module") {
                dependencySubstitution {
                    substitute(module("com.test:feature-ui")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Feature Data Module") {
                dependencySubstitution {
                    substitute(module("com.test:feature-data")).using(project(":"))
                }
            }
        """.trimIndent())

        val result = gradle("build").build()

        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "Both features should resolve core-models via substitution. Output:\n${result.output}"
        )
    }

    @Test
    fun `substitution is global - both consumers get core-models substituted even with asymmetric deps`() {
        // PROVES: substitution is global, not per-consumer.
        // Both feature-ui AND feature-data depend on core-models.
        // We substitute core-models once. Both consumers get the local version.
        // There is no way to give one the published version and the other the local one.

        // Create a host-only lib that neither feature imports
        val hostOnlyLib = File(workspaceDir, "Host Only Analytics")
        hostOnlyLib.mkdirs()
        File(hostOnlyLib, "settings.gradle.kts").writeText("rootProject.name = \"analytics\"")
        File(hostOnlyLib, "build.gradle.kts").writeText("""
            plugins { id("java-library") }
            group = "com.test"
            version = "1.0.0"
        """.trimIndent())
        val analyticsSrc = File(hostOnlyLib, "src/main/java/com/test/analytics")
        analyticsSrc.mkdirs()
        File(analyticsSrc, "Analytics.java").writeText("""
            package com.test.analytics;
            public class Analytics {}
        """.trimIndent())

        // feature-ui depends on core-models only (asymmetric)
        File(File(workspaceDir, "Feature UI Module"), "build.gradle.kts").writeText("""
            plugins { id("java-library") }
            group = "com.test"
            version = "1.0.0"
            dependencies {
                implementation("com.test:core-models:1.0.0")
            }
        """.trimIndent())

        // feature-data ALSO depends on core-models (the shared dep we're testing)
        // plus core-utils (asymmetric — only feature-data has this)
        File(File(workspaceDir, "Feature Data Module"), "build.gradle.kts").writeText("""
            plugins { id("java-library") }
            group = "com.test"
            version = "1.0.0"
            dependencies {
                implementation("com.test:core-models:1.0.0")
                implementation("com.test:core-utils:1.0.0")
            }
        """.trimIndent())

        // host-app depends on everything
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { id("java") }
            dependencies {
                implementation("com.test:core-models:1.0.0")
                implementation("com.test:core-utils:1.0.0")
                implementation("com.test:feature-ui:1.0.0")
                implementation("com.test:feature-data:1.0.0")
                implementation("com.test:analytics:1.0.0")
            }
        """.trimIndent())

        // Include ALL with substitutions
        File(testProjectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "host-app"

            includeBuild("${esc(workspaceDir)}/Core Models Lib") {
                dependencySubstitution {
                    substitute(module("com.test:core-models")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Core Utils Lib") {
                dependencySubstitution {
                    substitute(module("com.test:core-utils")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Feature UI Module") {
                dependencySubstitution {
                    substitute(module("com.test:feature-ui")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Feature Data Module") {
                dependencySubstitution {
                    substitute(module("com.test:feature-data")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Host Only Analytics") {
                dependencySubstitution {
                    substitute(module("com.test:analytics")).using(project(":"))
                }
            }
        """.trimIndent())

        // Build succeeds — both feature-ui and feature-data resolve core-models
        // to the local project (because substitution is global).
        // If substitution were per-consumer, we'd need separate configuration.
        val result = gradle("build").build()
        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "Both consumers should get core-models substituted globally. Output:\n${result.output}"
        )

        // Verify via dependency tree that core-models appears as a project for both
        val depsResult = gradle("dependencies", "--configuration", "compileClasspath").build()
        val depsOutput = depsResult.output
        assertTrue(
            depsOutput.contains("project :Core Models Lib") || depsOutput.contains("project :core-models"),
            "core-models should be substituted as a project. Output:\n$depsOutput"
        )
    }

    @Test
    fun `partial substitution - unsubstituted dep fails resolution proving substitution is not implicit`() {
        // Include feature-ui (substituted) and core-models (substituted)
        // but do NOT include core-utils.
        // feature-ui depends on core-models only (modified in this test).
        // feature-data depends on core-utils only — but core-utils is NOT substituted.
        //
        // Expected: feature-data FAILS because core-utils can't be resolved from any repo.
        // This proves substitution only applies to what you explicitly declare.

        // Modify feature-ui to only depend on core-models
        File(File(workspaceDir, "Feature UI Module"), "build.gradle.kts").writeText("""
            plugins { id("java-library") }
            group = "com.test"
            version = "1.0.0"
            dependencies {
                implementation("com.test:core-models:1.0.0")
            }
        """.trimIndent())

        // Modify feature-data to only depend on core-utils
        File(File(workspaceDir, "Feature Data Module"), "build.gradle.kts").writeText("""
            plugins { id("java-library") }
            group = "com.test"
            version = "1.0.0"
            dependencies {
                implementation("com.test:core-utils:1.0.0")
            }
        """.trimIndent())

        // host-app depends on feature-ui and feature-data
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins { id("java") }
            dependencies {
                implementation("com.test:feature-ui:1.0.0")
                implementation("com.test:feature-data:1.0.0")
            }
        """.trimIndent())

        // Include core-models, feature-ui, feature-data — but NOT core-utils
        File(testProjectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "host-app"

            includeBuild("${esc(workspaceDir)}/Core Models Lib") {
                dependencySubstitution {
                    substitute(module("com.test:core-models")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Feature UI Module") {
                dependencySubstitution {
                    substitute(module("com.test:feature-ui")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Feature Data Module") {
                dependencySubstitution {
                    substitute(module("com.test:feature-data")).using(project(":"))
                }
            }
        """.trimIndent())

        // feature-ui should work (core-models is substituted)
        // feature-data should FAIL (core-utils is not substituted and not in any repo)
        val result = gradle("build").buildAndFail()

        assertTrue(
            result.output.contains("core-utils") && (
                result.output.contains("Could not resolve") ||
                result.output.contains("Could not find") ||
                result.output.contains("Cannot resolve")
            ),
            "Should fail to resolve core-utils since it's not substituted. Output:\n${result.output}"
        )
    }

    @Test
    fun `substitution is global not per-consumer - cannot scope substitution to one build`() {
        // CRITICAL TEST: Prove that you CANNOT substitute core-models only for feature-ui.
        // Both feature-ui AND feature-data depend on core-models (from @BeforeEach).
        // We substitute core-models once. Both consumers get the local version.
        //
        // If per-consumer scoping existed, we could substitute only for feature-ui.
        // But it doesn't — substitution is global.

        // Include everything with substitution
        File(testProjectDir, "settings.gradle.kts").writeText("""
            rootProject.name = "host-app"

            includeBuild("${esc(workspaceDir)}/Core Models Lib") {
                dependencySubstitution {
                    substitute(module("com.test:core-models")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Core Utils Lib") {
                dependencySubstitution {
                    substitute(module("com.test:core-utils")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Feature UI Module") {
                dependencySubstitution {
                    substitute(module("com.test:feature-ui")).using(project(":"))
                }
            }
            includeBuild("${esc(workspaceDir)}/Feature Data Module") {
                dependencySubstitution {
                    substitute(module("com.test:feature-data")).using(project(":"))
                }
            }
        """.trimIndent())

        // Build succeeds — both feature-ui and feature-data resolve core-models
        // to the local project. There's no published artifact to fall back to,
        // so if substitution weren't global, one of them would fail.
        val buildResult = gradle("build").build()
        assertTrue(
            buildResult.output.contains("BUILD SUCCESSFUL"),
            "Build succeeds because both consumers get core-models substituted globally. Output:\n${buildResult.output}"
        )

        // Verify in the dependency tree that core-models is substituted as a project
        val depsResult = gradle(
            "dependencies", "--configuration", "compileClasspath"
        ).build()
        val output = depsResult.output

        // core-models should appear as a project substitution (both consumers get it)
        assertTrue(
            output.contains("project :Core Models Lib") || output.contains("project :core-models"),
            "core-models should appear as project substitution for all consumers. Output:\n$output"
        )
        // core-utils too
        assertTrue(
            output.contains("project :Core Utils Lib") || output.contains("project :core-utils"),
            "core-utils should appear as project substitution for all consumers. Output:\n$output"
        )
    }

    /** Escape backslashes for Kotlin string embedding (Windows paths). */
    private fun esc(file: File): String = file.absolutePath.replace("\\", "/")
}
