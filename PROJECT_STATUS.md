# Project status

## Implemented in source (not yet release-verified)

- Four-section Compose app: Home, Explore, My device and Settings.
- Searchable distro catalogue and generic public OCI image reference.
- OCI rootfs installation for compatible images, SHA-256 layer verification, safe extraction, and pause/resume/cancel controls.
- Rootless PRoot terminal path and Debian/Ubuntu XFCE + local noVNC desktop path.
- Full rootfs backup and staged restore.
- Android notifications for download/install progress, completion, errors and user-triggered apt update checks.
- Persistent error history, diagnostics copy, app crash logging and previous-shell recovery prompt. Recovery starts a new shell; it cannot restore a dead process's memory state.
- Smooth, Balanced and Battery Saver modes, plus adaptive launch-thread priority and XFCE resolution selection based on available RAM and thermal state.
- Live dashboard with physical RAM, app CPU, best-effort system CPU sampling, thermal status and estimated resource headroom. System CPU may be unavailable on restricted devices; responsiveness is an estimate, not measured frame latency.
- Independent installed-distro list with open/remove actions.
- SAF file import/export to each distro's `/root/shared` directory. Files are copied into the Linux rootfs before use; Android shared storage is not directly mounted or executed.

## Current limitations

- Automatic update checks currently support apt-based Debian/Ubuntu images. The app notifies about available packages; upgrades are not silently applied.
- XFCE desktop automation currently supports apt-based Debian/Ubuntu only. Other desktop environments and package managers are not automated.
- Selective backup, scheduled update checks and live external-storage mounts are not implemented.
- CPU scheduling changes are best-effort and remain subject to Android. The app does not overclock or change CPU/GPU clocks.
- A successful Kotlin compile preflight was observed in CI on 2026-11-06. The PRoot native build then failed because legacy talloc Waf Python files contained Python 2 octal syntax; the workflow has been updated to convert the complete talloc buildtools and `wscript`, but that fix still requires CI verification.
- No final APK or physical OPPO A6X 5G test is verified yet. Do not treat the app as release-ready until CI, emulator tests, APK installation and target-device testing pass.
