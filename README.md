# NOVA

NOVA is an original Android workspace project. The current repository contains an Android shell, a SAF-based file manager, Android app launching, task/session foundations, and host graphics capability reporting.

## Implemented in the Android app

- Kotlin + Jetpack Compose Android application (minSdk 24, target SDK 35).
- Home, Files, Apps, Performance and Settings areas.
- Built-in Notes and System Monitor windows.
- User-approved Storage Access Framework (SAF) folder access; no root or unrestricted-storage permission.
- Spatial Files UI: folder navigation and breadcrumbs, current-folder search, name/size/modified sorting, hidden-file toggle, multi-select, create file/folder, rename, copy, cut, paste, cross-folder move, delete, open, share and details.
- File operations are delegated to SAF. Exact support depends on the storage provider; some providers reject rename, delete, copy or move operations.
- Installed Android app discovery and launch using normal Android tasks.
- Optional Android-managed freeform launch request using ActivityOptions launch bounds. Android/OEM firmware can ignore this request if freeform windows are unavailable.
- In-shell window/task session model for NOVA-owned windows.
- Host OpenGL ES, Vulkan-feature and ABI capability reporting.

## Runtime status — not yet implemented

The following are **not available in this build**:

- **Windows execution:** Wine and Box64 binaries, a Windows prefix, runtime installer, process supervisor, and a verified Windows launch path are not bundled. EXE/MSI execution is unavailable.
- **Linux desktop execution:** no PRoot binary, Linux root filesystem, package manager, X/Wayland display server, desktop environment, or GUI bridge is bundled. Linux ELF and desktop-app execution are unavailable.
- **Unified external Android windows:** arbitrary third-party Android Activities cannot be embedded inside NOVA's Compose window tree by an ordinary third-party app. NOVA launches them as Android tasks and can request OS-managed freeform bounds where supported.
- **Advanced guest GPU translation:** host API detection is not GPU translation. DXVK, VKD3D, WineD3D integration, guest Vulkan/OpenGL translation, driver selection, and a tested Mali-specific graphics backend are not included.
- **Device validation:** CI compilation is not a substitute for installing and testing on the OPPO A6X 5G (CPH2823).

## Target-device engineering notes

OPPO India lists the CPH2823 with MediaTek Dimensity 6300 and ARM Mali-G57 MC2. The runtime must still probe the actual installed Vulkan/OpenGL driver at runtime; a GPU model name or Android feature flag does not prove that a particular DXVK/VKD3D path works. A 4 GB physical-RAM configuration also requires conservative guest memory limits and a lightweight desktop profile.

## Why these runtimes are separate engineering projects

A real rootless Linux desktop requires an Android-compatible PRoot/process runner, an ARM64 root filesystem, package installation and updates, a display server, input routing, lifecycle management, and storage/network integration. A Windows environment additionally needs compatible Wine and Box64 builds, a working prefix, architecture translation, audio/input integration and a graphics path. These pieces must be versioned, checksum-verified, license-reviewed and tested together on actual hardware.

A genuine guest-GPU path requires more than detecting Vulkan. It needs compatible guest translation libraries and a host driver that exposes the required extensions/features. If the path is unsupported, NOVA must report that honestly and use a slower compatible fallback where available.

## Build

JDK 17, Android SDK platform 35 and Gradle 8.9:

`gradle :app:testDebugUnitTest :app:assembleDebug`

APK output: `app/build/outputs/apk/debug/app-debug.apk`.
