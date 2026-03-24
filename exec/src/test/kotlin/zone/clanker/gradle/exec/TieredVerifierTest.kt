package zone.clanker.gradle.exec

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

class TieredVerifierTest {

    // ── VerifyMode ──

    @Test
    fun `fromString parses compile`() {
        assertEquals(VerifyMode.COMPILE, VerifyMode.fromString("compile"))
    }

    @Test
    fun `fromString parses test`() {
        assertEquals(VerifyMode.TEST, VerifyMode.fromString("test"))
    }

    @Test
    fun `fromString parses full`() {
        assertEquals(VerifyMode.FULL, VerifyMode.fromString("full"))
    }

    @Test
    fun `fromString parses auto`() {
        assertEquals(VerifyMode.AUTO, VerifyMode.fromString("auto"))
    }

    @Test
    fun `fromString is case insensitive`() {
        assertEquals(VerifyMode.COMPILE, VerifyMode.fromString("COMPILE"))
        assertEquals(VerifyMode.AUTO, VerifyMode.fromString("Auto"))
    }

    @Test
    fun `fromString throws on unknown`() {
        val ex = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            VerifyMode.fromString("turbo")
        }
        assertTrue(ex.message!!.contains("turbo"))
    }

    // ── Android detection ──

    @Test
    fun `isAndroidProject detects android application plugin`(@TempDir dir: File) {
        File(dir, "build.gradle.kts").writeText("""
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }
            android {
                compileSdk = 34
            }
        """.trimIndent())
        assertTrue(TieredVerifier.isAndroidProject(dir))
    }

    @Test
    fun `isAndroidProject detects android library plugin`(@TempDir dir: File) {
        File(dir, "build.gradle.kts").writeText("""
            plugins {
                id("com.android.library")
            }
        """.trimIndent())
        assertTrue(TieredVerifier.isAndroidProject(dir))
    }

    @Test
    fun `isAndroidProject returns false for plain kotlin`(@TempDir dir: File) {
        File(dir, "build.gradle.kts").writeText("""
            plugins {
                kotlin("jvm")
            }
        """.trimIndent())
        assertFalse(TieredVerifier.isAndroidProject(dir))
    }

    @Test
    fun `isAndroidProject returns false when no build file`(@TempDir dir: File) {
        assertFalse(TieredVerifier.isAndroidProject(dir))
    }

    @Test
    fun `isAndroidProject detects groovy build file`(@TempDir dir: File) {
        File(dir, "build.gradle").writeText("""
            apply plugin: 'com.android.application'
        """.trimIndent())
        assertTrue(TieredVerifier.isAndroidProject(dir))
    }

    // ── Task resolution ──

    @Test
    fun `resolveCompileTasks for kotlin project`(@TempDir dir: File) {
        File(dir, "build.gradle.kts").writeText("plugins { kotlin(\"jvm\") }")
        assertEquals(listOf("compileKotlin"), TieredVerifier.resolveCompileTasks(dir))
    }

    @Test
    fun `resolveCompileTasks for android project`(@TempDir dir: File) {
        File(dir, "build.gradle.kts").writeText("plugins { id(\"com.android.application\") }")
        assertEquals(listOf("compileDebugKotlin"), TieredVerifier.resolveCompileTasks(dir))
    }

    @Test
    fun `resolveTestTasks for kotlin project`(@TempDir dir: File) {
        File(dir, "build.gradle.kts").writeText("plugins { kotlin(\"jvm\") }")
        assertEquals(listOf("test"), TieredVerifier.resolveTestTasks(dir))
    }

    @Test
    fun `resolveTestTasks for android project`(@TempDir dir: File) {
        File(dir, "build.gradle.kts").writeText("plugins { id(\"com.android.library\") }")
        assertEquals(listOf("testDebugUnitTest"), TieredVerifier.resolveTestTasks(dir))
    }

    // ── Batch boundary ──

    @Test
    fun `isBatchBoundary at multiples of batchSize`() {
        val verifier = TieredVerifier(
            projectDir = File("."),
            gradlewPath = "/fake/gradlew",
            mode = VerifyMode.COMPILE,
            batchSize = 3,
        )
        // taskCount starts at 0, increments in verifyAfterTask
        // We can't easily call verifyAfterTask without a real gradlew,
        // so test the boundary detection directly
        assertFalse(verifier.isBatchBoundary()) // 0 → false
    }

    @Test
    fun `resetBatchCounter resets to zero`() {
        val verifier = TieredVerifier(
            projectDir = File("."),
            gradlewPath = "/fake/gradlew",
            mode = VerifyMode.COMPILE,
        )
        verifier.resetBatchCounter()
        assertEquals(0, verifier.currentTaskCount)
    }

    // ── VerifyResult ──

    @Test
    fun `VerifyResult data class`() {
        val result = VerifyResult(
            success = true,
            tier = VerifyMode.COMPILE,
            durationMs = 1500,
            message = "Tier 1 (compile): compileKotlin",
        )
        assertTrue(result.success)
        assertEquals(VerifyMode.COMPILE, result.tier)
        assertEquals(1500, result.durationMs)
    }

    // ── Mode defaults ──

    @Test
    fun `effectivePerTaskMode returns mode when not auto`() {
        val verifier = TieredVerifier(
            projectDir = File("."),
            gradlewPath = "/fake/gradlew",
            mode = VerifyMode.COMPILE,
        )
        assertEquals(VerifyMode.COMPILE, verifier.effectivePerTaskMode)
    }

    @Test
    fun `effectivePerTaskMode returns AUTO before calibration`() {
        val verifier = TieredVerifier(
            projectDir = File("."),
            gradlewPath = "/fake/gradlew",
            mode = VerifyMode.AUTO,
        )
        assertEquals(VerifyMode.AUTO, verifier.effectivePerTaskMode)
    }
}
