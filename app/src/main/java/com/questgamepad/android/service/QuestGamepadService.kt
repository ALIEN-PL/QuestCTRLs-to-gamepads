package com.questgamepad.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.questgamepad.android.MainActivity
import com.questgamepad.android.Prefs
import com.questgamepad.android.input.aggregator.QuestControllerAggregator
import com.questgamepad.android.input.model.QuestControllerType
import com.questgamepad.android.input.model.UnifiedGamepadState
import com.questgamepad.android.input.provider.GenericGamepadInputProvider
import com.questgamepad.android.input.provider.InputProvider
import com.questgamepad.android.input.provider.QuestVrInputProvider
import com.questgamepad.android.input.provider.SteamControllerInputProvider
import com.questgamepad.android.uinput.GamepadProfile
import com.questgamepad.android.uinput.UInputGamepad

class QuestGamepadService : Service() {

    private val TAG = "QuestGamepadService"

    companion object {
        const val ACTION_START = "com.questgamepad.android.ACTION_START"
        const val ACTION_STOP = "com.questgamepad.android.ACTION_STOP"
        const val ACTION_CYCLE_PROFILE = "com.questgamepad.android.ACTION_CYCLE_PROFILE"
        const val ACTION_SET_PROFILE = "com.questgamepad.android.ACTION_SET_PROFILE"
        const val EXTRA_PROFILE_ID = "com.questgamepad.android.EXTRA_PROFILE_ID"

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "quest_gamepad_service"

        @Volatile var isRunning = false
            private set
        @Volatile var latestState = UnifiedGamepadState()
            private set
        @Volatile var isForwarding = false
            private set
    }

    private lateinit var prefs: Prefs
    private var uinputGamepad: UInputGamepad? = null
    private var activeProvider: InputProvider? = null
    private var screenshotHeld = false
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "input_source" && isRunning) {
            stopPipeline()
            startServicePipeline()
        } else {
            (activeProvider as? QuestVrInputProvider)?.refreshPreferences(prefs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        prefs.registerChangeListener(preferenceListener)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop command received")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CYCLE_PROFILE -> {
                cycleNextProfile()
                return START_STICKY
            }
            ACTION_SET_PROFILE -> {
                val profileId = intent?.getIntExtra(EXTRA_PROFILE_ID, prefs.targetProfile.id) ?: prefs.targetProfile.id
                val targetProfile = GamepadProfile.fromId(profileId)
                prefs.targetProfile = targetProfile
                uinputGamepad?.switchProfile(targetProfile)
                updateNotification()
                Log.i(TAG, "Switched profile directly to ${targetProfile.displayName}")
                return START_STICKY
            }
            ACTION_START -> {
                startServicePipeline()
            }
        }

        return START_STICKY
    }

    private fun startServicePipeline() {
        if (isRunning) return
        isRunning = true

        startForeground(NOTIFICATION_ID, buildNotification("Initializing..."))

        val targetProf = prefs.targetProfile
        val uinput = UInputGamepad(this, targetProf)
        uinputGamepad = uinput
        uinput.start()

        // Set up aggregator with user preferences
        val aggregator = QuestControllerAggregator(
            leftCalibration = prefs.getLeftStickCalibration(),
            rightCalibration = prefs.getRightStickCalibration(),
            mappings = prefs.loadMappings(),
            enableGyroAiming = prefs.gyroAimingEnabled,
            gyroSensitivity = prefs.gyroSensitivity,
            dpadThreshold = prefs.dpadThreshold
        )

        // Select and instantiate active input provider
        val provider: InputProvider = when (prefs.inputSource) {
            QuestControllerType.QUEST_2, QuestControllerType.QUEST_3, QuestControllerType.QUEST_PRO -> {
                QuestVrInputProvider(this, prefs.inputSource, aggregator, uinput::readQuestState)
            }
            QuestControllerType.STEAM_CONTROLLER -> {
                SteamControllerInputProvider()
            }
            QuestControllerType.GENERIC_GAMEPAD -> {
                GenericGamepadInputProvider()
            }
        }
        activeProvider = provider

        // Connect rumble forwarding from games back to Quest controllers
        uinput.onRumble = { strong, weak ->
            val scale = prefs.rumbleIntensity / 100f
            val scaledStrong = (strong * scale).toInt().coerceIn(0, 65535)
            val scaledWeak = (weak * scale).toInt().coerceIn(0, 65535)
            provider.sendHapticFeedback(scaledStrong, scaledWeak)
        }

        provider.start { state ->
            latestState = state.copy()
            isForwarding = uinput.readQuestState()?.getOrNull(10) == 1
            if (isForwarding && state.screenshotPressed && !screenshotHeld) uinput.takeScreenshot()
            screenshotHeld = state.screenshotPressed
            uinput.sendFrame(state)
        }

        updateNotification()
        Log.i(TAG, "QuestGamepadService running with ${provider.name} -> ${targetProf.displayName}")
    }

    private fun cycleNextProfile() {
        val profiles = GamepadProfile.entries
        val currentIndex = profiles.indexOf(prefs.targetProfile)
        val nextIndex = (currentIndex + 1) % profiles.size
        val nextProfile = profiles[nextIndex]

        prefs.targetProfile = nextProfile
        uinputGamepad?.switchProfile(nextProfile)
        updateNotification()
        Log.i(TAG, "Cycled profile to ${nextProfile.displayName}")
    }

    private fun updateNotification() {
        val (batL, batR) = activeProvider?.getBatteryLevels() ?: Pair(-1, -1)
        val batText = if (batR >= 0) " | L: $batL% R: $batR%" else if (batL >= 0) " | $batL%" else ""
        val statusText = "Active: ${prefs.targetProfile.displayName}$batText"

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cycleIntent = PendingIntent.getService(
            this, 1,
            Intent(this, QuestGamepadService::class.java).apply { action = ACTION_CYCLE_PROFILE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, QuestGamepadService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Quest Gamepad Mapper")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_rotate, "Next Profile", cycleIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quest Gamepad Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status of virtual gamepad mapping"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "QuestGamepadService destroying...")
        prefs.unregisterChangeListener(preferenceListener)
        stopPipeline()
        super.onDestroy()
    }

    private fun stopPipeline() {
        isRunning = false
        isForwarding = false
        latestState = UnifiedGamepadState()
        screenshotHeld = false
        activeProvider?.stop()
        activeProvider = null
        uinputGamepad?.stop()
        uinputGamepad = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
