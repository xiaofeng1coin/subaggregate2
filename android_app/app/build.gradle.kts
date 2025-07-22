// 文件路径: android_app/app/build.gradle.kts
// 这是【最终修正版】，修正了之前的语法错误

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
}

// ===================================================================
// ============= Chaquopy 配置块 (已完全修正语法) =====================
// ===================================================================
chaquopy {
    // [修正] 将 Python 源码目录的配置放在这里
    sourceSets {
        getByName("main") {
            srcDirs("src/main/python")
        }
    }

    defaultConfig {
        // [修正] 这些配置项必须在 defaultConfig 内部
        
        // (必需) 指定要打包到 APP 中的 Python 版本。
        version = "3.11.5"

        // (必需) 告诉 Chaquopy 在构建机器上使用哪个 Python 命令。
         buildPython("python")

        // 指定 Python 依赖包。
        pip {
            install("Flask")
            install("PyYAML")
            install("requests")
        }

        // 指定 CPU 架构 (ABI)。
        abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"))
    }
}


dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
