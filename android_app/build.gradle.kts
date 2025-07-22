// 文件路径: android_app/build.gradle.kts
// 这是合并了项目级和模块级配置的唯一构建脚本

plugins {
    // [关键修改] 我们在这里直接应用插件，并带上版本号
    id("com.android.application") version "8.2.2"
    id("org.jetbrains.kotlin.android") version "1.9.22"
    id("com.chaquo.python") version "15.0.1"
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
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    chaquopy {
        version = "3.11"
        pip {
            install("Flask")
            install("PyYAML")
            install("requests")
        }
    }

    sourceSets {
        getByName("main") {
            // [关键修改] 源码目录现在是 'src'，因为我们去掉了 'app' 这一层
            java.srcDirs("src/main/java") 
            res.srcDirs("src/main/res")
            assets.srcDirs("src/main/assets")
            manifest.srcFile("src/main/AndroidManifest.xml")
            python {
                srcDir("src/main/python")
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

    buildFeatures {
        viewBinding = true
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
