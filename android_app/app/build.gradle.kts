// 文件路径: android_app/app/build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.example.subaggregator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.subaggregator"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    // 启用 View Binding
    buildFeatures {
        viewBinding = true
    }
}

// ===================================================================
// ==================== Chaquopy 配置块 (已修正) =====================
// ===================================================================
chaquopy {
    // 指定 Python 源码目录
    sourceSets {
        getByName("main") {
            srcDirs("src/main/python")
        }
    }

    // Chaquopy 的默认配置
    defaultConfig {
        // [关键修正]: 所有这些配置项都必须位于 defaultConfig 内部

        // (必需) 指定要打包到 APP 中的 Python 版本。
        version = "3.11.5"

        // (必需) 告诉 Chaquopy 在构建时使用哪个 Python 可执行文件。
        // 可以是 "python", "python3", "py", 或一个绝对路径。
        buildPython("python")

        // (可选) 指定 Python 依赖包。
        pip {
            install("Flask")
            install("PyYAML")
            install("requests")
        }

        // (可选) 指定需要支持的 CPU 架构 (ABI)。
        // 这行代码之前在错误的位置，现在已经移到 defaultConfig 内部。
        abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"))
    }
}


dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    // 如果您在 activity_main.xml 中使用了 ConstraintLayout，请确保添加此依赖
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
