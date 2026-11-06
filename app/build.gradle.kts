plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fitztv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fitztv"
        minSdk = 26
        targetSdk = 35
        // CI build number = version, so the in-app updater can tell which build is newer.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 100 + build
        versionName = "1.0.$build"
        // NVIDIA Shield is arm64; keep 32-bit for older Android TV boxes.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Same key on every CI build (see signing/KeyGen.java) so updates install over the old version.
    val ciKeystore = System.getenv("FITZTV_KEYSTORE")
    signingConfigs {
        if (ciKeystore != null) create("ci") {
            storeFile = file(ciKeystore)
            storePassword = System.getenv("FITZTV_KEY_PASSWORD")
            keyAlias = "fitztv"
            keyPassword = System.getenv("FITZTV_KEY_PASSWORD")
            storeType = "pkcs12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so the APK can be sideloaded straight from CI.
            signingConfig = signingConfigs.getByName(if (ciKeystore != null) "ci" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/*.kotlin_module")
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(project(":core"))
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3") // text fields only
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.zxing:core:3.5.3") // QR code for phone setup (generated offline)
    implementation("androidx.tvprovider:tvprovider:1.0.0") // Fitz TV row on the Android TV home screen
}
