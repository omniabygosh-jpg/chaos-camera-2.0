# CHAOS CAMERA 0.1 — Android project

Experimental camera app that keeps a live camera preview visible and takes still photos after random delays, with independently enabled randomization for ISO, exposure time, and manual focus where the device supports those controls.

## Current prototype scope

- Live preview using Android Camera2 API.
- Random interval between shots (seconds, configurable minimum and maximum).
- Independent toggles and ranges for ISO, exposure time (seconds), and focus distance (diopters).
- Random values generated using `SecureRandom`.
- JPEG capture saved to `Pictures/CHAOS CAMERA` through `MediaStore`.
- Photo counter and Start/Stop controls.
- In-app screen brightness control and three brightness presets.
- Keeps the display awake while the activity is open.
- No internet, microphone, audio, or location permissions.

## Build

1. Install Android Studio (current stable) with Android SDK Platform 35 and JDK 17.
2. Open this folder as an existing Gradle project.
3. Allow Android Studio to sync Gradle dependencies.
4. Connect an Android phone, enable USB debugging, and Run the `app` configuration; or use **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Application ID: `com.chaoscamera.app`  
Version: `0.1`  
Minimum Android: 8.0 (API 26)  
Target SDK: 35

## Device limitations / known prototype caveats

- Camera2 hardware capabilities vary. ISO and shutter-time randomization require `MANUAL_SENSOR`; focus randomization requires a lens that reports a non-zero minimum focus distance. Unsupported controls fall back to automatic behavior.
- The preview stays visible and the activity keeps the screen awake. This first prototype deliberately does **not** take photos with the screen off or while the app is backgrounded.
- Screen brightness is controlled for this app window only. The phone's camera sensor, processor, and display still consume power; minimum brightness is not equivalent to turning the display off.
- Android OEM camera stacks may reject some combinations of manual exposure/focus settings. Device testing is required.
- The initial UI uses editable numeric fields rather than polished range sliders; this keeps every range directly adjustable and avoids assuming device-specific camera limits before the camera is queried.
- The prototype uses a JPEG output size chosen from the camera's supported sizes. Production hardening should add selectable output size, orientation verification, capture queue handling, disk-full handling, and per-device testing.

## Architecture

- `MainActivity`: UI, lifecycle, permission request, brightness and session controls.
- Camera2 pipeline: `CameraManager` → `CameraDevice` → `CameraCaptureSession` with a preview `Surface` and JPEG `ImageReader`.
- Randomizer: independent uniform sampling inside validated/clamped device-supported ranges using `SecureRandom`.
- Storage: `MediaStore` insertion into `Pictures/CHAOS CAMERA`.

## Next development milestones

1. Test on the target phone and inspect its Camera2 capabilities.
2. Correct orientation and preview/JPEG sizing for that device.
3. Add range sliders with live device-supported bounds and saved settings.
4. Add capture metadata log (time, ISO, exposure, focus) for each image.
5. Improve error recovery and add a configurable maximum session duration / shot count.
6. Create signed release APK after device testing.

## Reproducible GitHub Actions build
The repository includes `.github/workflows/build-apk.yml`. Push to `main` or run it manually from the Actions tab. On success, download the `chaos-camera-debug-apk` artifact; it contains `app-debug.apk`.

Pinned build versions: Android Gradle Plugin 8.7.3, Gradle 8.9, Kotlin 2.0.21, JDK 17, Android SDK 35. See `BUILD_FIXES.md` for the current build status and troubleshooting notes.
