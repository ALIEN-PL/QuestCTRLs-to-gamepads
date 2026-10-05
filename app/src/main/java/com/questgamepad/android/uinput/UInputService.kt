package com.questgamepad.android.uinput

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class UInputService : IUInputService.Stub() {

    private val TAG = "QuestUInputService"

    override fun canCreateDevice(): Boolean {
        val ok = UInputNative.canOpen()
        Log.i(TAG, "canCreateDevice: $ok")
        return ok
    }

    override fun createGamepad(profileId: Int): Boolean {
        val ok = UInputNative.createDevice(profileId)
        Log.i(TAG, "createGamepad(profile=$profileId) -> $ok")
        return ok
    }

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

    override fun sendMouseFrame(relX: Int, relY: Int, scrollY: Int, keys: Int) {
        UInputNative.sendMouseFrame(relX, relY, scrollY, keys)
    }

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

    override fun setForwardingEnabled(enabled: Boolean) {
        UInputNative.setForwardingEnabled(enabled)
    }

    override fun destroy() {
        Log.i(TAG, "destroy called")
        UInputNative.destroy()
    }
}
