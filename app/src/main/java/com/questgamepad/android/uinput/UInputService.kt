package com.questgamepad.android.uinput

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class UInputService : IUInputService.Stub() {

    private val TAG = "QuestUInputService"
    @Volatile private var monitorRunning = false
    private var focusMonitor: Thread? = null

    @Synchronized
    override fun canCreateDevice(): Boolean {
        val ok = UInputNative.canOpen()
        Log.i(TAG, "canCreateDevice: $ok")
        return ok
    }

    @Synchronized
    override fun createGamepad(profileId: Int): Boolean {
        val ok = UInputNative.createDevice(profileId)
        Log.i(TAG, "createGamepad(profile=$profileId) -> $ok")
        return ok
    }

    @Synchronized
    override fun sendFrame(
        buttons: Int,
        leftStickX: Int,
        leftStickY: Int,
        rightStickX: Int,
        rightStickY: Int,
        leftTrigger: Int,
        rightTrigger: Int,
        dpadX: Int,
        dpadY: Int
    ) {
        UInputNative.sendFrame(
            buttons,
            leftStickX, leftStickY,
            rightStickX, rightStickY,
            leftTrigger, rightTrigger,
            dpadX, dpadY
        )
    }

    @Synchronized
    override fun sendMotionFrame(
        gyroX: Int,
        gyroY: Int,
        gyroZ: Int,
        accelX: Int,
        accelY: Int,
        accelZ: Int
    ) {
        UInputNative.sendMotionFrame(gyroX, gyroY, gyroZ, accelX, accelY, accelZ)
    }

    @Synchronized
    override fun sendMouseFrame(relX: Int, relY: Int, scrollY: Int, keys: Int) {
        UInputNative.sendMouseFrame(relX, relY, scrollY, keys)
    }

    @Synchronized
    override fun pollForceFeedback(): IntArray? {
        return UInputNative.pollFFEvent()
    }

    override fun runShellCommand(cmd: Array<out String>): Int {
        return try {
            val process = Runtime.getRuntime().exec(cmd)
            process.waitFor()
        } catch (t: Throwable) {
            Log.e(TAG, "runShellCommand failed: ${cmd.joinToString(" ")}", t)
            -1
        }
    }

    override fun runShellCommandForOutput(cmd: Array<out String>): String? {
        return try {
            val process = Runtime.getRuntime().exec(cmd)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append(line)
            }
            process.waitFor()
            sb.toString().trim()
        } catch (t: Throwable) {
            Log.e(TAG, "runShellCommandForOutput failed", t)
            null
        }
    }

    @Synchronized
    override fun setForwardingEnabled(enabled: Boolean) {
        UInputNative.setForwardingEnabled(enabled)
    }

    override fun setQuestInputEnabled(enabled: Boolean) {
        stopFocusMonitor()
        synchronized(this) {
            UInputNative.setQuestInputEnabled(enabled)
        }
        if (enabled) {
            monitorRunning = true
            focusMonitor = Thread {
                var lastForwarding: Boolean? = null
                while (monitorRunning) {
                    val forwarding = readForegroundState()
                    if (!monitorRunning) break
                    if (forwarding != lastForwarding) {
                        setForwardingEnabled(forwarding)
                        Log.i(TAG, "Horizon automatic pause: ${!forwarding}")
                        lastForwarding = forwarding
                    }
                    try {
                        Thread.sleep(500)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }.apply { name = "HorizonFocusMonitor"; start() }
        }
    }

    private fun readForegroundState(): Boolean {
        var process: Process? = null
        return try {
            process = ProcessBuilder("/system/bin/dumpsys", "-t", "1", "input")
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0 &&
                HorizonFocus.shouldForward(output)
        } catch (exception: Exception) {
            Log.w(TAG, "Cannot determine input focus", exception)
            false
        } finally {
            process?.destroy()
        }
    }

    private fun stopFocusMonitor() {
        monitorRunning = false
        focusMonitor?.interrupt()
        focusMonitor?.join(2500)
        focusMonitor = null
    }

    @Synchronized
    override fun readQuestState(): IntArray = UInputNative.readQuestState()

    override fun destroy() {
        stopFocusMonitor()
        Log.i(TAG, "destroy called")
        synchronized(this) {
            UInputNative.destroy()
        }
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
