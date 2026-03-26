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

class ReposTaskTest {

    @TempDir
    lateinit var testProjectDir: File

    @TempDir
    lateinit var workspaceDir: File

    private fun gradle(vararg args: String) = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments(*args)

    private fun outputFile() = File(testProjectDir, ".opsx/repos.md")

    @BeforeEach
    fun setup() {
        File(testProjectDir, "build.gradle.kts").writeText("")
    }

    private fun writeSettings(extra: String = "") {
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.monolith")
            }
            $extra
        """.trimIndent())
    }

    private fun writeConfig(json: String): File {
        val configFile = File(testProjectDir, "monolith.json")
        configFile.writeText(json)
        return configFile
    }

    private fun runRepos(configFile: File, monolithDir: File = workspaceDir): org.gradle.testkit.runner.BuildResult {
        return gradle(
            "opsx-repos",
            "-Pzone.clanker.openspec.monolithFile=${configFile.absolutePath}",
            "-Pzone.clanker.openspec.monolithDir=${monolithDir.absolutePath}"
        ).build()
    }

    // --- Registration ---

    @Test
    fun `opsx-repos task is registered`() {
        writeSettings()
        val result = gradle("tasks", "--group=opsx").build()
        assertTrue(result.output.contains("opsx-repos"), "Expected opsx-repos in task list")
    }

    // --- Basic output ---

    @Test
    fun `outputs repos from monolith json`() {
        val configFile = writeConfig("""
            [
              {"name": "MyOrg/core-lib", "enable": true, "category": "core", "substitutions": []},
              {"name": "MyOrg/app", "enable": true, "category": "apps", "substitutions": []}
            ]
        """.trimIndent())
        writeSettings()

        val result = runRepos(configFile)
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-repos")?.outcome)

        val output = outputFile().readText()
        assertTrue(output.contains("MyOrg/core-lib"), "Expected core-lib in output")
        assertTrue(output.contains("MyOrg/app"), "Expected app in output")
        assertTrue(output.contains("2 repos"), "Expected repo count")
    }

    @Test
    fun `output file is written to opsx directory`() {
        val configFile = writeConfig("""
            [{"name": "MyOrg/repo", "enable": true, "category": "libs", "substitutions": []}]
        """.trimIndent())
        writeSettings()

        runRepos(configFile)
        assertTrue(outputFile().exists(), "Expected .opsx/repos.md to exist")
        assertTrue(outputFile().readText().startsWith("# Monolith Repos"), "Expected markdown header")
    }

    // --- Enabled/disabled ---

    @Test
    fun `shows enabled and disabled status`() {
        val configFile = writeConfig("""
            [
              {"name": "MyOrg/enabled-repo", "enable": true, "category": "libs", "substitutions": []},
              {"name": "MyOrg/disabled-repo", "enable": false, "category": "libs", "substitutions": []}
            ]
        """.trimIndent())
        writeSettings()

        runRepos(configFile)
        val output = outputFile().readText()
        // Table should have both
        assertTrue(output.contains("MyOrg/enabled-repo"), "Expected enabled repo")
        assertTrue(output.contains("MyOrg/disabled-repo"), "Expected disabled repo")
        // Summary should count 1 enabled
        assertTrue(output.contains("1 enabled"), "Expected 1 enabled in summary")
    }

    // --- Cloned on disk ---

    @Test
    fun `detects repos cloned on disk`() {
        val monolithDir = File(workspaceDir, "mono")
        // Create directory for one repo, not the other
        File(monolithDir, "cloned-repo").mkdirs()

        val configFile = writeConfig("""
            [
              {"name": "MyOrg/cloned-repo", "enable": true, "category": "libs", "substitutions": []},
              {"name": "MyOrg/missing-repo", "enable": true, "category": "libs", "substitutions": []}
            ]
        """.trimIndent())
        writeSettings()

        runRepos(configFile, monolithDir)
        val output = outputFile().readText()
        // Summary should say 1 cloned
        assertTrue(output.contains("1 cloned on disk"), "Expected 1 cloned on disk. Output:\n$output")
    }

    @Test
    fun `shows cloned yes or no in table`() {
        val monolithDir = File(workspaceDir, "mono")
        File(monolithDir, "on-disk").mkdirs()

        val configFile = writeConfig("""
            [
              {"name": "MyOrg/on-disk", "enable": true, "category": "libs", "substitutions": []},
              {"name": "MyOrg/not-on-disk", "enable": true, "category": "libs", "substitutions": []}
            ]
        """.trimIndent())
        writeSettings()

        runRepos(configFile, monolithDir)
        val output = outputFile().readText()
        // The table rows — on-disk should have "yes" in Cloned column, not-on-disk should have "no"
        val lines = output.lines()
        val onDiskRow = lines.find { it.contains("MyOrg/on-disk") }
        val missingRow = lines.find { it.contains("MyOrg/not-on-disk") }
        assertTrue(onDiskRow != null, "Expected row for on-disk repo")
        assertTrue(missingRow != null, "Expected row for not-on-disk repo")
        // Count "yes" occurrences — on-disk should have more yes columns
        val onDiskYesCount = onDiskRow!!.split("|").count { it.trim() == "yes" }
        val missingYesCount = missingRow!!.split("|").count { it.trim() == "yes" }
        assertTrue(onDiskYesCount > missingYesCount, "on-disk repo should have more 'yes' fields")
    }

    // --- Substitution ---

    @Test
    fun `shows substitution config`() {
        val configFile = writeConfig("""
            [
              {
                "name": "MyOrg/subbed-lib",
                "enable": true,
                "category": "core",
                "substitute": true,
                "substitutions": ["com.example:core-lib,:"]
              },
              {
                "name": "MyOrg/no-sub-lib",
                "enable": true,
                "category": "core",
                "substitute": false,
                "substitutions": []
              }
            ]
        """.trimIndent())
        writeSettings()

        runRepos(configFile)
        val output = outputFile().readText()
        assertTrue(output.contains("1 with substitution"), "Expected 1 with substitution. Output:\n$output")
        assertTrue(output.contains("com.example:core-lib,:"), "Expected substitution artifact in output")
    }

    // --- Categories ---

    @Test
    fun `groups repos by category`() {
        val configFile = writeConfig("""
            [
              {"name": "MyOrg/core-one", "enable": true, "category": "core", "substitutions": []},
              {"name": "MyOrg/core-two", "enable": true, "category": "core", "substitutions": []},
              {"name": "MyOrg/app-one", "enable": true, "category": "apps", "substitutions": []},
              {"name": "MyOrg/feature-x", "enable": true, "category": "features", "substitutions": []}
            ]
        """.trimIndent())
        writeSettings()

        runRepos(configFile)
        val output = outputFile().readText()
        assertTrue(output.contains("### core"), "Expected core category heading")
        assertTrue(output.contains("### apps"), "Expected apps category heading")
        assertTrue(output.contains("### features"), "Expected features category heading")
    }

    @Test
    fun `repos with blank category go to default`() {
        val configFile = writeConfig("""
            [
              {"name": "MyOrg/no-category", "enable": true, "category": "", "substitutions": []}
            ]
        """.trimIndent())
        writeSettings()

        runRepos(configFile)
        val output = outputFile().readText()
        assertTrue(output.contains("default"), "Expected 'default' category for blank category")
    }

    // --- Machine-readable section ---

    @Test
    fun `machine-readable section contains all repos`() {
        val configFile = writeConfig("""
            [
              {"name": "MyOrg/alpha", "enable": true, "category": "libs", "substitutions": ["com.x:alpha,:"], "substitute": true, "ref": "develop"},
              {"name": "MyOrg/beta", "enable": false, "category": "apps", "substitutions": [], "ref": "main"}
            ]
        """.trimIndent())
        writeSettings()

        runRepos(configFile)
        val output = outputFile().readText()

        // Find the machine-readable block
        val codeBlock = output.substringAfter("```\nrepo=").substringBefore("\n```")
        val machineLines = ("repo=" + codeBlock).lines()
        assertEquals(2, machineLines.size, "Expected 2 machine-readable lines. Got:\n${machineLines.joinToString("\n")}")

        val alphaLine = machineLines.find { it.contains("MyOrg/alpha") }!!
        assertTrue(alphaLine.contains("enabled=true"), "Expected enabled=true for alpha")
        assertTrue(alphaLine.contains("substitute=true"), "Expected substitute=true for alpha")
        assertTrue(alphaLine.contains("ref=develop"), "Expected ref=develop for alpha")
        assertTrue(alphaLine.contains("substitutions=[com.x:alpha,:]"), "Expected substitution in machine-readable")

        val betaLine = machineLines.find { it.contains("MyOrg/beta") }!!
        assertTrue(betaLine.contains("enabled=false"), "Expected enabled=false for beta")
    }

    // --- Ref / branch ---

    @Test
    fun `shows ref for each repo`() {
        val configFile = writeConfig("""
            [
              {"name": "MyOrg/main-repo", "enable": true, "category": "libs", "substitutions": [], "ref": "main"},
              {"name": "MyOrg/feature-repo", "enable": true, "category": "libs", "substitutions": [], "ref": "feature-xyz"}
            ]
        """.trimIndent())
        writeSettings()

        runRepos(configFile)
        val output = outputFile().readText()
        assertTrue(output.contains("feature-xyz"), "Expected feature-xyz ref in output")
        assertTrue(output.contains("`main`"), "Expected main ref in output")
    }

    // --- Empty / edge cases ---

    @Test
    fun `handles empty monolith json`() {
        val configFile = writeConfig("[]")
        writeSettings()

        val result = runRepos(configFile)
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-repos")?.outcome)
        val output = outputFile().readText()
        assertTrue(output.contains("No repos configured"), "Expected empty message. Output:\n$output")
    }

    @Test
    fun `handles missing monolith json gracefully`() {
        writeSettings()
        // Don't create monolith.json — plugin reads it at settings time, returns empty list
        val missingFile = File(testProjectDir, "nonexistent.json")

        val result = gradle(
            "opsx-repos",
            "-Pzone.clanker.openspec.monolithFile=${missingFile.absolutePath}",
            "-Pzone.clanker.openspec.monolithDir=${workspaceDir.absolutePath}"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-repos")?.outcome)
        val output = outputFile().readText()
        assertTrue(output.contains("No repos configured"), "Expected empty message for missing file")
    }

    // --- Many repos (stress) ---

    @Test
    fun `handles 50 repos across multiple categories`() {
        val repos = (1..50).map { i ->
            val category = when {
                i <= 10 -> "core"
                i <= 25 -> "features"
                i <= 40 -> "apps"
                else -> "infra"
            }
            """{"name": "MyOrg/repo-$i", "enable": ${i % 3 != 0}, "category": "$category", "substitutions": []}"""
        }
        val configFile = writeConfig("[${repos.joinToString(",\n")}]")
        writeSettings()

        // Create some dirs on disk to simulate cloned repos
        (1..20).forEach { i ->
            File(workspaceDir, "repo-$i").mkdirs()
        }

        val result = runRepos(configFile)
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-repos")?.outcome)

        val output = outputFile().readText()
        assertTrue(output.contains("50 repos"), "Expected 50 repos in summary")
        assertTrue(output.contains("20 cloned on disk"), "Expected 20 cloned. Output:\n$output")
    }

    // --- DSL overrides ---

    @Test
    fun `reflects DSL overrides from settings`() {
        val configFile = writeConfig("""
            [
              {"name": "MyOrg/toggled-repo", "enable": false, "category": "libs", "substitutions": ["com.x:toggled,:"], "substitute": false, "ref": "main"}
            ]
        """.trimIndent())

        // Use DSL to override enable and substitute
        File(testProjectDir, "settings.gradle.kts").writeText("""
            plugins {
                id("zone.clanker.monolith")
            }
            monolith {
                toggledRepo.enable(true)
                toggledRepo.substitute(true)
                toggledRepo.ref("feature-override")
            }
        """.trimIndent())

        val result = gradle(
            "opsx-repos",
            "-Pzone.clanker.openspec.monolithFile=${configFile.absolutePath}",
            "-Pzone.clanker.openspec.monolithDir=${workspaceDir.absolutePath}"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-repos")?.outcome)
        val output = outputFile().readText()

        // Should reflect the DSL overrides, not JSON defaults
        assertTrue(output.contains("1 enabled"), "Expected repo to be enabled via DSL. Output:\n$output")
        assertTrue(output.contains("1 with substitution"), "Expected substitution enabled via DSL. Output:\n$output")
        assertTrue(output.contains("feature-override"), "Expected overridden ref. Output:\n$output")
    }

    // --- Special characters in repo names ---

    @Test
    fun `handles repos with dots and underscores in names`() {
        val configFile = writeConfig("""
            [
              {"name": "MyOrg/my.dotted.repo", "enable": true, "category": "libs", "substitutions": []},
              {"name": "MyOrg/my_underscored_repo", "enable": true, "category": "libs", "substitutions": []}
            ]
        """.trimIndent())
        writeSettings()

        val result = runRepos(configFile)
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-repos")?.outcome)

        val output = outputFile().readText()
        assertTrue(output.contains("my.dotted.repo"), "Expected dotted repo name")
        assertTrue(output.contains("my_underscored_repo"), "Expected underscored repo name")
    }

    @Test
    fun `handles SSH-style repo names`() {
        val configFile = writeConfig("""
            [
              {"name": "git@github.com:MyOrg/ssh-repo.git", "enable": true, "category": "libs", "substitutions": []}
            ]
        """.trimIndent())
        writeSettings()

        val result = runRepos(configFile)
        assertEquals(TaskOutcome.SUCCESS, result.task(":opsx-repos")?.outcome)

        val output = outputFile().readText()
        assertTrue(output.contains("git@github.com:MyOrg/ssh-repo.git"), "Expected SSH repo name in output")
    }

    // --- Multiple substitutions per repo ---

    @Test
    fun `shows multiple substitutions per repo`() {
        val configFile = writeConfig("""
            [
              {
                "name": "MyOrg/multi-module",
                "enable": true,
                "category": "core",
                "substitute": true,
                "substitutions": ["com.x:module-a,:module-a", "com.x:module-b,:module-b", "com.x:module-c,:module-c"]
              }
            ]
        """.trimIndent())
        writeSettings()

        runRepos(configFile)
        val output = outputFile().readText()
        assertTrue(output.contains("com.x:module-a,:module-a"), "Expected module-a substitution")
        assertTrue(output.contains("com.x:module-b,:module-b"), "Expected module-b substitution")
        assertTrue(output.contains("com.x:module-c,:module-c"), "Expected module-c substitution")
    }
}
