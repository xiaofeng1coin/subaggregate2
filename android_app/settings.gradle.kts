// 文件路径: android_app/settings.gradle.kts

pluginManagement {
    // [关键修正] 在这里集中定义所有插件的版本号
    plugins {
        // Android Application Plugin (AGP) - 需要版本号
        id("com.android.application") version "8.3.2" apply false
        // Kotlin Android Plugin - 需要版本号
        id("org.jetbrains.kotlin.android") version "1.9.22" apply false
        // Chaquopy Plugin - 需要版本号
        id("com.chaquo.python") version "15.0.1" apply false
    }
    // 这些是插件本身的下载仓库
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    // 这些是项目依赖库（如 androidx, flask）的下载仓库
    repositories {
        google()
        mavenCentral()
        // [关键补充] 添加 Chaquopy 的仓库，用于下载 Python 运行时和库
        maven { url = uri("https://chaquo.com/maven") }
    }
}

rootProject.name = "SubAggregator"
// 根项目本身就是应用，不需要 include
