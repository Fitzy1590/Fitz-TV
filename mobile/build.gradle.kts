plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fitztv.mobile"
    compileSdk = 35

    defaultConfig {
        // Different id from the TV app so both can be installed side by side.
        applicationId = "com.fitztv.mobile"
        minSdk = 26
        targetSdk = 35
        // CI build number = version, so the in-app updater can tell which build is newer.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 100 + build
        versionName = "1.0.$build"
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (ciKeystore != null) "ci" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { checkReleaseBuilds = false; abortOnError = false }
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
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.window:window:1.3.0") // fold / hinge awareness (Galaxy Z Fold)
    implementation("io.coil-kt:coil-compose:2.7.0")
}
