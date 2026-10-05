package com.questgamepad.android.input.provider

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.questgamepad.android.Prefs
import com.questgamepad.android.input.aggregator.QuestControllerAggregator
import com.questgamepad.android.input.aggregator.ControllerPose
import com.questgamepad.android.input.aggregator.VirtualControllerMotion
import com.questgamepad.android.input.model.QuestControllerType
import com.questgamepad.android.input.model.RawQuestControllerState
import com.questgamepad.android.input.model.UnifiedGamepadState

class QuestVrInputProvider(
    private val context: Context,
    override val deviceType: QuestControllerType = QuestControllerType.QUEST_3,
    val aggregator: QuestControllerAggregator = QuestControllerAggregator(),
    private val readHardwareState: (() -> IntArray?)? = null
) : InputProvider {

    private val TAG = "QuestVrInputProvider"
    override val name: String = "Meta Quest Controller Input Provider"

    @Volatile private var isRunning = false
    private var workerThread: Thread? = null
    private var callback: ((UnifiedGamepadState) -> Unit)? = null

    val rawState = RawQuestControllerState(deviceType = deviceType)
    private val unifiedState = UnifiedGamepadState(sourceDevice = deviceType)
    private val virtualMotion = VirtualControllerMotion()

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    override fun start(onStateUpdated: (UnifiedGamepadState) -> Unit) {
        if (isRunning) return
        callback = onStateUpdated
        isRunning = true

        detectQuestHardware()

        workerThread = Thread {
            Log.i(TAG, "Quest input polling loop started for ${deviceType.displayName}")
            // 90Hz polling cycle (~11ms) matching Quest display refresh rate
            val intervalNs = 11_111_111L

            while (isRunning) {
                val startNs = System.nanoTime()

                // Aggregate raw state into unified gamepad state
                synchronized(rawState) {
                    if (readHardwareState != null) {
                        val hardware = readHardwareState.invoke()
                        if (hardware == null || hardware.size < 11 || hardware[10] == 0) {
                            unifiedState.buttons = 0
                            unifiedState.screenshotPressed = false
                            unifiedState.leftStickX = 0
                            unifiedState.leftStickY = 0
                            unifiedState.rightStickX = 0
                            unifiedState.rightStickY = 0
                            unifiedState.leftTrigger = 0
                            unifiedState.rightTrigger = 0
                            unifiedState.dpadX = 0
                            unifiedState.dpadY = 0
                            unifiedState.motionTracked = false
                            unifiedState.gyroX = 0
                            unifiedState.gyroY = 0
                            unifiedState.gyroZ = 0
                            unifiedState.accelX = 0
                            unifiedState.accelY = 0
                            unifiedState.accelZ = 0
                            rawState.right.motionTimestampNs = 0L
                            virtualMotion.reset()
                        } else {
                            updateHardwareState(hardware)
                            aggregator.aggregate(rawState, unifiedState)
                            if (unifiedState.dpadX == 0) unifiedState.dpadX = hardware[7]
                            if (unifiedState.dpadY == 0) unifiedState.dpadY = hardware[8]
                        }
                    } else {
                        aggregator.aggregate(rawState, unifiedState)
                    }
                }

                callback?.invoke(unifiedState)

                val elapsedNs = System.nanoTime() - startNs
                val sleepNs = intervalNs - elapsedNs
                if (sleepNs > 0) {
                    val sleepMs = sleepNs / 1_000_000L
                    val sleepRemNs = (sleepNs % 1_000_000L).toInt()
                    try {
                        Thread.sleep(sleepMs, sleepRemNs)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            Log.i(TAG, "Quest input polling loop ended")
        }.apply {
            name = "QuestVrInputLoop"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    override fun stop() {
        isRunning = false
        workerThread?.interrupt()
        workerThread?.join(1000)
        workerThread = null
        callback = null
    }

    fun refreshPreferences(prefs: Prefs) {
        synchronized(rawState) {
            aggregator.mappings = prefs.loadMappings()
            aggregator.leftCalibration = prefs.getLeftStickCalibration()
            aggregator.rightCalibration = prefs.getRightStickCalibration()
            aggregator.dpadThreshold = prefs.dpadThreshold
            aggregator.enableGyroAiming = prefs.gyroAimingEnabled
            aggregator.gyroSensitivity = prefs.gyroSensitivity
        }
    }

    private fun updateHardwareState(hardware: IntArray) {
        rawState.left.stickX = hardware[0] / 32767f
        rawState.left.stickY = -hardware[1] / 32767f
        rawState.right.stickX = hardware[2] / 32767f
        rawState.right.stickY = -hardware[3] / 32767f
        rawState.left.trigger = hardware[4] / 255f
        rawState.right.trigger = hardware[5] / 255f
        val buttons = hardware[6]
        rawState.right.buttonPrimaryClick = buttons and (1 shl 0) != 0
        rawState.right.buttonSecondaryClick = buttons and (1 shl 1) != 0
        rawState.left.buttonPrimaryClick = buttons and (1 shl 2) != 0
        rawState.left.buttonSecondaryClick = buttons and (1 shl 3) != 0
        rawState.left.gripClick = buttons and (1 shl 4) != 0
        rawState.right.gripClick = buttons and (1 shl 5) != 0
        rawState.left.systemButtonClick = buttons and (1 shl 6) != 0
        rawState.right.systemButtonClick = buttons and ((1 shl 7) or (1 shl 8)) != 0
        val bothSticks = buttons and ((1 shl 9) or (1 shl 10)) == ((1 shl 9) or (1 shl 10))
        rawState.left.stickClick = !bothSticks && buttons and (1 shl 9) != 0
        rawState.right.stickClick = !bothSticks && buttons and (1 shl 10) != 0
        rawState.left.triggerClick = buttons and (1 shl 11) != 0
        rawState.right.triggerClick = buttons and (1 shl 12) != 0
        rawState.left.thumbrestTouch = hardware[9] != 0
    }

    override fun sendHapticFeedback(strongMagnitude: Int, weakMagnitude: Int) {
        // Forward rumble to Quest controller haptic actuators
        try {
            if (strongMagnitude == 0 && weakMagnitude == 0) {
                vibrator?.cancel()
                return
            }

            val maxMag = maxOf(strongMagnitude, weakMagnitude)
            // Scale 0..65535 to 1..255 for Android VibrationEffect
            val amplitude = ((maxMag / 65535f) * 255f).toInt().coerceIn(1, 255)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasAmplitudeControl() == true) {
                val effect = VibrationEffect.createOneShot(50, amplitude)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to apply haptic feedback", t)
        }
    }

    override fun isConnected(): Boolean {
        return rawState.left.isConnected || rawState.right.isConnected
    }

    override fun getBatteryLevels(): Pair<Int, Int> {
        return Pair(rawState.left.batteryPercent, rawState.right.batteryPercent)
    }

    private fun detectQuestHardware() {
        val model = Build.MODEL.lowercase()
        Log.i(TAG, "Device model: $model, Product: ${Build.PRODUCT}")
        rawState.deviceType = when {
            model.contains("pro") || Build.PRODUCT.lowercase().contains("pro") -> QuestControllerType.QUEST_PRO
            model.contains("quest 2") || Build.PRODUCT.lowercase().contains("hollywood") -> QuestControllerType.QUEST_2
            else -> QuestControllerType.QUEST_3
        }
    }

    /**
     * Update raw state from external VR / OpenXR runtime callbacks or Android input events.
     */
    fun updateLeftStick(x: Float, y: Float, click: Boolean, touch: Boolean) {
        synchronized(rawState) {
            rawState.left.stickX = x
            rawState.left.stickY = y
            rawState.left.stickClick = click
            rawState.left.stickTouch = touch
        }
    }

    fun updateRightStick(x: Float, y: Float, click: Boolean, touch: Boolean) {
        synchronized(rawState) {
            rawState.right.stickX = x
            rawState.right.stickY = y
            rawState.right.stickClick = click
            rawState.right.stickTouch = touch
        }
    }

    fun updateLeftButtons(xClick: Boolean, xTouch: Boolean, yClick: Boolean, yTouch: Boolean, menu: Boolean) {
        synchronized(rawState) {
            rawState.left.buttonPrimaryClick = xClick
            rawState.left.buttonPrimaryTouch = xTouch
            rawState.left.buttonSecondaryClick = yClick
            rawState.left.buttonSecondaryTouch = yTouch
            rawState.left.systemButtonClick = menu
        }
    }

    fun updateRightButtons(aClick: Boolean, aTouch: Boolean, bClick: Boolean, bTouch: Boolean, system: Boolean) {
        synchronized(rawState) {
            rawState.right.buttonPrimaryClick = aClick
            rawState.right.buttonPrimaryTouch = aTouch
            rawState.right.buttonSecondaryClick = bClick
            rawState.right.buttonSecondaryTouch = bTouch
            rawState.right.systemButtonClick = system
        }
    }

    fun updateLeftTriggers(trigger: Float, triggerClick: Boolean, grip: Float, gripClick: Boolean, thumbrest: Boolean) {
        synchronized(rawState) {
            rawState.left.trigger = trigger
            rawState.left.triggerClick = triggerClick
            rawState.left.grip = grip
            rawState.left.gripClick = gripClick
            rawState.left.thumbrestTouch = thumbrest
        }
    }

    fun updateRightTriggers(trigger: Float, triggerClick: Boolean, grip: Float, gripClick: Boolean, thumbrest: Boolean) {
        synchronized(rawState) {
            rawState.right.trigger = trigger
            rawState.right.triggerClick = triggerClick
            rawState.right.grip = grip
            rawState.right.gripClick = gripClick
            rawState.right.thumbrestTouch = thumbrest
        }
    }

    fun updateMotion(
        gyroX: Float, gyroY: Float, gyroZ: Float,
        accelX: Float, accelY: Float, accelZ: Float
    ) {
        synchronized(rawState) {
            if (!floatArrayOf(gyroX, gyroY, gyroZ, accelX, accelY, accelZ).all { it.isFinite() }) {
                rawState.right.motionTimestampNs = 0L
                return
            }
            rawState.right.gyroX = gyroX
            rawState.right.gyroY = gyroY
            rawState.right.gyroZ = gyroZ
            rawState.right.accelX = accelX
            rawState.right.accelY = accelY
            rawState.right.accelZ = accelZ
            rawState.right.motionTimestampNs = System.nanoTime()
        }
    }

    fun updateControllerPoses(timestampNs: Long, left: ControllerPose, right: ControllerPose): Boolean {
        synchronized(rawState) {
            if (System.nanoTime() - timestampNs !in 0L..100_000_000L) {
                virtualMotion.reset()
                rawState.right.motionTimestampNs = 0L
                return false
            }
            val motion = virtualMotion.update(timestampNs, left, right)
            if (motion == null) {
                rawState.right.motionTimestampNs = 0L
                return false
            }
            updateMotion(motion.gyro.x.toFloat(), motion.gyro.y.toFloat(), motion.gyro.z.toFloat(),
                motion.acceleration.x.toFloat(), motion.acceleration.y.toFloat(), motion.acceleration.z.toFloat())
            return true
        }
    }
}
