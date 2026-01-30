package com.aismartmeasure.app.ar

import com.aismartmeasure.app.data.Measurement
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Manages AR measurement calculations.
 */
object ARMeasurementManager {

    /**
     * Calculate the Euclidean distance between two AR poses in meters.
     */
    fun calculateDistance(pose1: Pose, pose2: Pose): Float {
        val dx = pose1.tx() - pose2.tx()
        val dy = pose1.ty() - pose2.ty()
        val dz = pose1.tz() - pose2.tz()
        return sqrt(dx.pow(2) + dy.pow(2) + dz.pow(2))
    }

    /**
     * Calculate measurement from a list of anchors.
     * - Anchors 0-1: Length (bottom edge)
     * - Anchors 1-2: Width (adjacent bottom edge)
     * - Anchors 2-3: Height (vertical edge)
     */
    fun calculateMeasurement(anchors: List<Anchor>): Measurement {
        if (anchors.size < 2) return Measurement()
        
        val length = calculateDistance(anchors[0].pose, anchors[1].pose)
        val width = if (anchors.size >= 3) calculateDistance(anchors[1].pose, anchors[2].pose) else 0f
        val height = if (anchors.size >= 4) calculateDistance(anchors[2].pose, anchors[3].pose) else 0f
        
        return Measurement(
            length = length,
            width = width,
            height = height
        )
    }

    /**
     * Get instruction text based on current anchor count.
     */
    fun getInstructionText(anchorCount: Int): String {
        return when (anchorCount) {
            0 -> "Tap the first corner of the object"
            1 -> "Tap the second corner (for Length)"
            2 -> "Tap the third corner (for Width)"
            3 -> "Tap the fourth corner (for Height)"
            else -> "Measurement complete!"
        }
    }
}
