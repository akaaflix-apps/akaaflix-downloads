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

rootProject.name = "AkaaFlixLauncher"

include(
    ":app",
    ":launcher-core",
    ":remote-navigation",
    ":user-preferences",
    ":device-information",
    ":app-manager",
    ":wallpaper-engine",
    ":home-screen",
    ":customization",
    ":settings",
)
