package com.questgamepad.android.uinput

internal object HorizonFocus {
    private val focusedDisplay = Regex("FocusedDisplayId:\\s*(\\d+)")
    private val focusedWindow = Regex("displayId=(\\d+), name='([^']+)'")
    private val component = Regex("([a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+)/")

    fun shouldForward(inputDump: String): Boolean {
        val display = focusedDisplay.find(inputDump)?.groupValues?.get(1) ?: return false
        val section = inputDump.substringAfter("FocusedWindows:", "")
            .lineSequence().takeWhile { it.isBlank() || it.startsWith("    ") }
            .joinToString("\n")
        val window = focusedWindow.findAll(section).firstOrNull { it.groupValues[1] == display }
            ?.groupValues?.get(2) ?: return false
        val packageName = component.find(window)?.groupValues?.get(1) ?: return false
        return packageName != "com.oculus.vrshell" &&
            packageName != "com.oculus.systemux" &&
            packageName != "com.android.systemui" &&
            packageName != "com.questgamepad.android" &&
            !packageName.startsWith("com.oculus.panelapp.")
    }
}