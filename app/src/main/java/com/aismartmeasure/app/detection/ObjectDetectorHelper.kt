package com.aismartmeasure.app.detection

import android.graphics.Rect
import android.media.Image
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/**
 * Handles ML Kit object detection for auto-measurement.
 */
class ObjectDetectorHelper {
    
    private val detector: ObjectDetector
    
    init {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .enableMultipleObjects()
            .build()
        detector = ObjectDetection.getClient(options)
    }

    /**
     * Process an image and return detected object bounds.
     */
    fun detectObjects(
        image: Image,
        rotationDegrees: Int,
        onSuccess: (List<DetectedObject>) -> Unit,
        onFailure: (Exception) -> Unit,
        onComplete: () -> Unit
    ) {
        try {
            val inputImage = InputImage.fromMediaImage(image, rotationDegrees)
            detector.process(inputImage)
                .addOnSuccessListener { objects -> onSuccess(objects) }
                .addOnFailureListener { e -> onFailure(e) }
                .addOnCompleteListener { onComplete() }
        } catch (e: Exception) {
            onFailure(e)
            onComplete()
        }
    }

    /**
     * Get the largest detected object's bounding box.
     */
    fun getLargestObjectBounds(objects: List<DetectedObject>): Rect? {
        return objects.maxByOrNull { 
            it.boundingBox.width() * it.boundingBox.height() 
        }?.boundingBox
    }

    /**
     * Release detector resources.
     */
    fun close() {
        detector.close()
    }
}
