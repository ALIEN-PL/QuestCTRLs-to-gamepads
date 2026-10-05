package com.questgamepad.android.input.model

data class UnifiedGamepadState(
    var buttons: Int = 0,             // Bitmask of GamepadButtons
    var leftStickX: Int = 0,          // -32768 .. 32767
    var leftStickY: Int = 0,          // -32768 .. 32767
    var rightStickX: Int = 0,         // -32768 .. 32767
    var rightStickY: Int = 0,         // -32768 .. 32767
    var leftTrigger: Int = 0,         // 0 .. 255
    var rightTrigger: Int = 0,        // 0 .. 255
    var dpadX: Int = 0,               // -1, 0, 1
    var dpadY: Int = 0,               // -1, 0, 1

    // Motion data (6-axis gyro/accel) for DualSense / DS4 motion aiming
    var gyroX: Int = 0,
    var gyroY: Int = 0,
    var gyroZ: Int = 0,
    var accelX: Int = 0,
    var accelY: Int = 0,
    var accelZ: Int = 0,

    // Status
    var batteryLeft: Int = 100,
    var batteryRight: Int = 100,
    var sourceDevice: QuestControllerType = QuestControllerType.QUEST_3
) {
    fun isButtonPressed(mask: Int): Boolean = (buttons and mask) != 0

    fun copyFrom(other: UnifiedGamepadState) {
        this.buttons = other.buttons
        this.leftStickX = other.leftStickX
        this.leftStickY = other.leftStickY
        this.rightStickX = other.rightStickX
        this.rightStickY = other.rightStickY
        this.leftTrigger = other.leftTrigger
        this.rightTrigger = other.rightTrigger
        this.dpadX = other.dpadX
        this.dpadY = other.dpadY
        this.gyroX = other.gyroX
        this.gyroY = other.gyroY
        this.gyroZ = other.gyroZ
        this.accelX = other.accelX
        this.accelY = other.accelY
        this.accelZ = other.accelZ
        this.batteryLeft = other.batteryLeft
        this.batteryRight = other.batteryRight
        this.sourceDevice = other.sourceDevice
    }
}
