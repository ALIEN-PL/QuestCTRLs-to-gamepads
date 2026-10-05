package com.questgamepad.android.input.aggregator

import org.apache.commons.math3.geometry.euclidean.threed.Rotation
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D

data class ControllerPose(
    val positionX: Double,
    val positionY: Double,
    val positionZ: Double,
    val rotationX: Double,
    val rotationY: Double,
    val rotationZ: Double,
    val rotationW: Double,
    val tracked: Boolean = true
)

data class ControllerMotion(val gyro: Vector3D, val acceleration: Vector3D)

class VirtualControllerMotion {
    private var previousOrientation: Rotation? = null
    private var previousCenter: Vector3D? = null
    private var previousVelocity: Vector3D? = null
    private var previousTimestamp = 0L
    private var previousInterval = 0.0

    fun reset() {
        previousOrientation = null
        previousCenter = null
        previousVelocity = null
        previousTimestamp = 0L
        previousInterval = 0.0
    }

    fun update(timestampNs: Long, left: ControllerPose, right: ControllerPose): ControllerMotion? {
        if (!valid(left) || !valid(right) || timestampNs <= 0L ||
            (previousTimestamp != 0L && timestampNs <= previousTimestamp)) {
            reset()
            return null
        }
        val leftPosition = Vector3D(left.positionX, left.positionY, left.positionZ)
        val rightPosition = Vector3D(right.positionX, right.positionY, right.positionZ)
        val axis = rightPosition.subtract(leftPosition)
        if (axis.norm < 0.04) {
            reset()
            return null
        }
        val leftRotation = Rotation(left.rotationW, -left.rotationX, -left.rotationY, -left.rotationZ, true)
        val rightRotation = Rotation(right.rotationW, -right.rotationX, -right.rotationY, -right.rotationZ, true)
        val rightAxis = axis.normalize()
        val upHint = leftRotation.applyTo(Vector3D.PLUS_J).add(rightRotation.applyTo(Vector3D.PLUS_J))
        val upAxis = upHint.subtract(rightAxis.scalarMultiply(upHint.dotProduct(rightAxis)))
        if (upAxis.norm < 0.01) {
            reset()
            return null
        }
        val orientation = Rotation(Vector3D.PLUS_I, Vector3D.PLUS_J, rightAxis, upAxis.normalize())
        val center = leftPosition.add(rightPosition).scalarMultiply(0.5)
        val interval = (timestampNs - previousTimestamp) / 1_000_000_000.0
        var angularVelocity = Vector3D.ZERO
        var acceleration = Vector3D.ZERO
        var velocity: Vector3D? = null
        val lastOrientation = previousOrientation
        val lastCenter = previousCenter
        if (lastOrientation != null && lastCenter != null && interval in 0.001..0.1) {
            val delta = orientation.compose(lastOrientation.revert(), RotationConvention.VECTOR_OPERATOR)
            angularVelocity = orientation.applyInverseTo(
                delta.getAxis(RotationConvention.VECTOR_OPERATOR).scalarMultiply(delta.angle / interval)
            )
            velocity = center.subtract(lastCenter).scalarMultiply(1.0 / interval)
            val lastVelocity = previousVelocity
            if (lastVelocity != null) {
                acceleration = velocity.subtract(lastVelocity).scalarMultiply(2.0 / (interval + previousInterval))
            }
        }
        previousOrientation = orientation
        previousCenter = center
        previousVelocity = velocity
        previousTimestamp = timestampNs
        previousInterval = interval
        val specificForce = orientation.applyInverseTo(acceleration.add(Vector3D(0.0, 9.80665, 0.0)))
        return ControllerMotion(angularVelocity, specificForce)
    }

    private fun valid(pose: ControllerPose): Boolean {
        val values = doubleArrayOf(pose.positionX, pose.positionY, pose.positionZ,
            pose.rotationX, pose.rotationY, pose.rotationZ, pose.rotationW)
        val norm = pose.rotationX * pose.rotationX + pose.rotationY * pose.rotationY +
            pose.rotationZ * pose.rotationZ + pose.rotationW * pose.rotationW
        return pose.tracked && values.all { it.isFinite() } && norm > 0.000001
    }
}