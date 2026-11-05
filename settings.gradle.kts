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
    // без FAIL_ON_PROJECT_REPOS: Kotlin/Wasm сам добавляет репозитории для Node.js и Yarn
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BabyTracker"
include(":shared")
include(":androidApp")
include(":webApp")
