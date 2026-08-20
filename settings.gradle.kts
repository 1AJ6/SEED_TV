pluginManagement {
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SayerTV Mobile"
include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:jellyfin")
include(":core:playback")
include(":core:anilist")
include(":core:matching")
include(":feature:onboarding")
include(":feature:library")
include(":feature:player")
include(":feature:syncplay")
include(":feature:anilist")
