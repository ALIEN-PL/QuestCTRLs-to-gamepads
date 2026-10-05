package com.questgamepad.android.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.appcompat.app.AppCompatActivity
import com.questgamepad.android.Prefs
import com.questgamepad.android.R
import com.questgamepad.android.service.QuestGamepadService
import com.questgamepad.android.databinding.ActivityCalibrationBinding

class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        binding.toolbarCalibration.setNavigationOnClickListener { finish() }

        // Left deadzone
        val leftPct = (prefs.leftDeadzone * 100).toInt()
        binding.sliderLeftDeadzone.value = leftPct.toFloat()
        binding.tvLeftDeadzoneVal.text = "$leftPct%"
        binding.sliderLeftDeadzone.addOnChangeListener { _, value, _ ->
            binding.tvLeftDeadzoneVal.text = "${value.toInt()}%"
            prefs.leftDeadzone = value / 100f
        }

        binding.switchInvertLeftY.isChecked = prefs.invertLeftY
        binding.switchInvertLeftY.setOnCheckedChangeListener { _, isChecked ->
            prefs.invertLeftY = isChecked
        }

        // Right deadzone
        val rightPct = (prefs.rightDeadzone * 100).toInt()
        binding.sliderRightDeadzone.value = rightPct.toFloat()
        binding.tvRightDeadzoneVal.text = "$rightPct%"
        binding.sliderRightDeadzone.addOnChangeListener { _, value, _ ->
            binding.tvRightDeadzoneVal.text = "${value.toInt()}%"
            prefs.rightDeadzone = value / 100f
        }

        binding.switchInvertRightY.isChecked = prefs.invertRightY
        binding.switchInvertRightY.setOnCheckedChangeListener { _, isChecked ->
            prefs.invertRightY = isChecked
        }

        // Gyro Aiming
        binding.switchGyroAiming.isChecked = prefs.gyroAimingEnabled
        binding.switchGyroAiming.setOnCheckedChangeListener { _, isChecked ->
            prefs.gyroAimingEnabled = isChecked
        }

        binding.sliderGyroSens.value = prefs.gyroSensitivity
        binding.sliderGyroSens.addOnChangeListener { _, value, _ ->
            prefs.gyroSensitivity = value
        }
        binding.tvMotionStatus.text = getString(R.string.motion_source_unavailable)
        binding.switchGyroAiming.isEnabled = QuestGamepadService.latestState.motionTracked
        binding.sliderGyroSens.isEnabled = QuestGamepadService.latestState.motionTracked

        // Rumble
        binding.sliderRumble.value = prefs.rumbleIntensity.toFloat()
        binding.sliderRumble.addOnChangeListener { _, value, _ ->
            prefs.rumbleIntensity = value.toInt()
        }

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        binding.btnTestRumble.setOnClickListener {
            val amp = ((prefs.rumbleIntensity / 100f) * 255f).toInt().coerceIn(1, 255)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasAmplitudeControl() == true) {
                vibrator.vibrate(VibrationEffect.createOneShot(180, amp))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(180)
            }
        }
    }
}
