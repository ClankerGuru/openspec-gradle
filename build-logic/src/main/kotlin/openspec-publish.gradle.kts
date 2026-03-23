import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar

// Publishing convention for internal library submodules.
// Plugin module uses its own GradlePublishPlugin() config instead.
plugins {
    id("com.vanniktech.maven.publish.base")
}

mavenPublishing {
    publishToMavenCentral()
    configure(JavaLibrary(javadocJar = JavadocJar.Empty()))

    pom {
        url.set("https://github.com/ClankerGuru/openspec-gradle")
        inceptionYear.set("2025")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
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
