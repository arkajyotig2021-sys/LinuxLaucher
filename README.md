# Linux Launcher — Phase 1 Foundation

This is the first Android source-code milestone for the Linux Launcher project.

## What is implemented

- Native Android application using Kotlin and Jetpack Compose.
- Dashboard with the target device profile.
- Searchable starter Linux distribution catalogue.
- Distribution detail dialog with explicit runtime status disclosure.
- Navigation foundation for Dashboard, Distros, Downloads and Performance.
- Light/dark theme following Android system appearance.
- Unit tests for distro catalogue search.
- GitHub Actions workflow for unit tests and debug APK generation.

## What is NOT implemented yet

This milestone does **not** install or boot Linux. It does not contain rootfs downloads,
PRoot, a VM, desktop streaming, package-manager integration, GPU acceleration,
hardware bridges, backup/restore, or a real download manager. The UI explicitly
labels these sections as future runtime work rather than pretending they function.

## Device target

Designed to be a lightweight starting point for the OPPO A6X 5G configuration
reported for this project: 4 GB physical RAM and 128 GB internal storage.
The app does not reserve or consume the full device RAM.

## Build

Requirements:
- JDK 17
- Gradle 8.13
- Android SDK platform 36 and build tools 36.0.0

From the project root:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

The debug APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions cloud build

1. Create a GitHub repository.
2. Upload this project folder's contents to the repository root.
3. Open the Actions tab.
4. Run **Android APK build** using **Run workflow**.
5. Download the `LinuxLauncher-debug-apk` artifact after the workflow succeeds.

## Version choices

The project uses Android Gradle Plugin 8.13.2, Gradle 8.13, Kotlin 2.2.21,
and Compose BOM 2026.09.00. Verify compatibility against current Android
tooling before upgrading these versions.

## Project identity

- Application ID: `com.mindflux.linuxlauncher`
- Version: `0.1.0`
- Minimum Android API: 26
- Target Android API: 35
