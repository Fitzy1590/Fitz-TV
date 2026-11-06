plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.fitztv.core"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
}

dependencies {
    val media3 = "1.6.1"
    api("androidx.core:core-ktx:1.15.0")
    api("androidx.work:work-runtime-ktx:2.10.0")

    // Playback engines (shared by the TV and phone apps)
    api("androidx.media3:media3-exoplayer:$media3")
    api("androidx.media3:media3-exoplayer-hls:$media3")
    api("androidx.media3:media3-exoplayer-dash:$media3")
    api("androidx.media3:media3-datasource-okhttp:$media3")
    api("androidx.media3:media3-ui:$media3")
    api("org.jellyfin.media3:media3-ffmpeg-decoder:$media3+2") // extra audio codecs
    api("org.videolan.android:libvlc-all:3.6.2")              // fallback engine

    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
