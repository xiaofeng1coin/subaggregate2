// =================================================================
// ==              这是最终的 build.gradle.kts 文件              ==
// =================================================================
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

// === 新增：一个辅助函数，用于从版本名生成版本号 ===
fun generateVersionCode(versionName: String): Int {
    val parts = versionName.split('.').map { it.toInt() }
    // 例如 "1.2.3" -> 1*10000 + 2*100 + 3 = 10203
    return parts[0] * 10000 + parts[1] * 100 + parts[2]
}

android {
    namespace = "com.example.subaggregator"
    compileSdk = 34

    signingConfigs {
        create("release") {
            if (project.hasProperty("RELEASE_KEYSTORE_FILE")) {
                storeFile = file(project.property("RELEASE_KEYSTORE_FILE") as String)
                storePassword = project.property("RELEASE_KEYSTORE_PASSWORD") as String?
                keyAlias = project.property("RELEASE_KEY_ALIAS") as String?
                keyPassword = project.property("RELEASE_KEY_PASSWORD") as String?
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.subaggregator"
        minSdk = 24
        targetSdk = 34
        
        // === 核心修改：动态设置版本号和版本名 ===
        // 检查是否有从 CI 传入的 'versionName' 属性
        val releaseVersionName = project.findProperty("versionName")?.toString()
        if (releaseVersionName != null) {
            // 如果有，就使用它 (记得去掉 'v' 前缀)
            versionName = releaseVersionName.removePrefix("v")
            versionCode = generateVersionCode(versionName)
        } else {
            // 如果没有（例如本地开发），使用默认值
            versionName = "1.0.2"
            versionCode = 10002
        }
        // =========================================
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }
}

chaquopy {
    defaultConfig {
        buildPython("python3.11")
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
