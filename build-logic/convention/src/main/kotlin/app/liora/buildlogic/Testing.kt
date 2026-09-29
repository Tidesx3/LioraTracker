package app.liora.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

/** JVM settings for Robolectric-backed host tests. */
internal fun Project.configureHostTests() {
    tasks.withType<Test>().configureEach {
        // Robolectric's SDK 36 runtime reaches into FileDescriptor internals, which JDK 17+ hides by default.
        jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
        maxHeapSize = "2g"
    }
}
