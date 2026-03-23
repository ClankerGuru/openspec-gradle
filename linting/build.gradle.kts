plugins {
    id("openspec-module")
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    compileOnly(libs.detekt.gradle)
    compileOnly(libs.ktlint.gradle)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    plugins {
        register("openspec-detekt") {
            id = "zone.clanker.gradle.detekt"
            implementationClass = "zone.clanker.gradle.linting.OpenSpecDetektPlugin"
            displayName = "OpenSpec Detekt Plugin"
            description = "Auto-applies detekt if not already configured. Disable with -Dopenspec.detekt.enabled=false"
        }
        register("openspec-ktlint") {
            id = "zone.clanker.gradle.ktlint"
            implementationClass = "zone.clanker.gradle.linting.OpenSpecKtlintPlugin"
            displayName = "OpenSpec Ktlint Plugin"
            description = "Auto-applies ktlint if not already configured. Disable with -Dopenspec.ktlint.enabled=false"
        }
        register("openspec-linting") {
            id = "zone.clanker.gradle.linting"
            implementationClass = "zone.clanker.gradle.linting.OpenSpecLintingPlugin"
            displayName = "OpenSpec Linting Plugin"
            description = "Applies both detekt and ktlint. Disable individually with system properties."
        }
    }
}
