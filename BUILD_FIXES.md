# CHAOS CAMERA 0.1 — build configuration notes

This archive restores the original CHAOS CAMERA 0.1 Android/Kotlin source and includes the Gradle/GitHub Actions configuration consolidated after the November 2026 troubleshooting screenshots.

## Pinned toolchain
- Android Gradle Plugin: 8.7.3
- Gradle: 8.9
- Kotlin Android plugin: 2.0.21
- JDK: 17
- compileSdk / targetSdk: 35
- minSdk: 26
- applicationId: com.chaoscamera.app

## Included build configuration
- Root `build.gradle.kts` declares both plugin versions and uses `apply false`.
- `settings.gradle.kts` explicitly configures Google, Maven Central, and Gradle Plugin Portal repositories.
- `app/build.gradle.kts` applies the plugins without redeclaring versions and targets JVM 17.
- `.github/workflows/build-apk.yml` installs JDK 17, Gradle 8.9, Android SDK Platform 35 and Build Tools 35.0.0, then builds and uploads `app-debug.apk`.

## Important status
The project source was restored from the previously saved 0.1 archive. The Gradle/workflow configuration has been updated, but this environment does not have Android SDK/Gradle available, so this archive is **not claimed to have passed compilation**. The Kotlin dependency-resolution error shown in the latest GitHub Actions screenshot must be verified by running the workflow again. If it persists, save the complete new `Build debug APK` log.

The app has not been tested on the Tecno Pova 5. Camera2 manual ISO, shutter, and focus support depends on the actual camera module and must be checked on-device.
