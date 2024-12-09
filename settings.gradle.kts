pluginManagement {
    repositories {
        maven { url=uri("https://maven.aliyun.com/repository/public")}
        maven { url=uri("https://maven.aliyun.com/repository/google")}
        maven { url=uri("https://maven.aliyun.com/repository/central")}
        maven { url=uri("https://maven.aliyun.com/repository/gradle-plugin")}
        maven { url=uri("https://plugins.gradle.org/m2/")}
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url=uri("https://www.jitpack.io")}
        maven { url=uri("https://maven.aliyun.com/repository/releases")}
        maven { url=uri("https://maven.aliyun.com/repository/google")}
        maven { url=uri("https://maven.aliyun.com/repository/central")}
        maven { url=uri("https://maven.aliyun.com/repository/gradle-plugin")}
        maven { url=uri("https://maven.aliyun.com/repository/public")}
        maven { url=uri("https://plugins.gradle.org/m2/")}
        google()
        mavenCentral()
    }
}

rootProject.name = "ANetHack"
include(":app")