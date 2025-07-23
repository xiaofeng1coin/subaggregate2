// 文件路径: android_app/app/build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.example.subaggregator"
    compileSdk = 34

    // [关键修改开始] 替换为从环境变量读取密钥的正式签名配置
    signingConfigs {
        create("release") {
            // 只在CI环境（有环境变量时）才配置这些值
            // 这可以防止在没有设置环境变量的本地机器上构建时出错
            val keystoreFile = System.getenv("RELEASE_KEYSTORE")
            if (keystoreFile != null && File(keystoreFile).exists()) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }
    // [关键修改结束]

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
            // [关键修改开始] 告诉 release 构建类型使用我们新的 "release" 签名配置
            signingConfig = signingConfigs.getByName("release")
            // [关键修改结束]
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

chaquopy {
    sourceSets {
        getByName("main") {
            srcDirs("src/main/python")
        }
    }
    defaultConfig {
        version = "3.11"
        buildPython("python")
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
