plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

// Fitz TV for Windows. Shares :core's platform-free logic (sources, matching, schedules, ranking,
// failover) by compiling those files directly, and swaps the Android-only pieces (storage, player
// engine, notifications) for desktop versions in `src/main/kotlin/com/fitztv/desktop` and
// `src/main/kotlin/android` (small stand-ins so the shared files compile unchanged).
val core = rootProject.file("core/src/main")


// Copy :core's sources minus the Android-only files; the desktop replacements live in this module
// under the same package names (an exclude on the srcDir itself would also hide those).
val coreSources = layout.buildDirectory.dir("generated/core")
val syncCore by tasks.registering(Sync::class) {
    from(core.resolve("java")) {
        exclude(
            // Android-only: replaced by versions under desktop/src/main/kotlin/com/fitztv.
            "com/fitztv/FitzApp.kt",
            "com/fitztv/alerts/Alerts.kt",
            "com/fitztv/data/ConnectionGuard.kt",
            "com/fitztv/player/ExoEngine.kt",
            "com/fitztv/player/VlcEngine.kt",
            "com/fitztv/player/Launcher.kt",
            "com/fitztv/player/Multiview.kt",
            "com/fitztv/player/PlaybackQueue.kt",
            "com/fitztv/player/WifiBoost.kt",
            "com/fitztv/record/Recorder.kt",
            "com/fitztv/update/Updater.kt",
            "com/fitztv/work/RefreshWorker.kt",
        )
    }
    into(coreSources)
}

// CI run number -> shown in Settings and used by the updater (same numbering as the APKs).
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
val genBuildInfo by tasks.registering {
    val out = layout.buildDirectory.dir("generated/buildinfo")
    outputs.dir(out)
    inputs.property("build", buildNumber)
    doLast { out.get().asFile.also { it.mkdirs() }.resolve("fitztv-build.properties").writeText("build=$buildNumber\n") }
}

sourceSets.main {
    kotlin.srcDir(syncCore.map { coreSources })
    resources.srcDir(core.resolve("assets"))
    resources.srcDir(core.resolve("res/font"))
    resources.srcDir(genBuildInfo)
}

// The shared matcher / ranking tests run here too, proving :core's logic behaves the same on the JVM.
sourceSets.test { kotlin.srcDir(core.resolve("../test/java")) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("net.sf.kxml:kxml2:2.3.0")          // XMLTV pull parser (android.util.Xml stand-in)
    implementation("uk.co.caprica:vlcj:4.8.3")          // libVLC playback (VLC 3.x installed or bundled)
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    testImplementation("junit:junit:4.13.2")
}

tasks.test { useJUnit() }

compose.desktop {
    application {
        mainClass = "com.fitztv.desktop.MainKt"
        nativeDistributions {
            // libVLC is copied into appResources/windows-x64/vlc by CI (see build.yml) and ships inside the installer.
            appResourcesRootDir.set(layout.projectDirectory.dir("appResources"))
            includeAllModules = true
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "FitzTV"
            packageVersion = "1.0.$buildNumber"
            description = "Fitz TV - sports-only IPTV"
            vendor = "Fitz TV"
            windows {
                menuGroup = "Fitz TV"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                iconFile.set(file("packaging/fitztv.ico"))
                upgradeUuid = "6f1b5a54-3b1e-4c6e-9a3a-2d8f7f0b8f10"
            }
        }
    }
}
