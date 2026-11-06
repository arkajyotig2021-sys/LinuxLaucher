# Project status

## Current verified scope

The app currently contains the Android Compose launcher interface, a searchable Linux distribution catalogue, device information, and four-section navigation. The catalogue is informational: it does not yet download or run Linux distributions.

## Automated validation

- Unit tests cover distribution catalogue search.
- Android emulator UI tests are being added to exercise all four navigation sections.
- A successful APK compilation alone is not proof that navigation, installation, or Linux runtime works.

## Not yet implemented

- Linux root filesystem downloads and extraction
- PRoot runtime and process/session management
- Terminal-only and graphical-desktop installation choices
- Desktop environment installation and display
- Package manager integration
- Download pause/resume/retry/cancel
- Live performance dashboard, performance modes, backup/restore

## Release approach

Keep all practical distributions visible in the catalogue. Implement and validate runtime support incrementally, beginning with a reliable Android-compatible base, while retaining the requested terminal-only and graphical-desktop choices. Do not mark a distribution as installable until its download, setup, launch, and basic package-manager path have been tested.

Physical testing on the user's OPPO A6X 5G and across the wider Android device ecosystem cannot be claimed from cloud CI alone.
