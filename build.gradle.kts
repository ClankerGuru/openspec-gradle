plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("com.gradle.plugin-publish") version "1.3.1"
    id("com.gradleup.nmcp") version "1.4.4"
    id("com.gradleup.nmcp.aggregation") version "1.4.4"
}

group = "zone.clanker"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    website.set("https://github.com/ClankerGuru/openspec-gradle")
    vcsUrl.set("https://github.com/ClankerGuru/openspec-gradle")
    plugins {
        create("openspec") {
            id = "zone.clanker.gradle"
            implementationClass = "zone.clanker.gradle.OpenSpecSettingsPlugin"
            displayName = "OpenSpec Gradle Plugin"
            description = "Settings plugin that generates Markdown skill and command files for AI coding assistants (GitHub Copilot, Claude Code). Auto-applies to root project via init script."
            tags.set(listOf("ai", "copilot", "claude", "openspec", "skills", "prompts", "coding-assistant"))
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

// Don't fail build if signing isn't available
tasks.withType<Sign>().configureEach {
    onlyIf { !project.hasProperty("skipSigning") }
}

nmcpAggregation {
    centralPortal {
        username = providers.gradleProperty("sonatypeUsername").getOrElse("")
        password = providers.gradleProperty("sonatypePassword").getOrElse("")
        publishingType = "USER_MANAGED"
    }
}

dependencies {
    nmcpAggregation(project(":"))
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("OpenSpec Gradle Plugin")
            description.set("Generates Markdown skill and command files for AI coding assistants (GitHub Copilot, Claude Code)")
            url.set("https://github.com/ClankerGuru/openspec-gradle")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("ClankerGuru")
                    name.set("ClankerGuru")
                    url.set("https://github.com/ClankerGuru")
                }
            }
            scm {
                url.set("https://github.com/ClankerGuru/openspec-gradle")
                connection.set("scm:git:https://github.com/ClankerGuru/openspec-gradle.git")
                developerConnection.set("scm:git:git@github.com:ClankerGuru/openspec-gradle.git")
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Inject version into plugin at build time
tasks.named<Copy>("processResources") {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("openspec-gradle.properties") {
        expand(props)
    }
}

// Install init script globally (from the plugin repo itself)
val installInitScript by tasks.registering {
    dependsOn("publishToMavenLocal")
    doLast {
        val gradleHome = gradle.gradleUserHomeDir
        val initDir = File(gradleHome, "init.d")
        initDir.mkdirs()
        val initScript = File(initDir, "openspec.init.gradle.kts")
        initScript.writeText("""
            |// OpenSpec Gradle Init Script
            |// Installed by: ./gradlew installOpenSpecGlobally
            |// To uninstall, delete this file.
            |//
            |// Configure in ~/.gradle/gradle.properties:
            |//   zone.clanker.openspec.agents=github          (default)
            |//   zone.clanker.openspec.agents=github,claude
            |//   zone.clanker.openspec.agents=none
            |
            |initscript {
            |    repositories {
            |        mavenLocal()
            |        mavenCentral()
            |    }
            |    dependencies {
            |        classpath("zone.clanker:openspec-gradle:${project.version}")
            |    }
            |}
            |
            |apply<zone.clanker.gradle.OpenSpecSettingsPlugin>()
        """.trimMargin() + "\n")

        // Ensure default property exists
        val gradleProps = File(gradleHome, "gradle.properties")
        if (!gradleProps.exists() || !gradleProps.readText().contains("zone.clanker.openspec.agents")) {
            gradleProps.appendText("\n# OpenSpec agents: github, claude, none (comma-separated)\nzone.clanker.openspec.agents=github\n")
        }

        logger.lifecycle("OpenSpec: Installed init script to ${initScript.absolutePath}")
        logger.lifecycle("OpenSpec: Configure agents in ~/.gradle/gradle.properties")
        logger.lifecycle("OpenSpec: To uninstall: rm ${initScript.absolutePath}")
    }
}

tasks.register("installGlobal") {
    group = "openspec"
    description = "Publishes to mavenLocal and installs the init script to Gradle user home"
    dependsOn(installInitScript)
}

tasks.register("installOpenSpecGlobally") {
    group = "clanker"
    description = "Publishes to mavenLocal and installs the OpenSpec init script globally"
    dependsOn(installInitScript)
}
