# Project status

## Milestone 1 — Android foundation

Status: The redesigned launcher UI and device-detection baseline compiled successfully through GitHub Actions. The debug APK was generated, and the user installed the redesigned UI and approved its visual direction.

Completed:
- Gradle project structure and Compose app entry point
- Dark launcher dashboard and searchable starter distro catalogue
- Distribution detail dialog with explicit runtime limitation disclosure
- Android device, ABI, RAM and storage detection
- Four-section navigation foundation
- Unit tests for distro catalogue search
- GitHub Actions unit-test and debug-APK workflow

Current limitations:
- Linux rootfs downloads, PRoot runtime, VM, desktop sessions and package-manager integration are not implemented.
- Live performance dashboard, performance modes, backup/restore and download manager are not implemented.
- Physical compatibility and sustained performance have not been verified across the Android device ecosystem.

Next development focus:
1. Preserve the approved visual design while fixing navigation sizing and improving Compose rendering efficiency.
2. Set the declared minimum Android version to API 24 and validate the build.
3. Add performance features in separate, testable increments without presenting planned functionality as already working.
4. Run automated tests and ask the user to smoke-test the resulting APK on their phone.
