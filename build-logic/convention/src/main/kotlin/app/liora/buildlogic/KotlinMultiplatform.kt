package app.liora.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Shared KMP setup. Every library targets Android; pure-Kotlin modules also target the JVM so the
 * future Ktor server can reuse them and their tests run fast without Robolectric.
 */
internal fun Project.configureKotlinMultiplatform(includeJvm: Boolean) {
    extensions.configure<KotlinMultiplatformExtension> {
        lioraAndroid {
            namespace = lioraNamespace
            compileSdk = LioraConfig.COMPILE_SDK
            minSdk = LioraConfig.MIN_SDK
            compilerOptions {
                jvmTarget.set(LioraConfig.jvmTarget)
            }
        }

        if (includeJvm) {
            jvm {
                compilerOptions {
                    jvmTarget.set(LioraConfig.jvmTarget)
                }
            }
        }

        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }

        sourceSets.commonMain.dependencies {
            implementation(project.dependencies.platform(libs.lib("koin-bom")))
            implementation(libs.lib("koin-core"))
            implementation(libs.lib("kotlinx-coroutines-core"))
        }
        sourceSets.commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.lib("kotlinx-coroutines-test"))
            implementation(libs.lib("turbine"))
        }
    }
}

/** The `kotlin { android { } }` block contributed by the com.android.kotlin.multiplatform.library plugin. */
internal fun KotlinMultiplatformExtension.lioraAndroid(configure: KotlinMultiplatformAndroidLibraryTarget.() -> Unit) {
    (this as ExtensionAware).extensions.configure("android", configure)
}
