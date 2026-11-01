pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Espelhos do Maven Central (Task 121): o repo1.maven.org limita por
        // IP (429) e o Kotlin 2.4.10 + Compose do LastWave precisam de
        // milhares de POMs numa build fria — espelho primeiro, central de
        // reserva. Em ambiente normal os espelhos são transparentes.
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "TuneGrab"
include(":app")
