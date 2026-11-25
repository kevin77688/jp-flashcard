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
        // VOICEVOX's Android library, downloaded by tools/fetch_voicevox.sh.
        maven { url = uri("third_party/voicevox/maven") }
    }
}

rootProject.name = "jp-flashcard"
include(":app")
