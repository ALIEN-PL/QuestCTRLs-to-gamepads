package com.questgamepad.android.input.mapping

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class StickCalibration(
    val innerDeadzone: Float = 0.08f,    // 8% inner radial deadzone
    val outerDeadzone: Float = 0.95f,    // 95% outer threshold
    val antiDeadzone: Float = 0.0f,      // offset to jump past game's internal deadzone
    val invertY: Boolean = false,
    val invertX: Boolean = false,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val sensitivity: Float = 1.0f
) {
    companion object {
        val DEFAULT = StickCalibration()
    }

    /**
     * Applies radial deadzone, scaling, anti-deadzone, and inversions to raw (-1.0 .. 1.0) input.
     * Returns a Pair(x, y) normalized to -1.0 .. 1.0.
     */
    fun apply(rawX: Float, rawY: Float): Pair<Float, Float> {
        val centeredX = rawX - centerX
        val centeredY = rawY - centerY

        val magnitude = sqrt(centeredX * centeredX + centeredY * centeredY)
        if (magnitude <= innerDeadzone) {
            return Pair(0f, 0f)
        }

        val angle = atan2(centeredY, centeredX)

        // Rescale magnitude from [innerDeadzone, outerDeadzone] to [0.0, 1.0]
        val clampedMag = min(magnitude, outerDeadzone)
        val normalizedMag = (clampedMag - innerDeadzone) / (outerDeadzone - innerDeadzone)

        // Add anti-deadzone if set, and apply sensitivity
        val finalMag = min(1.0f, (antiDeadzone + (1.0f - antiDeadzone) * normalizedMag) * sensitivity)

        var outX = cos(angle) * finalMag
        var outY = sin(angle) * finalMag

        if (invertX) outX = -outX
        if (invertY) outY = -outY

        return Pair(outX, outY)
    }
}
