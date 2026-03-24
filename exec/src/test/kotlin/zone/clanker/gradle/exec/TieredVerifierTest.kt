package zone.clanker.gradle.exec

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class BuildVerifierTest {

    // ── VerifyMode ──

    @Test
    fun `fromString parses build`() {
        assertEquals(VerifyMode.BUILD, VerifyMode.fromString("build"))
    }

    @Test
    fun `fromString parses compile`() {
        assertEquals(VerifyMode.COMPILE, VerifyMode.fromString("compile"))
    }

    @Test
    fun `fromString parses off`() {
        assertEquals(VerifyMode.OFF, VerifyMode.fromString("off"))
    }

    @Test
    fun `fromString is case insensitive`() {
        assertEquals(VerifyMode.BUILD, VerifyMode.fromString("BUILD"))
        assertEquals(VerifyMode.COMPILE, VerifyMode.fromString("Compile"))
        assertEquals(VerifyMode.OFF, VerifyMode.fromString("OFF"))
    }

    @Test
    fun `fromString throws on unknown`() {
        val ex = assertThrows<IllegalArgumentException> {
            VerifyMode.fromString("turbo")
        }
        assertTrue(ex.message!!.contains("turbo"))
    }

    @Test
    fun `fromString throws on old tier values`() {
        assertThrows<IllegalArgumentException> { VerifyMode.fromString("test") }
        assertThrows<IllegalArgumentException> { VerifyMode.fromString("full") }
        assertThrows<IllegalArgumentException> { VerifyMode.fromString("auto") }
    }

    // ── VerifyMode value ──

    @Test
    fun `mode value strings`() {
        assertEquals("build", VerifyMode.BUILD.value)
        assertEquals("compile", VerifyMode.COMPILE.value)
        assertEquals("off", VerifyMode.OFF.value)
    }

    // ── Off mode ──

    @Test
    fun `off mode returns success immediately`() {
        val verifier = BuildVerifier(
            projectDir = File("."),
            gradlewPath = "/nonexistent/gradlew",
            mode = VerifyMode.OFF,
        )
        val result = verifier.verify()
        assertTrue(result.success)
        assertEquals(VerifyMode.OFF, result.mode)
        assertEquals(0, result.durationMs)
    }

    @Test
    fun `off mode verifyModules returns success immediately`() {
        val verifier = BuildVerifier(
            projectDir = File("."),
            gradlewPath = "/nonexistent/gradlew",
            mode = VerifyMode.OFF,
        )
        val result = verifier.verifyModules(listOf(":core", ":app"))
        assertTrue(result.success)
        assertEquals(VerifyMode.OFF, result.mode)
    }

    // ── VerifyResult ──

    @Test
    fun `VerifyResult data class`() {
        val result = VerifyResult(
            success = true,
            mode = VerifyMode.BUILD,
            durationMs = 1500,
            message = "Verify (build): build — 1s",
        )
        assertTrue(result.success)
        assertEquals(VerifyMode.BUILD, result.mode)
        assertEquals(1500, result.durationMs)
    }

    @Test
    fun `VerifyResult copy`() {
        val r1 = VerifyResult(true, VerifyMode.BUILD, 100, "ok")
        val r2 = r1.copy(success = false)
        assertTrue(r1.success)
        assertTrue(!r2.success)
    }

    // ── Build mode with bad gradlew ──

    @Test
    fun `build mode fails gracefully with missing gradlew`() {
        val verifier = BuildVerifier(
            projectDir = File("."),
            gradlewPath = "/nonexistent/gradlew",
            mode = VerifyMode.BUILD,
        )
        val result = verifier.verify()
        assertTrue(!result.success)
        assertEquals(VerifyMode.BUILD, result.mode)
    }

    @Test
    fun `compile mode fails gracefully with missing gradlew`() {
        val verifier = BuildVerifier(
            projectDir = File("."),
            gradlewPath = "/nonexistent/gradlew",
            mode = VerifyMode.COMPILE,
        )
        val result = verifier.verify()
        assertTrue(!result.success)
        assertEquals(VerifyMode.COMPILE, result.mode)
    }

    @Test
    fun `verifyModules fails gracefully with missing gradlew`() {
        val verifier = BuildVerifier(
            projectDir = File("."),
            gradlewPath = "/nonexistent/gradlew",
            mode = VerifyMode.BUILD,
        )
        val result = verifier.verifyModules(listOf(":core"))
        assertTrue(!result.success)
    }
}
