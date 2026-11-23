pluginManagement {
    repositories {
        google()
        mavenCentral()
        // gradlePluginPortal() is deliberately absent: every plugin this build needs ships on
        // google() or mavenCentral(), and the portal sits behind an endpoint that stalls here.
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "terramap-android"

include(":core")
include(":app")
