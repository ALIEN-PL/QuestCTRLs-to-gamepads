package com.questgamepad.android.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.questgamepad.android.Prefs
import com.questgamepad.android.databinding.ActivityDebugBinding
import com.questgamepad.android.input.model.UnifiedGamepadState
import com.questgamepad.android.service.QuestGamepadService

class DebugActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugBinding
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private var isUpdating = true

    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var currentFps = 90

    private val sampleState = UnifiedGamepadState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        binding.toolbarDebug.setNavigationOnClickListener { finish() }

        binding.tvDebugProfile.text = prefs.targetProfile.displayName

        startUpdateLoop()
    }

    override fun onDestroy() {
        isUpdating = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startUpdateLoop() {
        val updateRunnable = object : Runnable {
            override fun run() {
                if (!isUpdating) return

                frameCount++
                val now = System.currentTimeMillis()
                if (now - lastFpsTimestamp >= 1000) {
                    currentFps = frameCount
                    frameCount = 0
                    lastFpsTimestamp = now
                    binding.tvPollRate.text = "$currentFps Hz"
                }

                // If service is running or test mode is on, update visualizer & telemetry
                binding.debugVisualizer.updateState(sampleState)

                val logText = buildString {
                    appendLine("── INPUT STATE ──")
                    appendLine("Source:       ${prefs.inputSource.displayName}")
                    appendLine("Target:       ${prefs.targetProfile.displayName}")
                    appendLine("Service:      ${if (QuestGamepadService.isRunning) "RUNNING" else "STOPPED"}")
                    appendLine()
                    appendLine("── ANALOG AXES ──")
                    appendLine("Left Stick:   X=${sampleState.leftStickX}  Y=${sampleState.leftStickY}")
                    appendLine("Right Stick:  X=${sampleState.rightStickX}  Y=${sampleState.rightStickY}")
                    appendLine("Triggers:     L2=${sampleState.leftTrigger}/255  R2=${sampleState.rightTrigger}/255")
                    appendLine("D-Pad Hat:    X=${sampleState.dpadX}  Y=${sampleState.dpadY}")
                    appendLine()
                    appendLine("── 6DoF MOTION (DUALSENSE IMU) ──")
                    appendLine("Gyro:         X=${sampleState.gyroX} Y=${sampleState.gyroY} Z=${sampleState.gyroZ}")
                    appendLine("Accel:        X=${sampleState.accelX} Y=${sampleState.accelY} Z=${sampleState.accelZ}")
                    appendLine()
                    appendLine("── BUTTON BITMASK ──")
                    appendLine("Bits:         0x${sampleState.buttons.toString(16).uppercase().padStart(8, '0')}")
                }
                binding.tvRawOutput.text = logText

                handler.postDelayed(this, 16) // ~60fps UI refresh
            }
        }
        handler.post(updateRunnable)
    }
}
