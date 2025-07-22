// 文件路径: android_app/build.gradle.kts

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    // [关键添加] 在项目级别声明 Chaquopy 插件
    id("com.chaquo.python") version "15.0.1" apply false
}
