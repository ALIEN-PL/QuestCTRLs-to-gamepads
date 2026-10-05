package com.questgamepad.android.uinput

import com.questgamepad.android.input.aggregator.ControllerPose
import com.questgamepad.android.input.aggregator.QuestControllerAggregator
import com.questgamepad.android.input.aggregator.VirtualControllerMotion
import com.questgamepad.android.input.mapping.QuestSourceButton
import com.questgamepad.android.input.mapping.TargetGamepadButton
import com.questgamepad.android.input.model.RawQuestControllerState
import com.questgamepad.android.input.model.UnifiedGamepadState
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class MotionAndMappingTest {
    @Test fun remapReplacesOriginalButtonAndCombinesHeldSources() {
        val raw = RawQuestControllerState()
        raw.right.buttonPrimaryClick = true
        raw.right.buttonSecondaryClick = true
        val output = UnifiedGamepadState()
        val aggregator = QuestControllerAggregator(mappings = mapOf(
            QuestSourceButton.A to TargetGamepadButton.X_SQUARE,
            QuestSourceButton.B to TargetGamepadButton.X_SQUARE))
        aggregator.aggregate(raw, output)
        assertEquals(GamepadButtons.X, output.buttons)
        raw.right.buttonPrimaryClick = false
        aggregator.aggregate(raw, output)
        assertEquals(GamepadButtons.X, output.buttons)
        raw.right.buttonSecondaryClick = false
        aggregator.aggregate(raw, output)
        assertEquals(0, output.buttons)
    }

    @Test fun triggerRemapDoesNotAlsoPullOriginalTrigger() {
        val raw = RawQuestControllerState()
        raw.left.trigger = 1f
        val output = UnifiedGamepadState()
        QuestControllerAggregator(mappings = mapOf(QuestSourceButton.LEFT_TRIGGER to TargetGamepadButton.A_CROSS))
            .aggregate(raw, output)
        assertEquals(GamepadButtons.A, output.buttons)
        assertEquals(0, output.leftTrigger)
    }

    @Test fun liveMappingReplacementDpadModifierAndScreenshot() {
        val raw = RawQuestControllerState()
        raw.right.buttonPrimaryClick = true
        val output = UnifiedGamepadState()
        val aggregator = QuestControllerAggregator()
        aggregator.aggregate(raw, output)
        assertEquals(GamepadButtons.A, output.buttons)
        aggregator.mappings = mapOf(QuestSourceButton.A to TargetGamepadButton.DPAD_UP)
        aggregator.aggregate(raw, output)
        assertEquals(0, output.buttons)
        assertEquals(-1, output.dpadY)
        raw.left.gripClick = true
        raw.left.stickX = 1f
        aggregator.mappings = mapOf(QuestSourceButton.LEFT_GRIP to TargetGamepadButton.DPAD_MODIFIER,
            QuestSourceButton.A to TargetGamepadButton.A_CROSS)
        aggregator.aggregate(raw, output)
        assertEquals(0, output.leftStickX)
        assertEquals(1, output.dpadX)
        assertEquals(1, output.dpadY)
        aggregator.mappings = mapOf(QuestSourceButton.A to TargetGamepadButton.SCREENSHOT)
        aggregator.aggregate(raw, output)
        assertTrue(output.screenshotPressed)
        raw.right.buttonPrimaryClick = false
        aggregator.aggregate(raw, output)
        assertFalse(output.screenshotPressed)
    }

    @Test fun stationaryPairReportsGravityButNoRotation() {
        val motion = VirtualControllerMotion()
        motion.update(1_000_000_000L, pose(-0.15), pose(0.15))
        val sample = motion.update(1_010_000_000L, pose(-0.15), pose(0.15))!!
        assertEquals(0.0, sample.gyro.norm, 0.000001)
        assertEquals(9.80665, sample.acceleration.y, 0.000001)
    }

    @Test fun translationDoesNotGenerateGyroscope() {
        val motion = VirtualControllerMotion()
        motion.update(1_000_000_000L, pose(-0.15), pose(0.15))
        val sample = motion.update(1_010_000_000L, pose(-0.15, depth = -0.01), pose(0.15, depth = -0.01))!!
        assertEquals(0.0, sample.gyro.norm, 0.000001)
    }

    @Test fun rotationAroundHandAxisUsesControllerOrientations() {
        val motion = VirtualControllerMotion()
        motion.update(1_000_000_000L, pose(-0.15), pose(0.15))
        val sample = motion.update(1_010_000_000L, pose(-0.15, angle = 0.01), pose(0.15, angle = 0.01))!!
        assertEquals(1.0, sample.gyro.x, 0.000001)
    }

    @Test fun trackingLossAndLongGapsResetHistory() {
        val motion = VirtualControllerMotion()
        motion.update(1_000_000_000L, pose(-0.15), pose(0.15))
        assertNull(motion.update(1_010_000_000L, pose(-0.15).copy(tracked = false), pose(0.15)))
        assertEquals(0.0, motion.update(1_020_000_000L, pose(-0.15, angle = 1.0), pose(0.15, angle = 1.0))!!.gyro.norm, 0.000001)
        assertEquals(0.0, motion.update(2_000_000_000L, pose(-0.15), pose(0.15))!!.gyro.norm, 0.000001)
        assertNull(motion.update(2_010_000_000L, pose(0.0), pose(0.0)))
    }

    @Test fun staleMotionDoesNotLeaveRightStickMoving() {
        val raw = RawQuestControllerState()
        raw.right.gyroY = 1f
        raw.right.motionTimestampNs = System.nanoTime() - 500_000_000L
        val output = UnifiedGamepadState()
        QuestControllerAggregator(enableGyroAiming = true).aggregate(raw, output)
        assertEquals(0, output.rightStickX)
        assertEquals(0, output.gyroY)
        assertFalse(output.motionTracked)
    }

    private fun pose(horizontal: Double, depth: Double = 0.0, angle: Double = 0.0) =
        ControllerPose(horizontal, 0.0, depth, sin(angle / 2), 0.0, 0.0, cos(angle / 2))
}