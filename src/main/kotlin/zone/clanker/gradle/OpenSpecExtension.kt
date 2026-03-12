package zone.clanker.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class OpenSpecExtension @Inject constructor(objects: ObjectFactory) {

    val tools: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(listOf("github-copilot"))

    val profile: Property<String> = objects.property(String::class.java)
        .convention("core")
}
