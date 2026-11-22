# NOVA

NOVA is an original Android desktop-environment project. This repository now contains the first installable Android workspace foundation.

## Preview 0.1.0

Implemented:
- Kotlin + Jetpack Compose Android app (Android 7.0+, minSdk 24).
- Original NOVA shell with Home, Files, Apps, Performance and Settings areas.
- Native in-app Notes and System Monitor windows.
- Android/ABI, available-storage and app-heap snapshot.
- User-selected shared-folder access via Android Storage Access Framework with persistable permission.
- Folder browsing, create-folder, delete and open-file handoff to Android apps.
- Light/dark appearance and selectable performance preference.
- GitHub Actions unit-test and APK build pipeline.

Not implemented yet:
- Windows/Wine/Box64 compatibility runtime.
- Linux userspace and package installation.
- Embedding external guest application windows.
- Guest GPU translation or Vulkan/Direct3D acceleration.
- System-wide CPU/GPU scheduling, thermal policy or privileged tuning.
- Full file operations, global indexing, archives and advanced document previews.

This preview does not claim to execute Windows or Linux desktop binaries. Those are separate engineering milestones requiring native runtime integration and device validation.

## Build
JDK 17, Android SDK platform 35 and Gradle 8.9:
`gradle :app:testDebugUnitTest :app:assembleDebug`

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

Primary target: OPPO A6X 5G, 4 GB physical RAM / 128 GB storage. No virtual RAM expansion is counted.
