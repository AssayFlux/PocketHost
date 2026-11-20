pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories {
        // Chaquopy publishes its Python runtime artifacts in this Maven repository.
        maven { url = uri("https://chaquo.com/maven") }
        google()
        mavenCentral()
    } }
rootProject.name = "PocketHost"
include(":app", ":runtime-worker")
