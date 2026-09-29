pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "liora"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app:android")

include(":core:model")
include(":core:common")
include(":core:domain")
include(":core:database")
include(":core:data")
include(":core:navigation")
include(":core:designsystem")

include(":feature:train")
include(":feature:logger")
include(":feature:history")
include(":feature:exercises")
include(":feature:progress")
include(":feature:body")
include(":feature:settings")
