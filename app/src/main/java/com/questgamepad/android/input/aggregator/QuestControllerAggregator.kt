package com.questgamepad.android.input.aggregator

import com.questgamepad.android.input.mapping.QuestSourceButton
import com.questgamepad.android.input.mapping.StickCalibration
import com.questgamepad.android.input.mapping.TargetGamepadButton
import com.questgamepad.android.input.model.QuestControllerType
import com.questgamepad.android.input.model.RawQuestControllerState
import com.questgamepad.android.input.model.UnifiedGamepadState
import com.questgamepad.android.uinput.GamepadButtons
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class QuestControllerAggregator(
    var leftCalibration: StickCalibration = StickCalibration.DEFAULT,
    var rightCalibration: StickCalibration = StickCalibration.DEFAULT,
    var mappings: Map<QuestSourceButton, TargetGamepadButton> = defaultMappings(),
    var enableGyroAiming: Boolean = false,
    var gyroSensitivity: Float = 1.0f,
    var dpadThreshold: Float = 0.45f
) {

    companion object {
        fun defaultMappings(): Map<QuestSourceButton, TargetGamepadButton> = mapOf(
            // Face buttons
            QuestSourceButton.A to TargetGamepadButton.A_CROSS,
            QuestSourceButton.B to TargetGamepadButton.B_CIRCLE,
            QuestSourceButton.X to TargetGamepadButton.X_SQUARE,
            QuestSourceButton.Y to TargetGamepadButton.Y_TRIANGLE,

            // Shoulders & Grips
            QuestSourceButton.LEFT_GRIP  to TargetGamepadButton.L1_LB,
            QuestSourceButton.RIGHT_GRIP to TargetGamepadButton.R1_RB,

            // Sticks
            QuestSourceButton.LEFT_STICK_CLICK  to TargetGamepadButton.L3_THUMBL,
            QuestSourceButton.RIGHT_STICK_CLICK to TargetGamepadButton.R3_THUMBR,

            // System
            QuestSourceButton.MENU   to TargetGamepadButton.SELECT_CREATE,
            QuestSourceButton.SYSTEM to TargetGamepadButton.START_OPTIONS,

            // Thumbrest / Modifier
            QuestSourceButton.LEFT_THUMBREST to TargetGamepadButton.DPAD_MODIFIER
        )
    }

    private val STICK_SCALE = 32767f
    private val TRIG_SCALE = 255f

    /**
     * Aggregates raw Quest Left + Right controller states into a single unified gamepad state.
     */
    fun aggregate(
        raw: RawQuestControllerState,
        output: UnifiedGamepadState
    ) {
        var buttonsMask = 0
        var forceLeftTrigger = false
        var forceRightTrigger = false
        var isModifierActive = false
        var dpadUp = false
        var dpadDown = false
        var dpadLeft = false
        var dpadRight = false
        var screenshotPressed = false

        // Check if D-pad modifier is active from any mapped button
        for ((source, target) in mappings) {
            val pressed = isSourcePressed(raw, source)
            if (pressed && target.isDpadModifier) {
                isModifierActive = true
                break
            }
        }

        // Process all mappings
        for ((source, target) in mappings) {
            val pressed = isSourcePressed(raw, source)
            if (!pressed) continue

            if (target.isDpadModifier) continue
            if (target == TargetGamepadButton.SCREENSHOT) {
                screenshotPressed = true
                continue
            }

            if (target.mask > 0) {
                // If modifier is active and this is a face button, translate to D-Pad!
                if (isModifierActive && (source == QuestSourceButton.X || source == QuestSourceButton.Y ||
                            source == QuestSourceButton.A || source == QuestSourceButton.B)) {
                    when (source) {
                        QuestSourceButton.Y -> dpadUp = true
                        QuestSourceButton.A -> dpadDown = true
                        QuestSourceButton.X -> dpadLeft = true
                        QuestSourceButton.B -> dpadRight = true
                        else -> {}
                    }
                } else {
                    buttonsMask = buttonsMask or target.mask
                }
            } else if (target.triggerSide == 1) {
                forceLeftTrigger = true
            } else if (target.triggerSide == 2) {
                forceRightTrigger = true
            } else if (target.dpadDirection > 0) {
                when (target.dpadDirection) {
                    1 -> dpadUp = true
                    2 -> dpadDown = true
                    3 -> dpadLeft = true
                    4 -> dpadRight = true
                }
            }
        }

        // Apply stick calibration
        val (calLx, calLy) = leftCalibration.apply(raw.left.stickX, raw.left.stickY)
        val (calRx, calRy) = rightCalibration.apply(raw.right.stickX, raw.right.stickY)

        var finalLx = (calLx * STICK_SCALE).toInt().coerceIn(-32768, 32767)
        var finalLy = (-calLy * STICK_SCALE).toInt().coerceIn(-32768, 32767)
        var finalRx = (calRx * STICK_SCALE).toInt().coerceIn(-32768, 32767)
        var finalRy = (-calRy * STICK_SCALE).toInt().coerceIn(-32768, 32767)

        // If D-Pad modifier is held and Left Stick is pushed, convert Left Stick to D-Pad Hat
        if (isModifierActive) {
            if (calLy > dpadThreshold) dpadUp = true
            if (calLy < -dpadThreshold) dpadDown = true
            if (calLx < -dpadThreshold) dpadLeft = true
            if (calLx > dpadThreshold) dpadRight = true

            // Zero out analog stick so it doesn't move character while navigating D-pad menu
            finalLx = 0
            finalLy = 0
        }

        // Calculate D-Pad HAT X and Y (-1, 0, 1)
        var dpadHatX = 0
        var dpadHatY = 0
        if (dpadLeft && !dpadRight) dpadHatX = -1
        else if (dpadRight && !dpadLeft) dpadHatX = 1

        if (dpadUp && !dpadDown) dpadHatY = -1
        else if (dpadDown && !dpadUp) dpadHatY = 1

        // Triggers (Index finger)
        var ltVal = (raw.left.trigger * TRIG_SCALE).toInt().coerceIn(0, 255)
        var rtVal = (raw.right.trigger * TRIG_SCALE).toInt().coerceIn(0, 255)
        val leftTriggerMapping = mappings[QuestSourceButton.LEFT_TRIGGER]
        val rightTriggerMapping = mappings[QuestSourceButton.RIGHT_TRIGGER]
        if (leftTriggerMapping != null && leftTriggerMapping != TargetGamepadButton.L2_TRIGGER_FULL) ltVal = 0
        if (rightTriggerMapping != null && rightTriggerMapping != TargetGamepadButton.R2_TRIGGER_FULL) rtVal = 0
        if (forceLeftTrigger) ltVal = 255
        if (forceRightTrigger) rtVal = 255

        val motionAge = System.nanoTime() - raw.right.motionTimestampNs
        val motionTracked = raw.right.motionTimestampNs > 0L && motionAge in 0L..100_000_000L
        // Gyro aiming: right controller angular velocity added to right stick or motion frame
        if (enableGyroAiming && raw.right.isConnected && motionTracked) {
            val gyroDeltaX = (raw.right.gyroY * gyroSensitivity * 1000f).toInt()
            val gyroDeltaY = (-raw.right.gyroX * gyroSensitivity * 1000f).toInt()
            finalRx = (finalRx + gyroDeltaX).coerceIn(-32768, 32767)
            finalRy = (finalRy + gyroDeltaY).coerceIn(-32768, 32767)
        }

        // Fill output
        output.buttons = buttonsMask
        output.screenshotPressed = screenshotPressed
        output.leftStickX = finalLx
        output.leftStickY = finalLy
        output.rightStickX = finalRx
        output.rightStickY = finalRy
        output.leftTrigger = ltVal
        output.rightTrigger = rtVal
        output.dpadX = dpadHatX
        output.dpadY = dpadHatY

        // Motion 6-axis data (scaled for DualSense IMU)
        output.motionTracked = motionTracked
        output.gyroX = if (motionTracked) (raw.right.gyroX * 1000f).toInt() else 0
        output.gyroY = if (motionTracked) (raw.right.gyroY * 1000f).toInt() else 0
        output.gyroZ = if (motionTracked) (raw.right.gyroZ * 1000f).toInt() else 0
        output.accelX = if (motionTracked) (raw.right.accelX * 1000f).toInt() else 0
        output.accelY = if (motionTracked) (raw.right.accelY * 1000f).toInt() else 0
        output.accelZ = if (motionTracked) (raw.right.accelZ * 1000f).toInt() else 0

        output.batteryLeft = raw.left.batteryPercent
        output.batteryRight = raw.right.batteryPercent
        output.sourceDevice = raw.deviceType
    }

    private fun isSourcePressed(raw: RawQuestControllerState, source: QuestSourceButton): Boolean {
        return when (source) {
            QuestSourceButton.A -> raw.right.buttonPrimaryClick
            QuestSourceButton.B -> raw.right.buttonSecondaryClick
            QuestSourceButton.X -> raw.left.buttonPrimaryClick
            QuestSourceButton.Y -> raw.left.buttonSecondaryClick
            QuestSourceButton.LEFT_TRIGGER -> raw.left.trigger >= 0.85f || raw.left.triggerClick
            QuestSourceButton.RIGHT_TRIGGER -> raw.right.trigger >= 0.85f || raw.right.triggerClick
            QuestSourceButton.LEFT_GRIP -> raw.left.grip >= 0.50f || raw.left.gripClick
            QuestSourceButton.RIGHT_GRIP -> raw.right.grip >= 0.50f || raw.right.gripClick
            QuestSourceButton.LEFT_STICK_CLICK -> raw.left.stickClick
            QuestSourceButton.RIGHT_STICK_CLICK -> raw.right.stickClick
            QuestSourceButton.MENU -> raw.left.systemButtonClick
            QuestSourceButton.SYSTEM -> raw.right.systemButtonClick
            QuestSourceButton.LEFT_THUMBREST -> raw.left.thumbrestTouch
            QuestSourceButton.RIGHT_THUMBREST -> raw.right.thumbrestTouch
            QuestSourceButton.PRO_STYLUS -> raw.right.stylusForce > 0.2f
        }
    }
}
