import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.netraprint.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.netraprint.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 24
        versionName = "2.0.18"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    val localProperties = Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { load(it) }
        }
    }
    // ⚠️ SECURITY: Never hardcode signing passwords. These must be set in local.properties
    // (gitignored) or via CI secrets. Release builds will fail loudly if missing.
    val releaseStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
        ?: error("""
            |RELEASE_STORE_PASSWORD is not set in local.properties.
            |Add the following to local.properties:
            |  RELEASE_STORE_PASSWORD=your_store_password
            |  RELEASE_KEY_PASSWORD=your_key_password
        """.trimMargin())
    val releaseKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
        ?: error("""
            |RELEASE_KEY_PASSWORD is not set in local.properties.
            |Add the following to local.properties:
            |  RELEASE_STORE_PASSWORD=your_store_password
            |  RELEASE_KEY_PASSWORD=your_key_password
        """.trimMargin())

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = releaseStorePassword
            keyAlias = "netraprint"
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

base {
    archivesName.set("NetraPrint")
}

val releaseOutputDir = layout.buildDirectory.dir("outputs/apk/release")
val debugOutputDir = layout.buildDirectory.dir("outputs/apk/debug")

tasks.matching { it.name == "assembleRelease" }.configureEach {
    val dir = releaseOutputDir
    doLast {
        val destination = dir.get().asFile
        val targetApk = File(destination, "NetraPrint.apk")
        destination.listFiles()?.forEach { file ->
            if (file.extension == "apk" && file.name != "NetraPrint.apk") {
                file.copyTo(targetApk, overwrite = true)
            }
        }
        println("Release APK successfully packaged to: ${targetApk.absolutePath}")
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    val dir = debugOutputDir
    doLast {
        val destination = dir.get().asFile
        val targetApk = File(destination, "NetraPrint-debug.apk")
        destination.listFiles()?.forEach { file ->
            if (file.extension == "apk" && file.name != "NetraPrint-debug.apk") {
                file.copyTo(targetApk, overwrite = true)
            }
        }
    }
}



dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // CameraX for high-precision barcode scanning
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ZXing for QR Code generation
    implementation("com.google.zxing:core:3.5.3")

    // AndroidX WebKit for WebView
    implementation("androidx.webkit:webkit:1.11.0")

    // Google Play In-App Review
    implementation("com.google.android.play:review-ktx:2.0.1")

    // Lottie animations for smooth UI feedback
    implementation("com.airbnb.android:lottie:6.4.0")

    // AndroidX Security Crypto for EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // AndroidX Lifecycle + ViewModel for MVVM
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}