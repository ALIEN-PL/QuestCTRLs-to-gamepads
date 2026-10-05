package com.questgamepad.android.input.provider

import android.view.KeyEvent
import android.view.MotionEvent
import com.questgamepad.android.input.model.QuestControllerType
import com.questgamepad.android.input.model.UnifiedGamepadState

class QuestPanelInputProvider(
    private val vrProvider: QuestVrInputProvider
) : InputProvider by vrProvider {

    override val name: String = "Quest 2D Panel Event Provider"

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK ||
            (event.source and android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD) {

            val lx = event.getAxisValue(MotionEvent.AXIS_X)
            val ly = event.getAxisValue(MotionEvent.AXIS_Y)
            val rx = event.getAxisValue(MotionEvent.AXIS_Z)
            val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
            val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER).coerceAtLeast(event.getAxisValue(MotionEvent.AXIS_BRAKE))
            val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER).coerceAtLeast(event.getAxisValue(MotionEvent.AXIS_GAS))

            vrProvider.updateLeftStick(lx, ly, false, false)
            vrProvider.updateRightStick(rx, ry, false, false)
            vrProvider.updateLeftTriggers(lt, lt > 0.85f, 0f, false, false)
            vrProvider.updateRightTriggers(rt, rt > 0.85f, 0f, false, false)
            return true
        }
        return false
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val pressed = event.action == KeyEvent.ACTION_DOWN
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> { vrProvider.updateRightButtons(pressed, false, false, false, false); true }
            KeyEvent.KEYCODE_BUTTON_B -> { vrProvider.updateRightButtons(false, false, pressed, false, false); true }
            KeyEvent.KEYCODE_BUTTON_X -> { vrProvider.updateLeftButtons(pressed, false, false, false, false); true }
            KeyEvent.KEYCODE_BUTTON_Y -> { vrProvider.updateLeftButtons(false, false, pressed, false, false); true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { vrProvider.updateLeftTriggers(0f, false, if (pressed) 1.0f else 0f, pressed, false); true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { vrProvider.updateRightTriggers(0f, false, if (pressed) 1.0f else 0f, pressed, false); true }
            KeyEvent.KEYCODE_BUTTON_THUMBL -> { vrProvider.updateLeftStick(0f, 0f, pressed, false); true }
            KeyEvent.KEYCODE_BUTTON_THUMBR -> { vrProvider.updateRightStick(0f, 0f, pressed, false); true }
            KeyEvent.KEYCODE_BUTTON_START -> { vrProvider.updateRightButtons(false, false, false, false, pressed); true }
            KeyEvent.KEYCODE_BUTTON_SELECT -> { vrProvider.updateLeftButtons(false, false, false, false, pressed); true }
            else -> false
        }
    }
}
