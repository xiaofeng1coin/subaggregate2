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

        // ndk 配置块是 defaultConfig 的一部分，这没有错
        ndk {
            // 指定要为哪些 CPU 架构构建 Python 环境
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    } // <-- defaultConfig 的大括号在这里结束

    // [关键修正] chaquopy 配置块必须和 defaultConfig 是同级兄弟，不能放在其内部
    chaquopy {
        // 指定 Python 版本，务必与 GitHub Actions 中 setup-python 的版本一致
        version = "3.11"
        
        // 在这里指定你的 Python 依赖 (这些会随 APK 一起打包)
        pip {
            install("Flask")
            install("PyYAML")
            install("requests")
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

    // [关键] Chaquopy 需要知道 Python 源码的位置。
    // 这个配置块也必须是 android 的直接子级。
    sourceSets {
        getByName("main") {
            python {
                // GitHub Actions 会在构建前将 src_py/* 复制到这里
                srcDir("src/main/python")
            }
        }
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
