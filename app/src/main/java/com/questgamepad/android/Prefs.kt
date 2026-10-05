package com.questgamepad.android

import android.content.Context
import android.content.SharedPreferences
import com.questgamepad.android.input.aggregator.QuestControllerAggregator
import com.questgamepad.android.input.mapping.QuestSourceButton
import com.questgamepad.android.input.mapping.StickCalibration
import com.questgamepad.android.input.mapping.TargetGamepadButton
import com.questgamepad.android.input.model.QuestControllerType
import com.questgamepad.android.uinput.GamepadProfile
import org.json.JSONObject

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("quest_gamepad_prefs", Context.MODE_PRIVATE)

    var inputSource: QuestControllerType
        get() = QuestControllerType.fromId(sp.getInt("input_source", QuestControllerType.QUEST_3.id))
        set(value) = sp.edit().putInt("input_source", value.id).apply()

    var targetProfile: GamepadProfile
        get() = GamepadProfile.fromId(sp.getInt("target_profile", GamepadProfile.DUALSENSE.id))
        set(value) = sp.edit().putInt("target_profile", value.id).apply()

    var leftDeadzone: Float
        get() = sp.getFloat("left_deadzone", 0.08f)
        set(value) = sp.edit().putFloat("left_deadzone", value).apply()

    var rightDeadzone: Float
        get() = sp.getFloat("right_deadzone", 0.08f)
        set(value) = sp.edit().putFloat("right_deadzone", value).apply()

    var invertLeftY: Boolean
        get() = sp.getBoolean("invert_left_y", false)
        set(value) = sp.edit().putBoolean("invert_left_y", value).apply()

    var invertRightY: Boolean
        get() = sp.getBoolean("invert_right_y", false)
        set(value) = sp.edit().putBoolean("invert_right_y", value).apply()

    var gyroAimingEnabled: Boolean
        get() = sp.getBoolean("gyro_aiming_enabled", false)
        set(value) = sp.edit().putBoolean("gyro_aiming_enabled", value).apply()

    var gyroSensitivity: Float
        get() = sp.getFloat("gyro_sensitivity", 1.0f)
        set(value) = sp.edit().putFloat("gyro_sensitivity", value).apply()

    var rumbleIntensity: Int
        get() = sp.getInt("rumble_intensity", 100)
        set(value) = sp.edit().putInt("rumble_intensity", value).apply()

    var dpadThreshold: Float
        get() = sp.getFloat("dpad_threshold", 0.45f)
        set(value) = sp.edit().putFloat("dpad_threshold", value).apply()

    fun getLeftStickCalibration(): StickCalibration {
        return StickCalibration(
            innerDeadzone = leftDeadzone,
            invertY = invertLeftY
        )
    }

    fun getRightStickCalibration(): StickCalibration {
        return StickCalibration(
            innerDeadzone = rightDeadzone,
            invertY = invertRightY
        )
    }

    fun saveMappings(map: Map<QuestSourceButton, TargetGamepadButton>) {
        val json = JSONObject()
        for ((k, v) in map) {
            json.put(k.name, v.name)
        }
        sp.edit().putString("button_mappings", json.toString()).apply()
    }

    fun loadMappings(): Map<QuestSourceButton, TargetGamepadButton> {
        val str = sp.getString("button_mappings", null) ?: return QuestControllerAggregator.defaultMappings()
        return try {
            val json = JSONObject(str)
            val result = mutableMapOf<QuestSourceButton, TargetGamepadButton>()
            for (key in json.keys()) {
                val source = QuestSourceButton.valueOf(key)
                val target = TargetGamepadButton.valueOf(json.getString(key))
                result[source] = target
            }
            result
        } catch (_: Throwable) {
            QuestControllerAggregator.defaultMappings()
        }
    }
}
