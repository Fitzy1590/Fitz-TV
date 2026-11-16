pluginManagement {
    repositories {
        google()
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
rootProject.name = "FitzTV"
if (providers.gradleProperty("androidOnly").orNull != "true") include(":desktop")
// The Android modules need an Android SDK; the Windows build (CI "windows" job) runs without one.
if (providers.gradleProperty("desktopOnly").orNull != "true") include(":core", ":app", ":mobile")
