# Project status

## Implemented in source

- Kotlin / Jetpack Compose Android app with Home, Explore, My device and Settings navigation.
- Preloaded searchable Linux catalogue spanning Ubuntu, Debian, Alpine, Fedora, Arch, openSUSE, Kali, Rocky, AlmaLinux, Oracle Linux, Amazon Linux, BusyBox, Gentoo, Nix/NixOS userspace and a custom OCI image entry.
- Editable public Docker Hub OCI image reference in the installer, so users are not restricted to the two initial hard-coded images.
- OCI manifest architecture selection, SHA-256 layer verification, staged extraction and rootfs path protections.
- PRoot terminal session integration in app-private storage.
- Android device telemetry dashboard with explicit limits on what Android exposes.
- CI workflow builds PRoot binaries/loaders for ARM32, ARM64, x86 and x86_64; transient upstream download failures are retried.

## Runtime boundaries

This runs Linux userspace in PRoot on Android's existing kernel. It is not a full VM, does not boot a separate kernel, and cannot promise systemd, kernel modules, privileged services, or every hardware driver. A catalogue image being present does not prove that image has been tested on every device. Custom OCI input currently supports public Docker Hub image references; private registries and arbitrary rootfs archives are not yet implemented.

## Not yet completed

- Graphical desktop install profiles and an integrated desktop viewer.
- Durable pause/resume/cancel for rootfs downloads.
- Full/selective backup and restore.
- Distro-aware update notifications and complete package-manager UX.
- External-storage location selection and comprehensive diagnostics/history.
- Real-device validation across target phones, including the OPPO A6X 5G.

## Release gate

Do not call the app release-ready until the native PRoot build, Kotlin unit tests, Android emulator UI tests and APK assembly all pass, and the resulting APK artifact is available. A successful cloud build still does not replace physical-device testing.
