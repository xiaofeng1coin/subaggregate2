// 文件路径: android_app/app/build.gradle.kts
// 这是修正后的完整版本

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Chaquopy 插件
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
        
        // 注意：我们已经将下面的 ndk.abiFilters 配置移到了 chaquopy 块中，
        // 由 Chaquopy 统一管理，所以这里可以删除或注释掉。
        // ndk {
        //     abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        // }
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

    // Chaquopy 的 Python 源码目录需要在这里也声明一下
    sourceSets {
        getByName("main") {
            python.srcDir("src/main/python")
        }
    }
}

// ===================================================================
// ============= Chaquopy 配置块 (已完全修正) ========================
// ===================================================================
chaquopy {
    // 1. [新增] (必需) 指定要打包到 APP 中的 Python 版本。
    //    这个配置之前缺失了。
    version.set("3.11.5")

    // 2. [关键修复] 告诉 Chaquopy 在构建机器上使用哪个 Python 命令。
    //    我们将其设置为 "python"，因为 CI 环境通过 setup-python 步骤提供了它。
    //    之前的错误 `buildPython("3.11")` 已被修正为正确的属性设置方式。
    buildPython.set("python")

    defaultConfig {
        // 3. [移动和推荐] 将 ABI (CPU架构) 的配置移到这里，由 Chaquopy 统一管理。
        //    这样更清晰，也是官方推荐的做法。
        abi.set(setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"))

        // 4. (保留) Python 依赖包的声明。
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
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
