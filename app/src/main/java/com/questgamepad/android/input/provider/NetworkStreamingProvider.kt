package com.questgamepad.android.input.provider

import android.util.Log
import com.questgamepad.android.input.aggregator.QuestControllerAggregator
import com.questgamepad.android.input.model.QuestControllerType
import com.questgamepad.android.input.model.RawQuestControllerState
import com.questgamepad.android.input.model.UnifiedGamepadState
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NetworkStreamingProvider(
    private val port: Int = 52525,
    val aggregator: QuestControllerAggregator = QuestControllerAggregator()
) : InputProvider {

    private val TAG = "NetworkStreamProvider"
    override val name: String = "Quest WiFi/UDP Stream Receiver"
    override val deviceType: QuestControllerType = QuestControllerType.QUEST_3

    @Volatile private var isRunning = false
    private var socket: DatagramSocket? = null
    private var receiverThread: Thread? = null

    private val rawState = RawQuestControllerState(deviceType = deviceType)
    private val unifiedState = UnifiedGamepadState(sourceDevice = deviceType)

    override fun start(onStateUpdated: (UnifiedGamepadState) -> Unit) {
        if (isRunning) return
        isRunning = true

        receiverThread = Thread {
            try {
                socket = DatagramSocket(port)
                val buffer = ByteArray(256)
                val packet = DatagramPacket(buffer, buffer.size)
                Log.i(TAG, "UDP Receiver listening on port $port")

                while (isRunning) {
                    socket?.receive(packet)
                    if (packet.length < 32) continue

                    val bb = ByteBuffer.wrap(packet.data, 0, packet.length).order(ByteOrder.LITTLE_ENDIAN)
                    val magic = bb.int
                    if (magic != 0x51554553) continue // 'QUES'

                    val typeId = bb.get().toInt()
                    val devType = QuestControllerType.fromId(typeId)

                    val lx = bb.float
                    val ly = bb.float
                    val rx = bb.float
                    val ry = bb.float
                    val lt = bb.float
                    val rt = bb.float
                    val lg = bb.float
                    val rg = bb.float
                    val buttons = bb.int

                    synchronized(rawState) {
                        rawState.deviceType = devType
                        rawState.left.stickX = lx
                        rawState.left.stickY = ly
                        rawState.right.stickX = rx
                        rawState.right.stickY = ry
                        rawState.left.trigger = lt
                        rawState.right.trigger = rt
                        rawState.left.grip = lg
                        rawState.right.grip = rg

                        rawState.right.buttonPrimaryClick = (buttons and 1) != 0    // A
                        rawState.right.buttonSecondaryClick = (buttons and 2) != 0  // B
                        rawState.left.buttonPrimaryClick = (buttons and 4) != 0     // X
                        rawState.left.buttonSecondaryClick = (buttons and 8) != 0   // Y
                        rawState.left.stickClick = (buttons and 16) != 0            // L3
                        rawState.right.stickClick = (buttons and 32) != 0           // R3
                        rawState.left.systemButtonClick = (buttons and 64) != 0     // Menu
                        rawState.right.systemButtonClick = (buttons and 128) != 0   // System
                        rawState.left.thumbrestTouch = (buttons and 256) != 0       // Left Rest

                        if (bb.remaining() >= 24) {
                            rawState.right.gyroX = bb.float
                            rawState.right.gyroY = bb.float
                            rawState.right.gyroZ = bb.float
                            rawState.right.accelX = bb.float
                            rawState.right.accelY = bb.float
                            rawState.right.accelZ = bb.float
                        }

                        if (bb.remaining() >= 2) {
                            rawState.left.batteryPercent = bb.get().toInt()
                            rawState.right.batteryPercent = bb.get().toInt()
                        }

                        aggregator.aggregate(rawState, unifiedState)
                    }

                    onStateUpdated(unifiedState)
                }
            } catch (t: Throwable) {
                if (isRunning) Log.e(TAG, "Socket error in receiver loop", t)
            } finally {
                socket?.close()
                socket = null
            }
        }.apply {
            name = "QuestNetworkStreamLoop"
            start()
        }
    }

    override fun stop() {
        isRunning = false
        socket?.close()
        socket = null
        receiverThread?.interrupt()
        receiverThread = null
    }

    override fun sendHapticFeedback(strongMagnitude: Int, weakMagnitude: Int) {
        // Can be forwarded as UDP response packet back to Quest if needed
    }

    override fun isConnected(): Boolean = isRunning && socket != null

    override fun getBatteryLevels(): Pair<Int, Int> =
        Pair(rawState.left.batteryPercent, rawState.right.batteryPercent)
}
