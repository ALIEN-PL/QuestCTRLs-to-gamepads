package com.questgamepad.android.input.provider

import com.questgamepad.android.input.model.QuestControllerType
import com.questgamepad.android.input.model.UnifiedGamepadState
import com.questgamepad.android.uinput.GamepadButtons

class SteamControllerInputProvider : InputProvider {

    override val name: String = "Steam Controller (USB / BLE)"
    override val deviceType: QuestControllerType = QuestControllerType.STEAM_CONTROLLER

    private var callback: ((UnifiedGamepadState) -> Unit)? = null
    private val unifiedState = UnifiedGamepadState(sourceDevice = deviceType)
    private var isConnectedFlag = false

    override fun start(onStateUpdated: (UnifiedGamepadState) -> Unit) {
        callback = onStateUpdated
        isConnectedFlag = true
    }

    override fun stop() {
        isConnectedFlag = false
        callback = null
    }

    override fun sendHapticFeedback(strongMagnitude: Int, weakMagnitude: Int) {
        // Handled via BLE/USB rumble commands
    }

    override fun isConnected(): Boolean = isConnectedFlag

    override fun getBatteryLevels(): Pair<Int, Int> = Pair(unifiedState.batteryLeft, -1)

    fun onSteamState(
        buttonsRaw: Int,
        lx: Short, ly: Short,
        rx: Short, ry: Short,
        lt: Int, rt: Int,
        dpadX: Int, dpadY: Int,
        battery: Int
    ) {
        var mask = 0
        // Buttons mapping from Steam Controller bits to GamepadButtons
        if ((buttonsRaw and 0x01) != 0) mask = mask or GamepadButtons.A
        if ((buttonsRaw and 0x02) != 0) mask = mask or GamepadButtons.B
        if ((buttonsRaw and 0x04) != 0) mask = mask or GamepadButtons.X
        if ((buttonsRaw and 0x08) != 0) mask = mask or GamepadButtons.Y
        if ((buttonsRaw and 0x0200) != 0) mask = mask or GamepadButtons.RB
        if ((buttonsRaw and 0x080000) != 0) mask = mask or GamepadButtons.LB
        if ((buttonsRaw and 0x40) != 0) mask = mask or GamepadButtons.START
        if ((buttonsRaw and 0x4000) != 0) mask = mask or GamepadButtons.SELECT
        if ((buttonsRaw and 0x10000) != 0) mask = mask or GamepadButtons.MODE
        if ((buttonsRaw and 0x8000) != 0) mask = mask or GamepadButtons.THUMBL
        if ((buttonsRaw and 0x20) != 0) mask = mask or GamepadButtons.THUMBR

        unifiedState.buttons = mask
        unifiedState.leftStickX = lx.toInt()
        unifiedState.leftStickY = -ly.toInt()
        unifiedState.rightStickX = rx.toInt()
        unifiedState.rightStickY = -ry.toInt()
        unifiedState.leftTrigger = ((lt / 32767f) * 255f).toInt().coerceIn(0, 255)
        unifiedState.rightTrigger = ((rt / 32767f) * 255f).toInt().coerceIn(0, 255)
        unifiedState.dpadX = dpadX
        unifiedState.dpadY = dpadY
        unifiedState.batteryLeft = battery

        callback?.invoke(unifiedState)
    }
}
