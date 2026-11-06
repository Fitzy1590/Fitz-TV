plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The device-side engine and package list live in ../shield so the PC/Termux
// scripts and the app share one source of truth. Copy them in as assets.
val shieldAssets = layout.buildDirectory.dir("generated/shieldAssets")
val copyShieldAssets by tasks.registering(Copy::class) {
    from(rootDir.resolve("../shield/device/fitz.sh"), rootDir.resolve("../shield/packages.txt"))
    into(shieldAssets)
}

android {
    namespace = "com.fitztv.shield"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fitztv.shield"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Self-signed personal key, committed so every build (local or CI) can
        // update the installed app in place.
        create("fitz") {
            storeFile = rootDir.resolve("fitztv.jks")
            storePassword = "fitztv"
            keyAlias = "fitztv"
            keyPassword = "fitztv"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("fitz")
        }
        debug { signingConfig = signingConfigs.getByName("fitz") }
    }

    sourceSets.getByName("main").assets.srcDir(shieldAssets)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

tasks.named("preBuild") { dependsOn(copyShieldAssets) }

dependencies {
    implementation("dev.mobile:dadb:1.2.10")
}
