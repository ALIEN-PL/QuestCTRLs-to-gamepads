package com.questgamepad.android.uinput

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.questgamepad.android.input.model.UnifiedGamepadState
import rikka.shizuku.Shizuku

class UInputGamepad(
    private val context: Context,
    initialProfile: GamepadProfile = GamepadProfile.DUALSENSE
) {
    private val TAG = "UInputGamepad"

    private var service: IUInputService? = null
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
        .version(1)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IUInputService.Stub.asInterface(binder)
            service = svc
            Log.i(TAG, "UInputService connected via Shizuku")

            Thread {
                try {
                    if (svc.canCreateDevice()) {
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
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
                isBound = true
            } else {
                Log.w(TAG, "Shizuku is not running or accessible")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to bind Shizuku UserService", t)
        }
    }

    fun stop() {
        stopRumblePolling()
        if (isBound) {
            try {
                service?.destroy()
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            } catch (t: Throwable) {
                Log.e(TAG, "Error unbinding UInputService", t)
            }
            isBound = false
            service = null
            isDeviceReady = false
        }
    }

    fun switchProfile(newProfile: GamepadProfile) {
        if (activeProfile == newProfile && isDeviceReady) return
        activeProfile = newProfile
        Thread {
            try {
                service?.let { svc ->
                    isDeviceReady = false
                    val ok = svc.createGamepad(newProfile.id)
                    isDeviceReady = ok
                    Log.i(TAG, "Switched virtual gamepad to ${newProfile.displayName} -> $ok")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to switch gamepad profile", t)
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
                if (state.gyroX != 0 || state.gyroY != 0 || state.gyroZ != 0) {
                    svc.sendMotionFrame(
                        state.gyroX, state.gyroY, state.gyroZ,
                        state.accelX, state.accelY, state.accelZ
                    )
                }
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
            while (isPollingRumble && isDeviceReady) {
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
                service?.runShellCommand(arrayOf("/system/bin/screencap", "-p", "/sdcard/Pictures/Screenshots/gamepad_${System.currentTimeMillis()}.png"))
            } catch (t: Throwable) {
                Log.e(TAG, "Screenshot failed", t)
            }
        }.start()
    }
}
