package zone.clanker.gradle.tasks.workflow

import zone.clanker.gradle.tasks.OPSX_GROUP

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File
import java.time.LocalDate

@org.gradle.api.tasks.UntrackedTask(because = "Moves files in project directory")
abstract class ArchiveTask : DefaultTask() {

    private var changeName: String = ""

    @Option(option = "name", description = "Change name to archive")
    fun setChangeName(name: String) {
        this.changeName = name
    }

    init {
        group = OPSX_GROUP
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

        if (!changeDir.renameTo(target)) {
            throw org.gradle.api.GradleException("Failed to archive '$name' — could not move to opsx/changes/archive/$dateName/")
        }
        logger.lifecycle("OpenSpec: Archived '$name' to opsx/changes/archive/$dateName/")
    }
}
