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
- Live device dashboard with physical RAM, best-effort whole-device CPU sampling from `/proc/stat`, app-process CPU time, Android thermal status, and an estimated resource-headroom indicator. The estimate is not a frame-time benchmark.
- Smooth, Balanced and Battery Saver runtime modes. Adaptive policy lowers the Linux launch-thread priority and reduces XFCE desktop resolution when RAM is low or thermal status rises; Android still controls actual scheduling and thermal limits.
- Android notifications for download/install progress, completion, errors and user-triggered apt update checks. Android 13+ notification permission is requested at first launch.
- Persistent error history, copied diagnostic reports, app-crash logging, and a recovery prompt that reopens a new shell in the last active distro. Exact in-memory process state cannot be restored after a crash.
- Independent installed-distro list with open/remove actions and per-distro apt update checks.
- SAF-based file import/export between Android storage and each distro's `/root/shared` directory.
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
- Rootfs files remain in app-private storage. Android Storage Access Framework is used to copy individual files into/out of `/root/shared`; external shared storage is not directly mounted or executed because Android may enforce no-exec and scoped-storage restrictions. Selective rootfs backup and automatic scheduled updates are not implemented. Update checks currently support apt-based Debian/Ubuntu images; applying package upgrades remains a user-controlled terminal action.
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


## Compatibility and recovery guarantees

- Ubuntu 24.04 and Debian Bookworm are the configured apt-based runtime profiles. Kali is experimental. Other catalogue entries are discovery entries only until their OCI rootfs, package manager, startup behaviour and desktop are validated. A registry image reference alone is not a compatibility guarantee.
- Linux sessions run rootlessly through PRoot and share the Android kernel. systemd, kernel modules, privileged mounts, hardware acceleration and arbitrary init/service assumptions are not promised.
- Recovery stores the last active distribution and reopens a fresh shell/desktop entry point. Android cannot resurrect a terminated process with its exact RAM, open descriptors or in-memory state.
- CPU scheduling priority and desktop resolution are best-effort controls. The RAM reserve is an automatic-throttling threshold, not a hard memory quota; Android retains control of process scheduling and memory reclamation.
- Backup traversal is streaming to avoid allocating a list of every rootfs entry. Large archive creation/restoration still needs emulator and physical-device interruption testing.
