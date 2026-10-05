package com.questgamepad.android.uinput

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.questgamepad.android.input.model.UnifiedGamepadState
import com.questgamepad.android.Prefs
import com.questgamepad.android.input.model.QuestControllerType
import rikka.shizuku.Shizuku

class UInputGamepad(
    private val context: Context,
    initialProfile: GamepadProfile = GamepadProfile.DUALSENSE
) {
    private val TAG = "UInputGamepad"

    @Volatile private var service: IUInputService? = null
    private val lifecycleLock = Any()
    @Volatile private var isBound = false
    @Volatile var activeProfile: GamepadProfile = initialProfile
        private set

    @Volatile var isDeviceReady = false
        private set

    var onRumble: ((strong: Int, weak: Int) -> Unit)? = null

    private var rumblePollingThread: Thread? = null
    @Volatile private var isPollingRumble = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, UInputService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("quest_uinput")
        .debuggable(false)
        .version(3)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IUInputService.Stub.asInterface(binder)
            service = svc
            Log.i(TAG, "UInputService connected via Shizuku")

            Thread {
                synchronized(lifecycleLock) {
                    if (!isBound || service !== svc) return@Thread
                    try {
                        if (svc.canCreateDevice()) {
                            val source = Prefs(context).inputSource
                            svc.setQuestInputEnabled(source != QuestControllerType.STEAM_CONTROLLER && source != QuestControllerType.GENERIC_GAMEPAD)
                            val ok = svc.createGamepad(activeProfile.id)
                            isDeviceReady = ok
                            Log.i(TAG, "Created virtual gamepad [${activeProfile.displayName}]: $ok")
                            if (ok) startRumblePolling()
                        } else {
                            Log.e(TAG, "Cannot access /dev/uinput from shell UID")
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error initializing virtual device", t)
                    }
                }
            }.start()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "UInputService disconnected")
            service = null
            isDeviceReady = false
            stopRumblePolling()
        }
    }

    fun start() {
        if (isBound) return
        try {
            if (Shizuku.pingBinder()) {
                Log.i(TAG, "Binding Shizuku UserService...")
                isBound = true
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            } else {
                Log.w(TAG, "Shizuku is not running or accessible")
            }
        } catch (t: Throwable) {
            isBound = false
            Log.e(TAG, "Failed to bind Shizuku UserService", t)
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            stopRumblePolling()
            if (isBound) {
                isBound = false
                try {
                    service?.destroy()
                    Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error unbinding UInputService", t)
                }
                service = null
                isDeviceReady = false
            }
        }
    }

    fun switchProfile(newProfile: GamepadProfile) {
        if (activeProfile == newProfile && isDeviceReady) return
        activeProfile = newProfile
        Thread {
            synchronized(lifecycleLock) {
                if (!isBound) return@Thread
                try {
                    service?.let { svc ->
                        isDeviceReady = false
                        val ok = svc.createGamepad(activeProfile.id)
                        isDeviceReady = ok
                        Log.i(TAG, "Switched virtual gamepad to ${activeProfile.displayName} -> $ok")
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to switch gamepad profile", t)
                }
            }
        }.start()
    }

    fun setForwardingEnabled(enabled: Boolean) {
        try {
            service?.setForwardingEnabled(enabled)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set forwarding state: $enabled", t)
        }
    }

    fun readQuestState(): IntArray? = try {
        if (isDeviceReady) service?.readQuestState() else null
    } catch (_: Throwable) {
        null
    }

    fun sendFrame(state: UnifiedGamepadState) {
        val svc = service ?: return
        if (!isDeviceReady) return

        try {
            svc.sendFrame(
                state.buttons,
                state.leftStickX,
                state.leftStickY,
                state.rightStickX,
                state.rightStickY,
                state.leftTrigger,
                state.rightTrigger,
                state.dpadX,
                state.dpadY
            )

            // Send motion frame for DualSense / DS4 gyro aiming
            if (activeProfile == GamepadProfile.DUALSENSE || activeProfile == GamepadProfile.DUALSHOCK_4) {
                svc.sendMotionFrame(
                    state.gyroX, state.gyroY, state.gyroZ,
                    state.accelX, state.accelY, state.accelZ
                )
            }
        } catch (_: Throwable) {
            // Drop frame on transient binder error
        }
    }

    fun sendMouseFrame(relX: Int, relY: Int, scrollY: Int, keys: Int) {
        val svc = service ?: return
        if (!isDeviceReady) return
        try {
            svc.sendMouseFrame(relX, relY, scrollY, keys)
        } catch (_: Throwable) {}
    }

    private fun startRumblePolling() {
        if (isPollingRumble) return
        isPollingRumble = true

        rumblePollingThread = Thread {
            Log.i(TAG, "Force feedback rumble polling thread started")
            while (isPollingRumble) {
                try {
                    val ff = service?.pollForceFeedback()
                    if (ff != null && ff.size >= 2) {
                        val strong = ff[0]
                        val weak = ff[1]
                        onRumble?.invoke(strong, weak)
                    }
                    Thread.sleep(16) // ~60Hz poll
                } catch (_: InterruptedException) {
                    break
                } catch (t: Throwable) {
                    Log.v(TAG, "Rumble poll loop exception: ${t.message}")
                    break
                }
            }
        }.apply {
            name = "UInputRumblePolling"
            start()
        }
    }

    private fun stopRumblePolling() {
        isPollingRumble = false
        rumblePollingThread?.interrupt()
        rumblePollingThread = null
    }

    fun takeScreenshot() {
        Thread {
            try {
                service?.runShellCommand(arrayOf("/system/bin/mkdir", "-p", "/sdcard/Pictures/Screenshots"))
                service?.runShellCommand(arrayOf("/system/bin/screencap", "-p", "/sdcard/Pictures/Screenshots/gamepad_${System.currentTimeMillis()}.png"))
            } catch (t: Throwable) {
                Log.e(TAG, "Screenshot failed", t)
            }
        }.start()
    }
}
