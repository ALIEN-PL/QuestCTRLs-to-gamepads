package com.questgamepad.android.input.mapping

import com.questgamepad.android.uinput.GamepadButtons

enum class TargetGamepadButton(
    val mask: Int,
    val dualSenseLabel: String,
    val xboxLabel: String,
    val isDpadModifier: Boolean = false,
    val dpadDirection: Int = 0, // 1: Up, 2: Down, 3: Left, 4: Right
    val keyBit: Int = -1,
    val triggerSide: Int = 0    // 1: Left trigger force, 2: Right trigger force
) {
    NONE(0, "(Unmapped)", "(Unmapped)"),

    // Face buttons
    A_CROSS   (GamepadButtons.A, "✕ (Cross)",    "A Button"),
    B_CIRCLE  (GamepadButtons.B, "○ (Circle)",   "B Button"),
    X_SQUARE  (GamepadButtons.X, "□ (Square)",   "X Button"),
    Y_TRIANGLE(GamepadButtons.Y, "△ (Triangle)", "Y Button"),

    // Shoulders
    L1_LB(GamepadButtons.LB, "L1 (Bumper)", "LB (Left Bumper)"),
    R1_RB(GamepadButtons.RB, "R1 (Bumper)", "RB (Right Bumper)"),

    // Triggers (Digital force max)
    L2_TRIGGER_FULL(-1, "L2 (Trigger Pull)", "LT (Left Trigger)", triggerSide = 1),
    R2_TRIGGER_FULL(-2, "R2 (Trigger Pull)", "RT (Right Trigger)", triggerSide = 2),

    // Sticks
    L3_THUMBL(GamepadButtons.THUMBL, "L3 (Left Stick Click)",  "L3 (Left Stick Click)"),
    R3_THUMBR(GamepadButtons.THUMBR, "R3 (Right Stick Click)", "R3 (Right Stick Click)"),

    // System
    SELECT_CREATE (GamepadButtons.SELECT, "Create / Share", "View / Back"),
    START_OPTIONS (GamepadButtons.START,  "Options",        "Menu / Start"),
    PS_GUIDE      (GamepadButtons.MODE,   "PS Button",      "Xbox Guide"),
    TOUCHPAD_CLICK(GamepadButtons.TOUCHPAD_CLICK, "Touchpad Click", "Touchpad Click"),

    // D-Pad
    DPAD_UP   (0, "D-Pad Up",    "D-Pad Up",    dpadDirection = 1),
    DPAD_DOWN (0, "D-Pad Down",  "D-Pad Down",  dpadDirection = 2),
    DPAD_LEFT (0, "D-Pad Left",  "D-Pad Left",  dpadDirection = 3),
    DPAD_RIGHT(0, "D-Pad Right", "D-Pad Right", dpadDirection = 4),

    // Special modifier: while pressed, transforms stick/face buttons to D-Pad
    DPAD_MODIFIER(0, "D-Pad Shift Modifier", "D-Pad Shift Modifier", isDpadModifier = true),

    // Special actions
    SCREENSHOT(-10, "📸 Take Screenshot", "📸 Take Screenshot");

    fun getDisplayName(isDualSense: Boolean = true): String =
        if (isDualSense) dualSenseLabel else xboxLabel
}
