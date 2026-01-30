package com.aismartmeasure.app.data

import com.google.ar.core.Anchor
import java.util.Date

/**
 * Represents a 3D measurement with length, width, and height.
 */
data class Measurement(
    val length: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val timestamp: Date = Date()
) {
    val volume: Float get() = length * width * height
    val isComplete: Boolean get() = length > 0 && width > 0 && height > 0
    val hasBaseline: Boolean get() = length > 0
}

/**
 * Represents the current state of the measurement process.
 */
sealed class MeasurementState {
    object Idle : MeasurementState()
    object DetectingPlane : MeasurementState()
    data class Measuring(val pointCount: Int) : MeasurementState()
    data class Complete(val measurement: Measurement) : MeasurementState()
    data class Error(val message: String) : MeasurementState()
}

/**
 * Holds all anchor points for the current measurement.
 */
data class MeasurementPoints(
    val anchors: List<Anchor> = emptyList()
) {
    val count: Int get() = anchors.size
    val isReadyForLength: Boolean get() = count >= 2
    val isReadyForWidth: Boolean get() = count >= 3
    val isReadyForHeight: Boolean get() = count >= 4
}
