package com.questgamepad.android.uinput

object UInputNative {

    init {
        System.loadLibrary("questgamepad_uinput")
    }

    @JvmStatic external fun canOpen(): Boolean
    @JvmStatic external fun createDevice(profileId: Int): Boolean
    @JvmStatic external fun sendFrame(
        buttons: Int,
        lx: Int, ly: Int, rx: Int, ry: Int,
        lt: Int, rt: Int,
        dpadX: Int, dpadY: Int
    )
    @JvmStatic external fun sendMotionFrame(
        gyroX: Int, gyroY: Int, gyroZ: Int,
        accelX: Int, accelY: Int, accelZ: Int
    )
    @JvmStatic external fun sendMouseFrame(relX: Int, relY: Int, scrollY: Int, keys: Int)
    @JvmStatic external fun pollFFEvent(): IntArray?
    @JvmStatic external fun setForwardingEnabled(enabled: Boolean)
    @JvmStatic external fun destroy()
}
