package com.questgamepad.android.input.provider

import android.view.KeyEvent
import android.view.MotionEvent
import com.questgamepad.android.input.model.QuestControllerType
import com.questgamepad.android.input.model.UnifiedGamepadState
import com.questgamepad.android.uinput.GamepadButtons

class GenericGamepadInputProvider : InputProvider {

    override val name: String = "Generic Android Gamepad"
    override val deviceType: QuestControllerType = QuestControllerType.GENERIC_GAMEPAD

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

    override fun sendHapticFeedback(strongMagnitude: Int, weakMagnitude: Int) {}

    override fun isConnected(): Boolean = isConnectedFlag

    override fun getBatteryLevels(): Pair<Int, Int> = Pair(100, -1)

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val lx = (event.getAxisValue(MotionEvent.AXIS_X) * 32767f).toInt().coerceIn(-32768, 32767)
        val ly = (event.getAxisValue(MotionEvent.AXIS_Y) * 32767f).toInt().coerceIn(-32768, 32767)
        val rx = (event.getAxisValue(MotionEvent.AXIS_Z) * 32767f).toInt().coerceIn(-32768, 32767)
        val ry = (event.getAxisValue(MotionEvent.AXIS_RZ) * 32767f).toInt().coerceIn(-32768, 32767)
        val lt = (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) * 255f).toInt().coerceIn(0, 255)
        val rt = (event.getAxisValue(MotionEvent.AXIS_RTRIGGER) * 255f).toInt().coerceIn(0, 255)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X).toInt()
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y).toInt()

        unifiedState.leftStickX = lx
        unifiedState.leftStickY = ly
        unifiedState.rightStickX = rx
        unifiedState.rightStickY = ry
        unifiedState.leftTrigger = lt
        unifiedState.rightTrigger = rt
        unifiedState.dpadX = hatX
        unifiedState.dpadY = hatY

        callback?.invoke(unifiedState)
        return true
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val pressed = event.action == KeyEvent.ACTION_DOWN
        val mask = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> GamepadButtons.A
            KeyEvent.KEYCODE_BUTTON_B -> GamepadButtons.B
            KeyEvent.KEYCODE_BUTTON_X -> GamepadButtons.X
            KeyEvent.KEYCODE_BUTTON_Y -> GamepadButtons.Y
            KeyEvent.KEYCODE_BUTTON_L1 -> GamepadButtons.LB
            KeyEvent.KEYCODE_BUTTON_R1 -> GamepadButtons.RB
            KeyEvent.KEYCODE_BUTTON_THUMBL -> GamepadButtons.THUMBL
            KeyEvent.KEYCODE_BUTTON_THUMBR -> GamepadButtons.THUMBR
            KeyEvent.KEYCODE_BUTTON_START -> GamepadButtons.START
            KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadButtons.SELECT
            KeyEvent.KEYCODE_BUTTON_MODE -> GamepadButtons.MODE
            else -> 0
        }

        if (mask != 0) {
            unifiedState.buttons = if (pressed) {
                unifiedState.buttons or mask
            } else {
                unifiedState.buttons and mask.inv()
            }
            callback?.invoke(unifiedState)
            return true
        }
        return false
    }
}
