# Linux Launcher — Android Linux environments

Linux Launcher is an Android app that provides a hardware-aware interface for installing and running Linux userspaces through rootless PRoot. It shares Android's kernel; it is **not** a virtual machine and does not boot a separate Linux kernel.

## Current implementation

- Native Android app using Kotlin and Jetpack Compose.
- Four working navigation sections: Home, Explore, My device, Settings.
- Preloaded searchable distro catalogue covering Debian-based, RPM-based, independent and minimal Linux environments.
- OCI image reference field in the install dialog. Choose a catalogue entry or enter another public Docker Hub Linux image reference (`namespace/image:tag`).
- OCI manifest selection for the Android device architecture, layer SHA-256 verification, rootfs extraction and path traversal protections.
- Rootless PRoot terminal session for an installed root filesystem.
- Device dashboard reports Android-exposed physical memory, app CPU time and thermal information where supported.
- GitHub Actions builds the native PRoot runtime for ARM32, ARM64, x86 and x86_64, then runs tests and assembles a debug APK.

## Important compatibility limits

- A Docker/OCI image is a userspace filesystem, not a complete bootable Linux OS. systemd, kernel modules, privileged services and hardware drivers generally do not run inside PRoot.
- The custom image field currently targets public Docker Hub images. Private registries and arbitrary downloadable rootfs archive formats are not supported by this installer yet.
- Not every image contains a shell or package manager, and not every distro/image works with Android's kernel or PRoot. Image availability is not a guarantee of runtime compatibility.
- Graphical desktop streaming, pause/resume downloads, backup/restore and full package-manager orchestration are not part of the verified terminal milestone yet.
- The cloud emulator cannot certify performance on every physical phone. Real-device validation on the OPPO A6X 5G is still required.

## Build

Requirements: JDK 17, Gradle 8.13, Android SDK/NDK as configured in `.github/workflows/android-build.yml`.

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

GitHub Actions uploads `LinuxLauncher-debug-apk` only after the native runtime build, unit tests, emulator UI tests and APK assembly succeed.
