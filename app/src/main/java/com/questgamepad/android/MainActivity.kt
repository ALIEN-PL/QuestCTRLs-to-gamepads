package com.questgamepad.android

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.questgamepad.android.databinding.ActivityMainBinding
import com.questgamepad.android.input.model.QuestControllerType
import com.questgamepad.android.service.QuestGamepadService
import com.questgamepad.android.ui.CalibrationActivity
import com.questgamepad.android.ui.DebugActivity
import com.questgamepad.android.ui.MappingActivity
import com.questgamepad.android.uinput.GamepadProfile
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = (grantResult == PackageManager.PERMISSION_GRANTED)
        updateShizukuBanner(granted)
        if (granted && !QuestGamepadService.isRunning) {
            val intent = Intent(this, QuestGamepadService::class.java).apply {
                action = QuestGamepadService.ACTION_START
            }
            startForegroundService(intent)
            binding.root.postDelayed({ updateServiceStateUI() }, 400)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)

        setupShizuku()
        setupUI()
        updateServiceStateUI()
    }

    override fun onResume() {
        super.onResume()
        updateShizukuBanner()
        updateServiceStateUI()

        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            if (!QuestGamepadService.isRunning) {
                val intent = Intent(this, QuestGamepadService::class.java).apply {
                    action = QuestGamepadService.ACTION_START
                }
                startForegroundService(intent)
                binding.root.postDelayed({ updateServiceStateUI() }, 400)
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    private fun setupShizuku() {
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        binding.cardShizuku.setOnClickListener {
            if (!Shizuku.pingBinder()) {
                showShizukuHelpDialog()
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1001)
            } else {
                Toast.makeText(this, "Shizuku is active and authorized!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateShizukuBanner(granted: Boolean = false) {
        val ping = Shizuku.pingBinder()
        val authorized = ping && (granted || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)

        if (authorized) {
            binding.cardShizuku.setCardBackgroundColor(Color.parseColor("#1F3A2A"))
            binding.cardShizuku.strokeColor = Color.parseColor("#7CD49B")
            binding.ivShizukuIcon.setColorFilter(Color.parseColor("#7CD49B"))
            binding.tvShizukuStatus.text = getString(R.string.shizuku_active)
            binding.tvShizukuStatus.setTextColor(Color.parseColor("#7CD49B"))
        } else if (ping) {
            binding.cardShizuku.setCardBackgroundColor(Color.parseColor("#3A311F"))
            binding.cardShizuku.strokeColor = Color.parseColor("#F5C518")
            binding.ivShizukuIcon.setColorFilter(Color.parseColor("#F5C518"))
            binding.tvShizukuStatus.text = "Shizuku Running — Tap to Grant Permission"
            binding.tvShizukuStatus.setTextColor(Color.parseColor("#F5C518"))
        } else {
            binding.cardShizuku.setCardBackgroundColor(Color.parseColor("#3A2A2D"))
            binding.cardShizuku.strokeColor = Color.parseColor("#FF5252")
            binding.ivShizukuIcon.setColorFilter(Color.parseColor("#FF5252"))
            binding.tvShizukuStatus.text = "Shizuku Not Running (Tap for ADB setup instructions)"
            binding.tvShizukuStatus.setTextColor(Color.parseColor("#FF5252"))
        }
    }

    private fun setupUI() {
        // Target Profile Chips
        when (prefs.targetProfile) {
            GamepadProfile.DUALSENSE -> binding.chipDualSense.isChecked = true
            GamepadProfile.DUALSHOCK_4 -> binding.chipDualShock4.isChecked = true
            GamepadProfile.XBOX_360 -> binding.chipXbox360.isChecked = true
            GamepadProfile.XBOX_ONE -> binding.chipXboxOne.isChecked = true
            GamepadProfile.MOUSE -> binding.chipDesktop.isChecked = true
        }

        binding.chipGroupTargetProfile.setOnCheckedStateChangeListener { _, checkedIds ->
            val profile = when (checkedIds.firstOrNull()) {
                R.id.chipDualSense -> GamepadProfile.DUALSENSE
                R.id.chipDualShock4 -> GamepadProfile.DUALSHOCK_4
                R.id.chipXbox360 -> GamepadProfile.XBOX_360
                R.id.chipXboxOne -> GamepadProfile.XBOX_ONE
                R.id.chipDesktop -> GamepadProfile.MOUSE
                else -> GamepadProfile.DUALSENSE
            }
            prefs.targetProfile = profile
            updateDetailsText()
            if (QuestGamepadService.isRunning) {
                val intent = Intent(this, QuestGamepadService::class.java).apply {
                    action = QuestGamepadService.ACTION_SET_PROFILE
                    putExtra(QuestGamepadService.EXTRA_PROFILE_ID, profile.id)
                }
                startService(intent)
            }
        }

        // Input Source Chips
        when (prefs.inputSource) {
            QuestControllerType.QUEST_3 -> binding.chipSourceQuest3.isChecked = true
            QuestControllerType.QUEST_PRO -> binding.chipSourceQuestPro.isChecked = true
            QuestControllerType.QUEST_2 -> binding.chipSourceQuest2.isChecked = true
            QuestControllerType.STEAM_CONTROLLER -> binding.chipSourceSteam.isChecked = true
            QuestControllerType.GENERIC_GAMEPAD -> binding.chipSourceGeneric.isChecked = true
        }

        binding.chipGroupInputSource.setOnCheckedStateChangeListener { _, checkedIds ->
            val source = when (checkedIds.firstOrNull()) {
                R.id.chipSourceQuest3 -> QuestControllerType.QUEST_3
                R.id.chipSourceQuestPro -> QuestControllerType.QUEST_PRO
                R.id.chipSourceQuest2 -> QuestControllerType.QUEST_2
                R.id.chipSourceSteam -> QuestControllerType.STEAM_CONTROLLER
                R.id.chipSourceGeneric -> QuestControllerType.GENERIC_GAMEPAD
                else -> QuestControllerType.QUEST_3
            }
            prefs.inputSource = source
            updateDetailsText()
        }

        // Navigation buttons
        binding.btnMapping.setOnClickListener {
            startActivity(Intent(this, MappingActivity::class.java))
        }

        binding.btnCalibration.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        binding.btnDebug.setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }

        binding.btnHelp.setOnClickListener {
            showInfoDialog()
        }

        // Start/Stop Toggle
        binding.btnToggleService.setOnClickListener {
            toggleService()
        }

        updateDetailsText()
    }

    private fun toggleService() {
        val intent = Intent(this, QuestGamepadService::class.java)
        if (QuestGamepadService.isRunning) {
            intent.action = QuestGamepadService.ACTION_STOP
            startService(intent)
        } else {
            intent.action = QuestGamepadService.ACTION_START
            startForegroundService(intent)
        }
        binding.root.postDelayed({ updateServiceStateUI() }, 300)
    }

    private fun updateServiceStateUI() {
        val running = QuestGamepadService.isRunning
        if (running) {
            binding.tvServiceStatus.text = getString(R.string.service_running)
            binding.viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E5FF"))
            binding.btnToggleService.text = getString(R.string.stop_service)
            binding.btnToggleService.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252"))
            binding.btnToggleService.setTextColor(Color.WHITE)
            binding.btnToggleService.iconTint = android.content.res.ColorStateList.valueOf(Color.WHITE)
        } else {
            binding.tvServiceStatus.text = getString(R.string.service_stopped)
            binding.viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252"))
            binding.btnToggleService.text = getString(R.string.start_service)
            binding.btnToggleService.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E5FF"))
            binding.btnToggleService.setTextColor(Color.BLACK)
            binding.btnToggleService.iconTint = android.content.res.ColorStateList.valueOf(Color.BLACK)
        }
        updateDetailsText()
    }

    private fun updateDetailsText() {
        binding.tvActiveDetails.text = "Output: ${prefs.targetProfile.displayName} | Input: ${prefs.inputSource.displayName}"
    }

    private fun showShizukuHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Setting up Shizuku on Meta Quest")
            .setMessage(
                "Shizuku allows Quest to Gamepad to create a real virtual controller via /dev/uinput without rooting your headset.\n\n" +
                "1. Install Shizuku on your Quest (via SideQuest or ADB install).\n" +
                "2. Connect your Quest to your PC via USB or Wireless ADB.\n" +
                "3. In terminal run:\n" +
                "   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh\n" +
                "4. Return to this app and tap to grant permission!"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showInfoDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Quest to Gamepad")
            .setMessage(
                "Map your Meta Quest 2, Quest 3, Quest 3S, Quest Pro (or Steam Controller) into a full Sony DualSense PS5, DualShock 4, or Xbox gamepad!\n\n" +
                "• Hold Left Thumbrest or Grip to use D-Pad navigation\n" +
                "• Automatically pauses virtual gamepad output in Horizon system menus\n" +
                "• Motion emulation requires an integrated controller pose source\n" +
                "• Supports game force feedback rumble forwarded to Touch haptic motors"
            )
            .setPositiveButton("Got it", null)
            .show()
    }
}
