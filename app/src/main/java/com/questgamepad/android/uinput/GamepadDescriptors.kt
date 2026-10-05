package com.questgamepad.android.uinput

object GamepadButtons {
    // Standard 16-bit button mask matching the order in uinput_jni.cpp:
    // bit 0 = A / Cross
    // bit 1 = B / Circle
    // bit 2 = X / Square
    // bit 3 = Y / Triangle
    // bit 4 = L1 / LB
    // bit 5 = R1 / RB
    // bit 6 = SELECT / CREATE / SHARE
    // bit 7 = START / OPTIONS / MENU
    // bit 8 = MODE / PS / GUIDE
    // bit 9 = THUMBL / L3
    // bit 10 = THUMBR / R3
    // bit 11 = L2 / TL2 Click
    // bit 12 = R2 / TR2 Click
    // bit 13 = TOUCHPAD CLICK

    const val A             = 1 shl 0   // Cross (PS5) / A (Xbox)
    const val B             = 1 shl 1   // Circle (PS5) / B (Xbox)
    const val X             = 1 shl 2   // Square (PS5) / X (Xbox)
    const val Y             = 1 shl 3   // Triangle (PS5) / Y (Xbox)
    const val LB            = 1 shl 4   // L1
    const val RB            = 1 shl 5   // R1
    const val SELECT        = 1 shl 6   // Create / Share / View / Select
    const val START         = 1 shl 7   // Options / Menu / Start
    const val MODE          = 1 shl 8   // PS Button / Guide
    const val THUMBL        = 1 shl 9   // L3 Stick Click
    const val THUMBR        = 1 shl 10  // R3 Stick Click
    const val L2_CLICK      = 1 shl 11  // L2 Digital Click
    const val R2_CLICK      = 1 shl 12  // R2 Digital Click
    const val TOUCHPAD_CLICK= 1 shl 13  // PS Touchpad Click
}
