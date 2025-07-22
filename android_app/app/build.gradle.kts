// 文件路径: android_app/app/build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // [关键添加] 引入 Chaquopy 插件
    id("com.chaquo.python")
}

android {
    namespace = "com.example.subaggregator" // <-- 请确保这里是您的包名
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.subaggregator" // <-- 请确保这里是您的包名
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // [关键添加] Chaquopy 的配置块
        ndk {
            // 指定要为哪些 CPU 架构构建 Python 环境。这些是最常用的。
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
        chaquopy {
            // Chaquopy 需要知道 Python 源码的位置。
            // GitHub Actions 会在构建前将 src_py/* 复制到这里。
            // 这是“图纸”上的一个关键指令，告诉构建系统去哪里找 python 代码。
            sourceSets {
                getByName("main") {
                    srcDirs("src/main/python")
                }
            }
            // 指定要使用的 Python 版本。
            // 务必与 GitHub Actions (android-build.yml) 中 setup-python 的版本一致。
            version = "3.11"
            // 在这里指定你的 Python 依赖 (这些会随 APK 一起打包)
            // 内容应与根目录的 requirements.txt 保持一致
            pip {
                install("Flask")
                install("PyYAML")
                install("requests")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    // [可选但推荐] 允许在 Kotlin 代码中更方便地访问布局视图
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // 这些是标准的安卓依赖
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
