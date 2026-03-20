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
        google() // includes com.google.ai.edge.litert (LiteRT)
        mavenCentral()
    }
}

rootProject.name = "agendroid"

include(":app")
include(":core:common")
include(":core:data")
include(":core:embeddings")
include(":core:ai")
include(":core:voice")
include(":core:telephony")
include(":feature:sms")
include(":feature:phone")
include(":feature:assistant")
