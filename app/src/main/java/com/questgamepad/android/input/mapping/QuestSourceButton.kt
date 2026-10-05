package com.questgamepad.android.input.mapping

enum class ButtonCategory(val title: String) {
    FACE("Face Buttons"),
    TRIGGERS("Triggers & Grips"),
    STICKS("Stick Clicks"),
    SYSTEM("System & Menu"),
    TOUCH("Capacitive & Pro Features")
}

enum class QuestSourceButton(
    val displayName: String,
    val shortLabel: String,
    val category: ButtonCategory
) {
    // Face buttons
    A("A Button (Right)", "A", ButtonCategory.FACE),
    B("B Button (Right)", "B", ButtonCategory.FACE),
    X("X Button (Left)",  "X", ButtonCategory.FACE),
    Y("Y Button (Left)",  "Y", ButtonCategory.FACE),

    // Triggers & Grips
    LEFT_TRIGGER ("Left Index Trigger (Full Press)",  "LT", ButtonCategory.TRIGGERS),
    RIGHT_TRIGGER("Right Index Trigger (Full Press)", "RT", ButtonCategory.TRIGGERS),
    LEFT_GRIP    ("Left Grip Squeeze",                "LG", ButtonCategory.TRIGGERS),
    RIGHT_GRIP   ("Right Grip Squeeze",               "RG", ButtonCategory.TRIGGERS),

    // Stick clicks
    LEFT_STICK_CLICK ("Left Stick Click (L3)",  "L3", ButtonCategory.STICKS),
    RIGHT_STICK_CLICK("Right Stick Click (R3)", "R3", ButtonCategory.STICKS),

    // System & Menu
    MENU  ("Menu Button (Left)",   "Menu", ButtonCategory.SYSTEM),
    SYSTEM("Oculus Button (Right)", "Oculus", ButtonCategory.SYSTEM),

    // Capacitive & Pro features
    LEFT_THUMBREST ("Left Thumbrest Touch",  "LTH", ButtonCategory.TOUCH),
    RIGHT_THUMBREST("Right Thumbrest Touch", "RTH", ButtonCategory.TOUCH),
    PRO_STYLUS     ("Quest Pro Stylus Force","Stylus", ButtonCategory.TOUCH)
}
