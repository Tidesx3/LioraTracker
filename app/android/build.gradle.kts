import com.android.build.api.variant.HostTestBuilder
import java.util.Properties

plugins {
    alias(libs.plugins.liora.android.application)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.baselineprofile)
}

// Release signing comes from keystore.properties at the repo root (gitignored, see README).
// Without it, release builds come out unsigned, as on CI.
val releaseSigning =
    providers
        .fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
        .asText
        .map { text -> Properties().apply { load(text.reader()) } }
        .orNull

// The version lives in version.properties, which the release script reads too. The code follows from it,
// major × 10000 + minor × 100 + patch, so a newer version always installs over an older one.
val lioraVersion =
    providers
        .fileContents(rootProject.layout.projectDirectory.file("version.properties"))
        .asText
        .map { text -> Properties().apply { load(text.reader()) }.getProperty("version") }
        .get()
val lioraVersionCode =
    lioraVersion
        .split('.')
        .map(String::toInt)
        .also { require(it.size == 3) { "version.properties: expected major.minor.patch, got $lioraVersion" } }
        .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

android {
    namespace = "app.liora.android"

    defaultConfig {
        applicationId = "app.liora"
        versionCode = lioraVersionCode
        versionName = lioraVersion
        // Where releases are published; the release build checks it for updates (UPDATES_FROM_GITHUB).
        buildConfigField("String", "GITHUB_REPO", "\"Tidesx3/LioraTracker\"")
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Only releases come from GitHub, signed with the release key: a debug build never updates itself.
            buildConfigField("boolean", "UPDATES_FROM_GITHUB", "false")
        }
        release {
            buildConfigField("boolean", "UPDATES_FROM_GITHUB", "true")
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

// The baseline profile is collected on the phone (benchmark/, README › Measuring speed) and committed under
// src/release/generated, so release builds never wait for a device.
baselineProfile {
    automaticGenerationDuringBuild = false
    // Also lays out the dex files so the code startup runs is read first.
    dexLayoutOptimization = true
}

// The plugin adds builds to measure (benchmarkRelease) and to collect the profile from (nonMinifiedRelease),
// copied from the release build. They install as their own app with made-up training (src/benchmark), so
// measuring never touches the real one, are signed with the debug key and never look for updates.
val measuringBuildTypes = setOf("benchmarkRelease", "nonMinifiedRelease")
androidComponents {
    finalizeDsl { android ->
        android.buildTypes.filter { it.name in measuringBuildTypes }.forEach { buildType ->
            buildType.applicationIdSuffix = ".benchmark"
            buildType.versionNameSuffix = "-benchmark"
            buildType.signingConfig = android.signingConfigs.getByName("debug")
            buildType.buildConfigField("boolean", "UPDATES_FROM_GITHUB", "false")
        }
    }
    onVariants(selector().all()) { variant ->
        if (variant.buildType in measuringBuildTypes) {
            variant.sources.kotlin?.addStaticSourceDirectory("src/benchmark/kotlin")
            variant.sources.manifests.addStaticManifestFile("src/benchmark/AndroidManifest.xml")
        }
        // Tested with the debug build's unit tests, the only ones there are (BenchmarkTrainingTest).
        if (variant.buildType == "debug") {
            variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]
                ?.sources
                ?.kotlin
                ?.addStaticSourceDirectory("src/benchmark/kotlin")
        }
    }
}

dependencies {
    implementation(projects.core.model)
    // Ids and HLCs, for the benchmark builds' made-up training (src/benchmark).
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.domain)
    implementation(projects.core.navigation)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)

    implementation(projects.feature.train)
    implementation(projects.feature.logger)
    implementation(projects.feature.history)
    implementation(projects.feature.exercises)
    implementation(projects.feature.progress)
    implementation(projects.feature.body)
    implementation(projects.feature.settings)
    implementation(projects.feature.walkthrough)

    implementation(projects.sync.gdrive)
    implementation(projects.integration.healthconnect)
    implementation(projects.integration.github)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.network.okhttp)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.components.resources)
    implementation(libs.jb.lifecycle.runtime.compose)
    implementation(libs.jb.lifecycle.viewmodel.compose)
    implementation(libs.jb.lifecycle.viewmodel.navigation3)
    implementation(libs.jb.navigation3.ui)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Installs the baseline profile on phones that don't get it from a store, as sideloaded Liora doesn't.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.benchmark)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.core.viewmodel)

    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.sqlite.framework)
    testImplementation(libs.coil.compose)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.window.testing)
    testImplementation(libs.glance.appwidget.testing)
}
