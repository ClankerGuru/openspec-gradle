import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar

// Publishing convention for internal library submodules.
// Plugin module uses its own GradlePublishPlugin() config instead.
// SONATYPE_HOST comes from root gradle.properties.
// POM_ARTIFACT_ID, POM_NAME, POM_DESCRIPTION come from per-module gradle.properties.
plugins {
    id("com.vanniktech.maven.publish.base")
}

mavenPublishing {
    configure(JavaLibrary(javadocJar = JavadocJar.Empty()))

    coordinates(
        groupId = project.property("GROUP").toString(),
        artifactId = project.property("POM_ARTIFACT_ID").toString(),
        version = project.version.toString(),
    )

    pom {
        name.set(project.property("POM_NAME").toString())
        description.set(project.property("POM_DESCRIPTION").toString())
        url.set(project.property("POM_URL").toString())
        inceptionYear.set(project.property("POM_INCEPTION_YEAR").toString())

        licenses {
            license {
                name.set(project.property("POM_LICENSE_NAME").toString())
                url.set(project.property("POM_LICENSE_URL").toString())
                distribution.set(project.property("POM_LICENSE_DIST").toString())
            }
        }
        developers {
            developer {
                id.set(project.property("POM_DEVELOPER_ID").toString())
                name.set(project.property("POM_DEVELOPER_NAME").toString())
                url.set(project.property("POM_DEVELOPER_URL").toString())
            }
        }
        scm {
            url.set(project.property("POM_SCM_URL").toString())
            connection.set(project.property("POM_SCM_CONNECTION").toString())
            developerConnection.set(project.property("POM_SCM_DEV_CONNECTION").toString())
        }
    }
}
