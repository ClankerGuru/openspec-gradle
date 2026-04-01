package zone.clanker.gradle.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class VersionInfoTest {

    @Test
    fun `PLUGIN_VERSION is not the fallback value`() {
        assertNotEquals("0.0.0", VersionInfo.PLUGIN_VERSION,
            "PLUGIN_VERSION should not be the fallback '0.0.0' — " +
            "openspec-gradle.properties was not expanded or is missing")
    }

    @Test
    fun `PLUGIN_VERSION matches semver pattern`() {
        val semver = Regex("""^\d+\.\d+\.\d+(-[A-Za-z0-9.+-]+)?$""")
        assertTrue(semver.matches(VersionInfo.PLUGIN_VERSION),
            "PLUGIN_VERSION '${VersionInfo.PLUGIN_VERSION}' does not match semver pattern " +
            "(expected digits.digits.digits with optional suffix like -SNAPSHOT or -LOCAL)")
    }
}
