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
        group = "opsx"
        description = "[tool] Archive a completed change to opsx/changes/archive/. " +
            "Options: --name=<change-name>. " +
            "Use when: A proposal is fully implemented."
    }

    @TaskAction
    fun archive() {
        val changesRoot = File(project.projectDir, "opsx/changes")
        val name = changeName.ifEmpty {
            throw org.gradle.api.GradleException(
                "Change name required. Use: ./gradlew opsx-archive --name=my-change"
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
        logger.lifecycle("OpenSpec: Archived '$name' to opsx/changes/archive/$dateName/")
    }
}
