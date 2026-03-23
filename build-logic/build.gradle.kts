plugins {
    `kotlin-dsl`
}

dependencies {
    // Make plugins available to convention plugins
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${embeddedKotlinVersion}")
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.36.0")
}
