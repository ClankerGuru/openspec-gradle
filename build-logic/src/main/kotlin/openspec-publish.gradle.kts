import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar

// Publishing convention for internal library submodules.
// Plugin module uses its own GradlePublishPlugin() config instead.
// All POM metadata comes from gradle.properties (root shared + per-module overrides).
// The full vanniktech plugin auto-reads: GROUP, POM_ARTIFACT_ID, POM_NAME,
// POM_DESCRIPTION, POM_URL, POM_SCM_*, POM_LICENSE_*, POM_DEVELOPER_*, SONATYPE_HOST.
plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    configure(JavaLibrary(javadocJar = JavadocJar.Empty()))
}
