package com.questgamepad.android.uinput

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HorizonFocusTest {
    @Test fun shellFocusPausesEvenWithGameFocusedOnAnotherDisplay() {
        assertFalse(HorizonFocus.shouldForward(dump(0)))
    }

    @Test fun activeGamePanelResumesEvenWithShellPlaceholder() {
        assertTrue(HorizonFocus.shouldForward(dump(12)))
    }

    @Test fun missingOrUnknownFocusPauses() {
        assertFalse(HorizonFocus.shouldForward(""))
        assertFalse(HorizonFocus.shouldForward(dump(99)))
        assertFalse(HorizonFocus.shouldForward(dump(12).replace("com.example.game/.Main", "com.questgamepad.android/.Main")))
    }

    private fun dump(display: Int) = """
        FocusedDisplayId: $display
        FocusedApplications:
          displayId=0, name='com.example.game/.Old'
        FocusedWindows:
          displayId=0, name='123 com.oculus.vrshell/.FocusPlaceholderActivity'
          displayId=12, name='456 com.example.game/.Main'
        FocusRequests:
          displayId=99, name='com.example.game/.Old'
    """.trimIndent().prependIndent("  ")
}