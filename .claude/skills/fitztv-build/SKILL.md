---
name: fitztv-build
description: How to build, lint and test Fitz TV in a fresh cloud container, and how to read Media3 APIs. Use when setting up the Android SDK, when Gradle fails with 429 or missing SDK, before running lintDebug / testDebugUnitTest, or when subclassing or calling Media3 classes (LoadControl, ExoPlayer).
---

# Building Fitz TV in a cloud session

## Setup (once per container)
- No SDK is preinstalled. Install command-line tools from `dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip` into `/opt/android-sdk/cmdline-tools/latest`, then
  `yes | sdkmanager --sdk_root=/opt/android-sdk "platforms;android-35" "build-tools;35.0.0" "platform-tools"`.
- Write `sdk.dir=/opt/android-sdk` to `local.properties` (gitignored; never commit it).
- The Stop hook skips lint when neither `$ANDROID_HOME` nor `sdk.dir` is set, so set it before finishing.

## Running
- Always `./gradlew <tasks> --console=plain --no-parallel`. Maven Central answers `429 Too Many Requests` to parallel dependency downloads; retry (sleep 30 s) a few times. Downloads already fetched stay cached.
- Verification baseline: `./gradlew lintDebug testDebugUnitTest` (all three modules, about 2 min warm).
- Confirm tests really ran: `grep -ho 'testsuite name=.*' core/build/test-results/testDebugUnitTest/*.xml`.

## Media3 gotchas
- `core/build.gradle.kts` says 1.6.1 but Gradle resolves **1.7.1**. Check signatures with `javap` on
  `~/.gradle/caches/8.11.1/transforms/*/transformed/media3-exoplayer-1.7.1-api.jar` (find it with `find ~/.gradle -name "media3-exoplayer-1*"`); set `JAVA_TOOL_OPTIONS=` first to silence the proxy banner.
- `DefaultLoadControl` can be subclassed through its protected 9-argument constructor
  `(DefaultAllocator, min, max, forPlayback, afterRebuffer, targetBytes, prioritizeTime, backBuffer, retainFromKeyframe)`; override `shouldContinueLoading(Parameters)` / `shouldStartPlayback(Parameters)`. See `core/.../player/ClutchLoadControl.kt`.
- Keep decision logic in pure objects (`ClutchPolicy`) so it is unit-testable on the JVM without Media3.
