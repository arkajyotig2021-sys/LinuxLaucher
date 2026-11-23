# Third-party runtime notices

NOVA's first ARM64 Linux command-line backend uses the Android PRoot Engine from
[oonid/pr](https://github.com/oonid/pr), pinned to commit
`2b12909a74373b2ba1621b1de253f0225f8a137d`.

The Kotlin Android host wrappers and PTY bridge are MIT-licensed. The bundled
PRoot executable is GPL-2.0-or-later; the bundled pr-cli is GPL-3.0-or-later;
the statically linked talloc component is LGPL-3.0-or-later. The exact upstream
source archive and its upstream LICENSE file are included in NOVA preview
release assets. The engine is used as separate executable processes; NOVA's
application UI remains a separate module.

The PRoot Engine is ARM64-only in this preview. It does not provide a Linux
graphical display server, Windows/Wine execution, Box64, or guest GPU translation.
