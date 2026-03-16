package zone.clanker.gradle

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionTest {

    @Test
    fun `plugin version is not a placeholder`() {
        val version = OpenSpecSettingsPlugin.PLUGIN_VERSION
        assertFalse(version == "0.0.0", "Plugin version should not be the fallback 0.0.0")
        assertFalse(version.isBlank(), "Plugin version should not be blank")
        assertTrue(version.matches(Regex("""\d+\.\d+\.\d+.*""")), "Plugin version should be semver-like, got: $version")
    }

    @Test
    fun `plugin version matches properties file`() {
        val propsVersion = this::class.java.classLoader
            .getResourceAsStream("openspec-gradle.properties")
            ?.let { java.util.Properties().apply { load(it) }.getProperty("version") }
        assertFalse(propsVersion.isNullOrBlank(), "openspec-gradle.properties should contain version")
        assertTrue(propsVersion!!.matches(Regex("""\d+\.\d+\.\d+.*""")), "Properties version should be semver-like, got: $propsVersion")
        assertTrue(propsVersion == OpenSpecSettingsPlugin.PLUGIN_VERSION, "PLUGIN_VERSION (${ OpenSpecSettingsPlugin.PLUGIN_VERSION}) should match properties ($propsVersion)")
    }
}
