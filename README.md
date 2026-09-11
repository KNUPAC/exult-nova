# Exult Nova — experimental ARM64 Android port

Exult 1.12.1 with a Retroid Pocket Nova controller adapter. Targets Android 13+,
landscape, and arm64-v8a. Game data is imported separately using Exult's existing
Android Storage Access Framework importer. No Ultima VII game assets are included.
This is an unofficial GPL-2.0-or-later modification, not a Retroid or Exult release.

## Build and install

The `Build Exult Nova ARM64` Actions workflow builds on the `nova-controller`
branch and pull requests to main. Open a successful run, download the
`Exult-Nova-arm64-test` artifact, unzip it, and install `Exult-Nova-arm64-debug.apk`.
The app is called **Exult Nova**, package `info.exult.nova`, and coexists with
stock Exult. Import the Black Gate ZIP in its launcher. Do not uninstall an old
build to fix a signing conflict before exporting your saves: debug signing keys
may differ between fresh CI runners. Stable release signing is not configured.

The source archive is checksum-verified, extracted, patched, then built using
upstream's out-of-tree Android build. Gradle 8.9, AGP 8.7.3, Java 17, SDK 35,
NDK 27.2.12479018 and CMake 3.22.1 are selected. Upstream native dependencies
are downloaded during compilation; some upstream dependency refs are unpinned.
The output artifact includes the patched engine source. To reproduce locally on
Ubuntu with the same dependencies, run `bash scripts/build-nova.sh` from a fresh
checkout. Remove or rename `build-nova` before a subsequent build.

## Controller defaults

Labels are Android logical button names, not verified Nova physical labels.

| Control | Action |
|---|---|
| D-pad (keys or hat) | Eight-direction movement |
| Left stick | Mouse cursor |
| A | Left click; hold to drag |
| B | Escape / close / back |
| X | Double click: talk, open, use |
| Y | Inventory |
| R1 | Hold left mouse button to drag |
| R2 | Right mouse button |
| L1 | 25% pointer speed |
| L2 | Slower walking (Exult's Shift modifier) |
| Start, released | Escape / menu |
| Select, released | Map |
| L3 | Combat pause, according to Exult combat settings |
| R3 | Toggle combat |
| Start + Select | Nova controls and diagnostics |

The small **Nova controls** button is also a touch-accessible settings entry.
Use the stick pointer and A/X to operate engine menus and inventory; walking is
suppressed while inventory gumps are open. Android settings dialogs use standard
D-pad focus navigation. You may still need Android's on-screen keyboard for names.

Settings persist: button remapping, 8/12/18/25% radial dead zone, cursor speed,
optional Z/RZ right-stick pointer, and selection of pointer/trigger axes from the
connected controller's advertised ranges. D-pad direction keys/hat use Android's
standard assignments. Default trigger axes are LTRIGGER/RTRIGGER, falling back to
BRAKE/GAS, with 55% press / 35% release thresholds. Disable a trigger axis in
settings if firmware reports a nonstandard resting value. Button and axis events
for the same trigger do not prematurely release the action. M1/M2 can be assigned
only if Android reports distinct button events; vendor-only mappings need hardware
inspection. Duplicate button assignments are cleared (reopen settings to refresh
all displayed labels).

Pointer speed uses elapsed time, independent of display refresh rate. The engine's
simulation timing and renderer are retained; no 120 FPS or Vulkan claim is made.
The Android video default uses Aspect Correct Fit instead of Fill. Android controls are laid out relative to the current surface. External displays,
pixel aspect ratio, and 4:3 output still require device testing through Exult's
video options. Existing touchscreen controls remain available and can be hidden
in Exult's own options. The adapter releases held inputs on pause, focus loss and
controller removal. Upstream background save behavior is retained.

## Validation status

The workflow runs input-math tests, compiles the APK, checks native ABI, manifest
and APK signature. A green build establishes compilation, not device correctness.
ADB diagnostics are deferred at the user's request. No actual Nova testing has
been performed. Before release, check:

- Cold/repeated launch and game import; internal and microSD ZIP selection.
- All eight directions and direction transitions, simultaneous stick and D-pad.
- Click, double-click, hold-drag, precision modifier, trigger range and duplicate events.
- Inventory/menu navigation without touch; dialogs and name entry.
- Button remapping, axis selection, idle drift, and persistence after restart.
- Pause/resume, screen sleep, and Bluetooth disconnect while moving or dragging.
- 60/120 Hz, 4:3 composition, USB-C external-display aspect ratio and touch coordinates.
- Speaker/headphone routing and extended play on the 8 GB model in both power modes.

Hardware-specific code cannot be finalized until the Nova's firmware/input/display
behavior is verified. No fan, thermal, power sysfs, or ROM modifications are made.
