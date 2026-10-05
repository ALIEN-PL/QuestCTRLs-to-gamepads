package com.questgamepad.android.input.provider

import com.questgamepad.android.input.model.QuestControllerType
import com.questgamepad.android.input.model.UnifiedGamepadState

interface InputProvider {
    val name: String
    val deviceType: QuestControllerType

    fun start(onStateUpdated: (UnifiedGamepadState) -> Unit)
    fun stop()

    /**
     * Send rumble / vibration to the controller hardware.
     * @param strongMagnitude 0..65535 (left motor)
     * @param weakMagnitude   0..65535 (right motor)
     */
    fun sendHapticFeedback(strongMagnitude: Int, weakMagnitude: Int)

    fun isConnected(): Boolean
    fun getBatteryLevels(): Pair<Int, Int> // (left, right) or (main, -1)
}
