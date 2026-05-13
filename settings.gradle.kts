pluginManagement {
    repositories {
        // Prefer mirror when dl.google.com fails (TLS / network). Remove these lines if you are not in a restricted network.
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}
rootProject.name = "MobileBot"

include(":app")
include(":core:model")
include(":core:bus")
include(":core:network")
include(":core:bridge")
include(":core:domain")
include(":core:data")
include(":core:systemruntime")
include(":feature:chat")
