package com.questgamepad.android.uinput

enum class GamepadProfile(
    val id: Int,
    val displayName: String,
    val vid: Int,
    val pid: Int,
    val isMouseMode: Boolean = false
) {
    DUALSENSE  (3, "Sony DualSense (PS5)",        0x054C, 0x0CE6),
    DUALSHOCK_4(2, "Sony DualShock 4 (PS4)",      0x054C, 0x05C4),
    XBOX_360   (0, "Xbox 360 Controller",         0x045E, 0x028E),
    XBOX_ONE   (1, "Xbox One Controller",         0x045E, 0x02EA),
    MOUSE      (4, "Desktop Mode (Mouse + Keyboard)", 0x046D, 0xC077, isMouseMode = true);

    companion object {
        fun fromId(id: Int): GamepadProfile = entries.firstOrNull { it.id == id } ?: DUALSENSE
    }
}
