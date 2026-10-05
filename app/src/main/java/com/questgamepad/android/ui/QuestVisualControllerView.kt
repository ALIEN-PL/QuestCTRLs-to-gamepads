package com.questgamepad.android.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.questgamepad.android.input.model.UnifiedGamepadState
import com.questgamepad.android.uinput.GamepadButtons
import kotlin.math.min

class QuestVisualControllerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E222B")
        style = Paint.Style.FILL
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#384252")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF") // Vibrant Cyan
        style = Paint.Style.FILL
    }

    private val inactiveButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A313D")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val stickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5A6982")
        style = Paint.Style.FILL
    }

    private val stickActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
    }

    private var currentState: UnifiedGamepadState = UnifiedGamepadState()

    fun updateState(state: UnifiedGamepadState) {
        currentState.copyFrom(state)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 20f

        // Draw background container
        val rect = RectF(padding, padding, w - padding, h - padding)
        canvas.drawRoundRect(rect, 32f, 32f, bgPaint)
        canvas.drawRoundRect(rect, 32f, 32f, outlinePaint)

        val centerX = w / 2f
        val centerY = h / 2f

        // Draw Left Controller (Left side)
        val leftControllerX = w * 0.28f
        val rightControllerX = w * 0.72f

        // ── LEFT CONTROLLER ──
        // Thumbstick (Left Stick)
        val stickRadius = min(w, h) * 0.12f
        val stickCenterX = leftControllerX - stickRadius * 0.5f
        val stickCenterY = centerY - stickRadius * 0.3f
        canvas.drawCircle(stickCenterX, stickCenterY, stickRadius, inactiveButtonPaint)
        canvas.drawCircle(stickCenterX, stickCenterY, stickRadius, outlinePaint)

        // Stick position offset (-32768..32767)
        val normLx = (currentState.leftStickX / 32767f).coerceIn(-1f, 1f)
        val normLy = (currentState.leftStickY / 32767f).coerceIn(-1f, 1f)
        val stickDotX = stickCenterX + normLx * (stickRadius * 0.65f)
        val stickDotY = stickCenterY + normLy * (stickRadius * 0.65f)
        val isL3 = currentState.isButtonPressed(GamepadButtons.THUMBL)
        canvas.drawCircle(stickDotX, stickDotY, stickRadius * 0.45f, if (isL3) stickActivePaint else stickPaint)

        // Left Face Buttons (X, Y)
        val btnRadius = stickRadius * 0.32f
        val btnX_x = leftControllerX + stickRadius * 0.6f
        val btnX_y = centerY + stickRadius * 0.3f
        val btnY_x = leftControllerX + stickRadius * 0.4f
        val btnY_y = centerY - stickRadius * 0.3f

        drawButton(canvas, btnX_x, btnX_y, btnRadius, "X", currentState.isButtonPressed(GamepadButtons.X))
        drawButton(canvas, btnY_x, btnY_y, btnRadius, "Y", currentState.isButtonPressed(GamepadButtons.Y))

        // Left Menu Button
        val menuX = leftControllerX - stickRadius * 0.2f
        val menuY = centerY + stickRadius * 0.8f
        drawButton(canvas, menuX, menuY, btnRadius * 0.8f, "≡", currentState.isButtonPressed(GamepadButtons.SELECT))

        // Left Trigger Bar (LT)
        val trigW = stickRadius * 0.35f
        val trigH = stickRadius * 1.2f
        val ltRect = RectF(leftControllerX - stickRadius * 1.5f, centerY - trigH * 0.5f, leftControllerX - stickRadius * 1.5f + trigW, centerY + trigH * 0.5f)
        canvas.drawRoundRect(ltRect, 10f, 10f, inactiveButtonPaint)
        val ltFill = (currentState.leftTrigger / 255f).coerceIn(0f, 1f)
        if (ltFill > 0.05f) {
            val fillRect = RectF(ltRect.left, ltRect.bottom - (trigH * ltFill), ltRect.right, ltRect.bottom)
            canvas.drawRoundRect(fillRect, 10f, 10f, activePaint)
        }
        canvas.drawRoundRect(ltRect, 10f, 10f, outlinePaint)

        // Left Bumper / Grip (L1)
        val l1Pressed = currentState.isButtonPressed(GamepadButtons.LB)
        val l1Rect = RectF(leftControllerX - stickRadius * 1.1f, centerY - trigH * 0.9f, leftControllerX - stickRadius * 0.1f, centerY - trigH * 0.6f)
        canvas.drawRoundRect(l1Rect, 12f, 12f, if (l1Pressed) activePaint else inactiveButtonPaint)
        canvas.drawRoundRect(l1Rect, 12f, 12f, outlinePaint)
        canvas.drawText("L1", l1Rect.centerX(), l1Rect.centerY() + 10f, textPaint)

        // ── RIGHT CONTROLLER ──
        // Thumbstick (Right Stick)
        val rightStickCenterX = rightControllerX - stickRadius * 0.5f
        val rightStickCenterY = centerY + stickRadius * 0.4f
        canvas.drawCircle(rightStickCenterX, rightStickCenterY, stickRadius, inactiveButtonPaint)
        canvas.drawCircle(rightStickCenterX, rightStickCenterY, stickRadius, outlinePaint)

        val normRx = (currentState.rightStickX / 32767f).coerceIn(-1f, 1f)
        val normRy = (currentState.rightStickY / 32767f).coerceIn(-1f, 1f)
        val rightStickDotX = rightStickCenterX + normRx * (stickRadius * 0.65f)
        val rightStickDotY = rightStickCenterY + normRy * (stickRadius * 0.65f)
        val isR3 = currentState.isButtonPressed(GamepadButtons.THUMBR)
        canvas.drawCircle(rightStickDotX, rightStickDotY, stickRadius * 0.45f, if (isR3) stickActivePaint else stickPaint)

        // Right Face Buttons (A, B)
        val btnA_x = rightControllerX + stickRadius * 0.4f
        val btnA_y = centerY + stickRadius * 0.1f
        val btnB_x = rightControllerX + stickRadius * 0.6f
        val btnB_y = centerY - stickRadius * 0.5f

        drawButton(canvas, btnA_x, btnA_y, btnRadius, "A", currentState.isButtonPressed(GamepadButtons.A))
        drawButton(canvas, btnB_x, btnB_y, btnRadius, "B", currentState.isButtonPressed(GamepadButtons.B))

        // Right Oculus / System Button
        val oculusX = rightControllerX - stickRadius * 0.2f
        val oculusY = centerY - stickRadius * 0.4f
        drawButton(canvas, oculusX, oculusY, btnRadius * 0.8f, "◎", currentState.isButtonPressed(GamepadButtons.START))

        // Right Trigger Bar (RT)
        val rtRect = RectF(rightControllerX + stickRadius * 1.2f, centerY - trigH * 0.5f, rightControllerX + stickRadius * 1.2f + trigW, centerY + trigH * 0.5f)
        canvas.drawRoundRect(rtRect, 10f, 10f, inactiveButtonPaint)
        val rtFill = (currentState.rightTrigger / 255f).coerceIn(0f, 1f)
        if (rtFill > 0.05f) {
            val fillRect = RectF(rtRect.left, rtRect.bottom - (trigH * rtFill), rtRect.right, rtRect.bottom)
            canvas.drawRoundRect(fillRect, 10f, 10f, activePaint)
        }
        canvas.drawRoundRect(rtRect, 10f, 10f, outlinePaint)

        // Right Bumper / Grip (R1)
        val r1Pressed = currentState.isButtonPressed(GamepadButtons.RB)
        val r1Rect = RectF(rightControllerX + stickRadius * 0.1f, centerY - trigH * 0.9f, rightControllerX + stickRadius * 1.1f, centerY - trigH * 0.6f)
        canvas.drawRoundRect(r1Rect, 12f, 12f, if (r1Pressed) activePaint else inactiveButtonPaint)
        canvas.drawRoundRect(r1Rect, 12f, 12f, outlinePaint)
        canvas.drawText("R1", r1Rect.centerX(), r1Rect.centerY() + 10f, textPaint)

        // Center D-Pad Hat indicator
        val dpadCenterY = centerY + stickRadius * 0.8f
        drawDpad(canvas, centerX, dpadCenterY, stickRadius * 0.6f, currentState.dpadX, currentState.dpadY)
    }

    private fun drawButton(canvas: Canvas, x: Float, y: Float, r: Float, label: String, pressed: Boolean) {
        canvas.drawCircle(x, y, r, if (pressed) activePaint else inactiveButtonPaint)
        canvas.drawCircle(x, y, r, outlinePaint)
        textPaint.color = if (pressed) Color.BLACK else Color.WHITE
        canvas.drawText(label, x, y + 10f, textPaint)
        textPaint.color = Color.WHITE
    }

    private fun drawDpad(canvas: Canvas, x: Float, y: Float, size: Float, hatX: Int, hatY: Int) {
        val half = size / 2f
        val arm = size * 0.35f

        // Up
        val upPressed = hatY == -1
        canvas.drawRect(x - arm / 2, y - half, x + arm / 2, y - arm / 2, if (upPressed) activePaint else inactiveButtonPaint)
        // Down
        val downPressed = hatY == 1
        canvas.drawRect(x - arm / 2, y + arm / 2, x + arm / 2, y + half, if (downPressed) activePaint else inactiveButtonPaint)
        // Left
        val leftPressed = hatX == -1
        canvas.drawRect(x - half, y - arm / 2, x - arm / 2, y + arm / 2, if (leftPressed) activePaint else inactiveButtonPaint)
        // Right
        val rightPressed = hatX == 1
        canvas.drawRect(x + arm / 2, y - arm / 2, x + half, y + arm / 2, if (rightPressed) activePaint else inactiveButtonPaint)
        // Center
        canvas.drawRect(x - arm / 2, y - arm / 2, x + arm / 2, y + arm / 2, inactiveButtonPaint)
    }
}
