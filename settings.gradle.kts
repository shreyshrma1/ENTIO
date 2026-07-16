pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "entio"

include(
    "core-types",
    "semantic-engine",
    "validation-engine",
    "graph-diff",
    "cli",
    "shared",
    "web-server",
)
