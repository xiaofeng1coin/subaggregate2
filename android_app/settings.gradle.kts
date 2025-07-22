// 文件路径: android_app/settings.gradle.kts

// 这个区块定义了 Gradle 在哪里寻找插件，比如 Chaquopy 和 Android 插件
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// 这个区块定义了项目依赖库的来源
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// 设置项目的根名称
rootProject.name = "SubAggregator"
// 告诉 Gradle，名为 "app" 的模块是这个项目的一部分
include(":app")
