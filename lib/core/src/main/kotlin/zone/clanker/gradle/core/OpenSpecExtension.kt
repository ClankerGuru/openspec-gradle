package zone.clanker.gradle.core

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import javax.inject.Inject

abstract class OpenSpecExtension @Inject constructor(objects: ObjectFactory) {

    val tools: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(listOf("copilot"))
}
