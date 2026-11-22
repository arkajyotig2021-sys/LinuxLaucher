# NOVA

NOVA is an original Android workspace project. This repository contains an installable Android shell and the next runtime/file-system engineering foundations.

## Current implementation

- Kotlin + Jetpack Compose Android app (Android 7.0+, minSdk 24).
- Home, Files, Apps, Performance and Settings areas.
- Native Notes and System Monitor windows.
- User-approved Storage Access Framework folder access.
- SAF file operation service: list, create file/folder, rename, recursive copy, copy-then-delete move, delete, open and share intents. Provider capabilities and permissions still apply.
- Installed Android app discovery and launch through normal Android tasks.
- Guest runtime plug-in contract for Windows and Linux, with explicit NOT_INSTALLED status until real native backends are packaged.
- In-shell window/task session model.
- Host OpenGL ES/Vulkan/ABI capability reporting.

## Important limitations — not yet implemented

- **Windows/Wine/Box64 execution:** no Wine, Box64 or Windows guest runtime binaries are bundled. Windows EXE/MSI execution is not available.
- **Linux desktop execution:** no Linux userspace, package manager, display server or desktop environment is bundled. Linux ELF/desktop execution is not available.
- **Unified external windows:** installed Android apps launch in their own Android tasks. Ordinary Android apps cannot embed arbitrary other apps' Activities as NOVA child windows.
- **Advanced GPU translation:** this build detects host graphics APIs only. It does not contain DXVK, WineD3D, Vulkan guest translation, Direct3D translation or a guest graphics bridge.
- **Full file-manager UI:** the SAF service provides core operations, while the current screen still exposes only a subset. SAF providers can reject operations and may not support atomic moves or all metadata.
- The project has not been physically installed or validated on the OPPO A6X 5G. CI compilation/tests are not device compatibility proof.

## Engineering direction

Real Windows/Linux execution requires compatible native runtime components for each ABI, process and filesystem isolation, guest display/input integration, dependency management, licensing review, storage lifecycle, and device-specific testing. Guest GPU translation is a separate native graphics project and must not be inferred from the presence of Vulkan/OpenGL ES on the host.

## Build

JDK 17, Android SDK platform 35 and Gradle 8.9:
`gradle :app:testDebugUnitTest :app:assembleDebug`

APK output: `app/build/outputs/apk/debug/app-debug.apk`.
