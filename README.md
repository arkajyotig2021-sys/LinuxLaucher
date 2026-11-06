# Linux Launcher

Linux Launcher is an Android app for installing and running compatible Linux userspaces without root. It uses PRoot and the Android device's existing Linux kernel; it is **not** a virtual machine and does not boot a separate kernel.

## Current app features

- Four main sections: Home, Explore, My device and Settings.
- Searchable catalogue of Linux distributions, plus an editable OCI image reference for public images from Docker Hub and other OCI registries.
- Architecture-aware OCI manifest selection for ARM64, ARM32, x86_64 and x86 devices.
- Layer SHA-256 verification, guarded extraction, gzip and uncompressed tar support, and saved partial downloads.
- Download controls: pause while preserving partial data, resume, restart, and cancel while discarding partial data.
- Full Linux rootfs backup and restore as a `.tar.gz` archive through Android file pickers, with archive path validation and staged restore replacement.
- Rootless PRoot terminal for compatible Linux root filesystems. Commands run inside the selected Linux userspace.
- Environment chooser after installation: terminal or an XFCE desktop setup.
- XFCE desktop setup with TigerVNC and a local noVNC viewer for Debian/Ubuntu images using apt.
- Device dashboard with physical RAM, app CPU-time and Android thermal information where available.
- Android 7.0 / API 24 minimum target; native runtime build includes ARM and x86 ABIs.

## Install a Linux image

1. Open **Explore** and choose a catalogue entry, or choose an entry as a starting point for a custom OCI image.
2. Open **Install / Open**.
3. Enter a public image reference, for example `alpine:3.22`, `ubuntu:24.04`, or `ghcr.io/owner/image:tag`.
4. Tap **Install Linux**. If a transfer is interrupted, choose **Resume** or **Restart**. While downloading, **Pause** preserves partial layer files and **Cancel** removes them.
5. After installation, choose **Open terminal** or **Install / open XFCE desktop**.

The app needs network access to download images and packages. Large root filesystems and desktop environments can use several gigabytes of storage. Keep the phone connected to power and Wi-Fi for large installs.

## Compatibility and limitations

- A public OCI reference is not a guarantee that every image will run. The image must publish a matching Linux architecture and contain a compatible shell/runtime layout. Some OCI registries or image formats may not be supported.
- OCI gzip and uncompressed tar layers are supported by the current extractor. Zstandard-compressed layers are rejected with an explicit error rather than silently mis-extracted.
- The generic terminal is intended for compatible OCI Linux userspaces. One-click XFCE setup is currently limited to Debian/Ubuntu apt-based images; other distributions can still be used through their own package manager in the terminal, but their graphical setup is not automated yet.
- PRoot shares the Android kernel. Kernel-dependent features, systemd as PID 1, privileged containers, hardware drivers, and some low-level networking or mount operations may not work.
- The current desktop path is CPU-rendered VNC/noVNC. It does not provide native GPU acceleration and can be demanding on low-memory phones. On a 4 GB RAM device, prefer a minimal rootfs and XFCE over a heavier desktop.
- Rootfs files are stored in app-private storage. Backups cover the full root filesystem; selective backup, external removable-storage execution, distro auto-update scheduling, and a complete package-manager UI are not currently implemented.
- This project has not yet been validated on every physical Android device. Emulator CI cannot replace testing on the target phone.

## Build from source

Requirements: JDK 17, Gradle 8.13, Android SDK platform 36, build tools 36.0.0, and Android NDK 23.2.8568313.

```bash
gradle --no-daemon :app:testDebugUnitTest
gradle --no-daemon :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk` after the build workflow completes. The GitHub Actions workflow builds the APK-compatible PRoot runtime for ARM and x86 ABIs, runs unit tests and emulator UI tests, assembles the APK, and uploads it as a workflow artifact.

## Project identity

- Application ID: `com.mindflux.linuxlauncher`
- Minimum Android API: 24
- Target Android API: 35
