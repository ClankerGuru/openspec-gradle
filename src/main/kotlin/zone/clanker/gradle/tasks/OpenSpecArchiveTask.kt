package zone.clanker.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File
import java.time.LocalDate

@org.gradle.api.tasks.UntrackedTask(because = "Moves files in project directory")
abstract class OpenSpecArchiveTask : DefaultTask() {

    private var changeName: String = ""

    @Option(option = "name", description = "Change name to archive")
    fun setChangeName(name: String) {
        this.changeName = name
    }

    init {
        group = "openspec"
        description = "Archives a completed change by moving its artifacts from openspec/changes/<name>/ to openspec/changes/archive/<date>-<name>/. Preserves the full history of proposals, specs, and implementation decisions for future reference. Use --name=<change-name> to archive a specific change."
    }

    @TaskAction
    fun archive() {
        val changesRoot = File(project.projectDir, "openspec/changes")
        val name = changeName.ifEmpty {
            throw org.gradle.api.GradleException(
                "Change name required. Use: ./gradlew openspecArchive --name=my-change"
            )
        }

        val changeDir = File(changesRoot, name)
        if (!changeDir.exists()) {
            throw org.gradle.api.GradleException("Change '$name' not found")
        }

        val archiveDir = File(changesRoot, "archive")
        archiveDir.mkdirs()

        val dateName = "${LocalDate.now()}-$name"
        val target = File(archiveDir, dateName)
        if (target.exists()) {
            throw org.gradle.api.GradleException("Archive '$dateName' already exists")
        }

        changeDir.renameTo(target)
        logger.lifecycle("OpenSpec: Archived '$name' to openspec/changes/archive/$dateName/")
    }
}
