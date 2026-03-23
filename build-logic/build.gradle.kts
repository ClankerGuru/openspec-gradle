plugins {
    `kotlin-dsl`
}

dependencies {
    // Make the Kotlin Gradle plugin available to convention plugins
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${embeddedKotlinVersion}")
}
