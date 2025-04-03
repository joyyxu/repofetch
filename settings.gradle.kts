pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Required for the Compose Compiler when using dev builds
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    }
}

rootProject.name = "simplerecipe"
include(":app")
