# Changelog

## [v.1.1] - 2026-10-05

### Fixed

- Routed native Quest controller input through the mapping aggregator instead of forwarding a hardcoded layout.
- Applied saved button mappings, stick deadzones, Y-axis inversions and D-pad settings without restarting the service.
- Suppressed the original analog trigger when that trigger is explicitly remapped to another action.
- Released held virtual buttons and axes when forwarding is paused, including the L3 + R3 shortcut.
- Corrected DualShock 4 button codes, right-stick axes and trigger axes for the legacy Sony layout used by the tested Horizon OS version.
- Implemented mapped screenshot actions and limited touchpad-click selection to the supported DS4 profile.
- Serialized virtual-device lifecycle operations and terminated the Shizuku helper after device destruction.
- Replaced placeholder debug values with actual service telemetry.

### Added

- Automatic forwarding pause when Horizon system UI, the mapper or an unknown window has input focus. Focus is checked every 500 ms through Shizuku.
- Automatic resume when another application gains focus, without overriding a manual L3 + R3 pause.
- Separate Sony gyro and accelerometer uinput output for DS4 and DualSense, with Android-compatible units and sensor timestamps.
- Two-controller pose-to-IMU calculation using the hand baseline and controller orientations to derive virtual-pad rotation, angular velocity and acceleration including gravity.
- Motion history reset after tracking loss, invalid poses or sampling gaps over 100 ms; stale motion samples no longer affect gyro aiming.
- Debug indicators for forwarding state and motion-data availability.
- Eleven unit tests covering focus detection, remapping, D-pad modifiers, screenshots and synthetic motion.

### Changed

- Updated the motion controls and README to distinguish implemented IMU output from unavailable standalone controller tracking.
- Increased the Shizuku helper version to reload the updated native library and IPC interface.

### Known Limitations

- No controller-pose source is connected to the background service. The tested Quest 3 evdev nodes provide buttons and analog axes, not controller positions or IMU samples. Standalone gyro aiming and full 6DoF therefore remain unavailable.
- The pose conversion entry point is ready for a tracking integration, but that integration must supply valid controller poses while the target application is running.
- Sony sensor output requires an application that supports gamepad motion sensors; it does not reproduce PSVR1 camera-based positional tracking.
- Automatic pause depends on Horizon input-focus diagnostics and may require adjustment after OS updates.

### Verification

- Debug APK build and all 11 unit tests passed.
- Installed and checked on Quest 3: automatic pause/resume transitions, DS4 axis ranges, Sony sensor classification and preserved user mappings.
- Confirmed that one Shizuku helper remained after cleanup and final installation.
- End-to-end gyro control in a game was not verified because the background pose source is still missing.