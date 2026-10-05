package com.questgamepad.android.input.model

enum class QuestControllerType(
    val id: Int,
    val displayName: String,
    val description: String,
    val hasTruTouchHaptics: Boolean = false,
    val hasStylus: Boolean = false
) {
    QUEST_2(
        0,
        "Meta Quest 2 (Touch v3)",
        "Standard Touch controllers with tracking rings and capacitive sensors"
    ),
    QUEST_3(
        1,
        "Meta Quest 3 / 3S (Touch Plus)",
        "Ringless Touch Plus controllers with TruTouch localized haptics",
        hasTruTouchHaptics = true
    ),
    QUEST_PRO(
        2,
        "Meta Quest Pro (Touch Pro)",
        "Self-tracking Touch Pro controllers with Snapdragon chips, stylus tip & trigger pinch",
        hasTruTouchHaptics = true,
        hasStylus = true
    ),
    STEAM_CONTROLLER(
        3,
        "Valve Steam Controller",
        "Dual trackpads, rear paddles, dual-stage triggers"
    ),
    GENERIC_GAMEPAD(
        4,
        "Generic Bluetooth / USB Gamepad",
        "Standard HID gamepad remapped to DualSense"
    );

    companion object {
        fun fromId(id: Int): QuestControllerType = entries.firstOrNull { it.id == id } ?: QUEST_3
    }
}
