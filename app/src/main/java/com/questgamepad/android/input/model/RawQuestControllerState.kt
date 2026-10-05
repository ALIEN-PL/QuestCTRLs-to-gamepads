package com.questgamepad.android.input.model

data class SingleControllerState(
    var stickX: Float = 0f,           // -1.0f .. 1.0f
    var stickY: Float = 0f,           // -1.0f .. 1.0f
    var stickClick: Boolean = false,
    var stickTouch: Boolean = false,

    // Face buttons: X, Y for left; A, B for right
    var buttonPrimaryClick: Boolean = false,    // X (left) or A (right)
    var buttonPrimaryTouch: Boolean = false,
    var buttonSecondaryClick: Boolean = false,  // Y (left) or B (right)
    var buttonSecondaryTouch: Boolean = false,

    // System button: Menu (left) or Oculus/System (right)
    var systemButtonClick: Boolean = false,

    // Trigger (Index finger)
    var trigger: Float = 0f,          // 0.0f .. 1.0f
    var triggerClick: Boolean = false,
    var triggerTouch: Boolean = false,

    // Grip (Middle finger squeeze)
    var grip: Float = 0f,             // 0.0f .. 1.0f
    var gripClick: Boolean = false,

    // Capacitive thumbrest
    var thumbrestTouch: Boolean = false,

    // Quest Pro specific
    var stylusForce: Float = 0f,      // 0.0f .. 1.0f
    var triggerCurl: Float = 0f,      // 0.0f .. 1.0f

    // Status
    var batteryPercent: Int = 100,
    var isConnected: Boolean = true,

    // 6DoF Motion & IMU (radians/sec and m/s^2)
    var gyroX: Float = 0f,
    var gyroY: Float = 0f,
    var gyroZ: Float = 0f,
    var accelX: Float = 0f,
    var accelY: Float = 0f,
    var accelZ: Float = 0f,
    var motionTimestampNs: Long = 0L
)

data class RawQuestControllerState(
    val left: SingleControllerState = SingleControllerState(),
    val right: SingleControllerState = SingleControllerState(),
    var deviceType: QuestControllerType = QuestControllerType.QUEST_3,
    var timestampNs: Long = System.nanoTime()
)
