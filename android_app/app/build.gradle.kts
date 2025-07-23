// 文件路径: android_app/app/build.gradle.kts
// 这是完整的、已修正的版本，请直接替换

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.example.subaggregator"
    compileSdk = 34

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("RELEASE_KEYSTORE")
            if (keystoreFile != null && File(keystoreFile).exists()) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.subaggregator"
        minSdk = 24
        targetSdk = 34
        
        versionCode = 10002
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
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
            signingConfig = signingConfigs.getByName("release")
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
        // =======================> [修复 1: 开启 BuildConfig] <=======================
        // 这一行是解决 "Unresolved reference: BuildConfig" 错误所必需的。
        buildConfig = true
        // =======================> 修复 1 结束 <==================================
    }
}

chaquopy {
    sourceSets {
        getByName("main") {
            srcDirs("src/main/python")
        }
    }
    defaultConfig {
        version = "3.11"
        // =======================> [修复 2: 修正 buildPython 配置] <================
        // 原来的 "python" 是无效值，这里明确指定为 "3.11"，与 version 保持一致。
        buildPython("3.11")
        // =======================> 修复 2 结束 <==================================
        pip {
            install("Flask")
            install("PyYAML")
            install("requests")
        }
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
