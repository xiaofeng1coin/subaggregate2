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

        // =========================================================================
        // [最终修正]: 将 ABI/CPU架构的配置移至标准的 android.defaultConfig.ndk 块中。
        // 这是最可靠的配置方式，可以解决 `Unresolved reference: abiFilters` 错误。
        // Chaquopy 插件会自动识别并使用这里的配置。
        // =========================================================================
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"))
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
    buildFeatures {
        viewBinding = true
    }
}

// ===================================================================
// ==================== Chaquopy 配置块 ==============================
// ===================================================================
chaquopy {
    sourceSets {
        getByName("main") {
            srcDirs("src/main/python")
        }
    }

    defaultConfig {
        // (必需) Python 版本等其他配置保留在此处。
        version = "3.11.5"
        buildPython("python")

        pip {
            install("Flask")
            install("PyYAML")
            install("requests")
        }
        
        // [最终修正] 已将 abiFilters 配置移至上面的 android.defaultConfig.ndk 块中。
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
