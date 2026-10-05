# QuestCTRLs to Gamepads (Virtual DualSense & Xbox for Meta Quest)

> **Fork Information:**  
> This project is a fork of the excellent [SteamController-Android](https://github.com/SonicDX12/SteamController-Android) created by [Kevin (SonicDX12)](https://github.com/SonicDX12).  
> While the upstream project focused on Valve Steam Controller support over USB OTG/BLE, this fork massively expands the architecture to natively support **Meta Quest VR controllers (Quest 2, Quest 3 / 3S, Quest Pro)** as high-performance virtual gamepads on **Meta Horizon OS** (Android) via Linux `/dev/uinput` and Shizuku. Steam Controller and generic gamepad modes remain fully supported.

---

**QuestCTRLs to Gamepads** maps **Meta Quest 2**, **Meta Quest 3 / 3S**, and **Meta Quest Pro** controllers (as well as the **Valve Steam Controller** and generic gamepads) into a fully functional virtual **Sony PlayStation 5 DualSense**, **DualShock 4**, **Xbox 360**, **Xbox One**, or **Desktop Mouse/Keyboard**.

Designed specifically for **Meta Horizon OS** (Android) and standalone VR headsets, allowing you to play flat 2D Android games (e.g. *Genshin Impact*, *Minecraft*, *Dead Cells*), retro emulators (*RetroArch*, *PPSSPP*, *AetherSX2*, *Dolphin*), and Cloud Gaming services (*GeForce NOW*, *Xbox Cloud Gaming*, *PlayStation Remote Play*) using your Quest Touch controllers as a real unified physical gamepad!

---

## 🎮 Key Features

### 1. Multi-Controller Support
- **Meta Quest 3 / 3S (Touch Plus)**: Direct hardware evdev input aggregation, unified dual-stick tracking, ultra-low latency (<1 ms).
- **Meta Quest Pro (Touch Pro)**: Self-tracking 6DoF, stylus pressure, trigger curl pinch.
- **Meta Quest 2 (Touch v3)**: Full dual-controller mapping with tracking rings.
- **Valve Steam Controller**: USB OTG and Bluetooth LE support (integrated from `SteamController-Android`).
- **Generic Gamepad / HID**: Remap any physical controller to DualSense / Xbox layout.
- **Low-Latency Network Streamer (UDP port 52525)**: Send/receive Quest controller packets wirelessly to a companion device or PC.

### 2. Virtual Gamepad Emulation Profiles (`/dev/uinput`)
- **Sony DualSense (PS5)** (`VID 0x054C`, `PID 0x0CE6`) — Cross, Circle, Square, Triangle, L1, R1, L2, R2, L3, R3, Create, Options, PS Button (matches Android's official `/system/usr/keylayout/Vendor_054c_Product_0ce6.kl`).
- **Sony DualShock 4 (PS4)** (`VID 0x054C`, `PID 0x05C4`).
- **Microsoft Xbox 360** (`VID 0x045E`, `PID 0x028E`).
- **Microsoft Xbox One** (`VID 0x045E`, `PID 0x02EA`).
- **Desktop Mode** (Virtual Mouse + Keyboard): Right stick/trackpad drives cursor, buttons trigger Enter, Back, Esc, Volume, Tab.

### 3. Intelligent Quest Dual-Controller Aggregation
On Quest, the user holds two independent controllers (Left and Right). QuestCTRLs to Gamepads unifies them into a single standard 16-button gamepad:
- **Left Stick** ➔ Gamepad Left Stick (LS / L3 Click)
- **Right Stick** ➔ Gamepad Right Stick (RS / R3 Click)
- **Right Face Buttons (A / B)** ➔ DualSense Cross (✕) / Circle (○) [Xbox A / B]
- **Left Face Buttons (X / Y)** ➔ DualSense Square (□) / Triangle (△) [Xbox X / Y]
- **Index Triggers** ➔ Analog L2 / R2 (0–255)
- **Grip Triggers** ➔ L1 / R1 Bumpers (or D-Pad modifier)
- **Menu Button (Left)** ➔ Create / Share / View
- **Oculus Button (Right)** ➔ Options / Start / PS Guide

### 4. D-Pad & Quick-Toggle Features
- **Quick-Toggle Shortcut (L3 + R3)**: Simultaneously clicking both thumbsticks toggles gamepad forwarding on the fly, immediately restoring the VR pointer for system navigation without closing your game.
- **D-Pad Modifier Shift**: Touching the **Left Thumbrest** transforms the **Left Stick** or buttons into **D-Pad Up/Down/Left/Right**.

### 5. Gyro Motion Aiming & Force Feedback
- **DualSense Motion Sensor**: Quest 6DoF controller orientation and angular velocity are fed into DualSense gyro aiming.
- **Haptic Rumble Pipeline**: Game vibration events intercepted via Linux `FF_RUMBLE` on `/dev/uinput` are forwarded directly as haptic pulses to Quest controller vibration motors.

---

## 🚀 Setup & Installation on Meta Quest

### Prerequisites
1. **Developer Mode** enabled on your Meta Quest headset.
2. [Shizuku](https://shizuku.rikka.app/) installed on the headset (provides `/dev/uinput` access without root).

### Step-by-Step Guide

#### 1. Install the APK
Install `QuestCTRLs to Gamepads` onto your Quest using ADB or SideQuest:
```bash
adb install app-debug.apk
```

#### 2. Start Shizuku on Quest
After headset reboot, ensure Shizuku is running (can be started wirelessly or via ADB):
```bash
adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
```

#### 3. Launch & Grant Permission
1. Open **QuestCTRLs to Gamepads** in your Quest Library (under *Unknown Sources*).
2. Tap the **Shizuku status banner** to grant permission.
3. Select your target profile (e.g. **DualSense (PS5)**) and controller type (**Quest 3 / Pro / 2**).
4. Tap **START SERVICE**.

Now launch your favorite 2D Android game, emulator, or Cloud Gaming app in Horizon OS — the game will detect a genuine physical Sony DualSense or Xbox gamepad!

---

## 🛠️ Project Structure

```
QuestCTRLs-to-gamepads/
├── app/
│   ├── src/main/
│   │   ├── aidl/com/questgamepad/android/uinput/
│   │   │   └── IUInputService.aidl            # IPC interface for Shizuku shell service
│   │   ├── cpp/
│   │   │   ├── CMakeLists.txt                 # NDK build script
│   │   │   └── uinput_jni.cpp                 # Low-latency evdev reader and /dev/uinput driver
│   │   ├── java/com/questgamepad/android/
│   │   │   ├── MainActivity.kt                # Main dashboard & live visual preview
│   │   │   ├── Prefs.kt                       # Configuration store & JSON mappings
│   │   │   ├── input/
│   │   │   │   ├── aggregator/
│   │   │   │   │   └── QuestControllerAggregator.kt # Merges L+R controllers, D-pad modifier, gyro
│   │   │   │   ├── mapping/
│   │   │   │   │   ├── ButtonMapping.kt       # Remapping engine
│   │   │   │   │   ├── QuestSourceButton.kt   # Physical Quest buttons
│   │   │   │   │   ├── StickCalibration.kt    # Deadzones & sensitivity curves
│   │   │   │   │   └── TargetGamepadButton.kt # Target DualSense / Xbox buttons
│   │   │   │   ├── model/
│   │   │   │   │   ├── QuestControllerType.kt # Quest 2, 3, Pro, Steam, Generic
│   │   │   │   │   ├── RawQuestControllerState.kt
│   │   │   │   │   └── UnifiedGamepadState.kt
│   │   │   │   └── provider/
│   │   │   │       ├── InputProvider.kt       # Extensible input provider interface
│   │   │   │       ├── QuestVrInputProvider.kt# Horizon OS / OpenXR input reader
│   │   │   │       ├── QuestPanelInputProvider.kt # 2D Panel event reader
│   │   │   │       ├── SteamControllerInputProvider.kt # USB OTG / BLE Steam Controller
│   │   │   │       ├── GenericGamepadInputProvider.kt # Standard Android gamepad
│   │   │   │       └── NetworkStreamingProvider.kt # Low-latency UDP socket streamer
│   │   │   ├── service/
│   │   │   │   └── QuestGamepadService.kt     # Foreground service with persistent notification
│   │   │   ├── ui/
│   │   │   │   ├── CalibrationActivity.kt     # Radial deadzones, invert Y, rumble test
│   │   │   │   ├── DebugActivity.kt           # Real-time Hz, battery, axes & telemetry HUD
│   │   │   │   ├── MappingActivity.kt         # Custom button remapping UI
│   │   │   │   └── QuestVisualControllerView.kt # Custom Canvas 2D live controller preview
│   │   │   └── uinput/
│   │   │       ├── GamepadDescriptors.kt      # Button masks & profiles
│   │   │       ├── GamepadProfile.kt          # DualSense, DS4, Xbox 360, Xbox One, Desktop
│   │   │       ├── UInputGamepad.kt           # High-level Shizuku manager
│   │   │       ├── UInputNative.kt            # JNI bindings
│   │   │       └── UInputService.kt           # UID 2000 UserService
│   │   └── res/                               # Layouts, vector drawables, Material 3 themes
│   └── build.gradle.kts
├── settings.gradle.kts
└── build.gradle.kts
```

---

## 🔨 Building from Source

Requires:
- Android SDK (API 34+)
- Android NDK 26.1+
- CMake 3.22.1+
- JDK 17+

Run:
```bash
./gradlew assembleDebug
```
The output APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📜 Credits & License

- **Original Project:** [SteamController-Android](https://github.com/SonicDX12/SteamController-Android) by [Kevin (SonicDX12)](https://github.com/SonicDX12).
- **Fork Maintainer:** [ALIEN-PL](https://github.com/ALIEN-PL).
- **License:** Licensed under the [MIT License](LICENSE).
  - Copyright (c) 2026 Kevin (SonicDX12)
  - Copyright (c) 2026 ALIEN-PL

