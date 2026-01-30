package com.example.sizecalc

import android.Manifest
import android.graphics.Rect
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import io.github.sceneview.ar.ARSceneView
import kotlin.math.pow
import kotlin.math.sqrt
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraPermissionWrapper {
                        SizeCalculatorScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPermissionWrapper(content: @Composable () -> Unit) {
    var hasPermission by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        content()
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is required for AR")
        }
    }
}

@Composable
fun SizeCalculatorScreen() {
    val context = LocalContext.current
    val anchors = remember { mutableStateListOf<Anchor>() }
    var currentFrame by remember { mutableStateOf<Frame?>(null) }
    
    // Dimension states
    var length by remember { mutableStateOf(0f) }
    var width by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }

    // Auto-detection states
    var isAutoDetectEnabled by remember { mutableStateOf(true) }
    var detectedObjectRect by remember { mutableStateOf<Rect?>(null) }
    
    // Initialize ML Kit Object Detector
    val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableClassification()
        .build()
    val objectDetector = remember { ObjectDetection.getClient(options) }

    val statusText = when {
        anchors.size >= 4 -> "Measurement Complete!"
        isAutoDetectEnabled -> if (detectedObjectRect != null) "Object Detected! Tap to confirm." else "Point at an item to auto-detect"
        anchors.size == 0 -> "Step 1: Tap first bottom corner"
        anchors.size == 1 -> "Step 2: Tap second bottom corner (Length)"
        anchors.size == 2 -> "Step 3: Tap adjacent bottom corner (Width)"
        anchors.size == 3 -> "Step 4: Tap a top corner (Height)"
        else -> ""
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                ARSceneView(ctx).apply {
                    sessionConfiguration = { _, config ->
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    }
                    onSessionUpdated = { _, frame ->
                        currentFrame = frame
                        
                        if (isAutoDetectEnabled && frame.camera.trackingState == com.google.ar.core.TrackingState.TRACKING) {
                            try {
                                // Capture frame for ML Kit
                                val image = frame.acquireCameraImage()
                                val inputImage = InputImage.fromMediaImage(image, 90) // Rotation may need adjustment
                                
                                objectDetector.process(inputImage)
                                    .addOnSuccessListener { objects ->
                                        detectedObjectRect = objects.firstOrNull()?.boundingBox
                                    }
                                    .addOnCompleteListener {
                                        image.close()
                                    }
                            } catch (e: Exception) {
                                // Image might be unavailable
                            }
                        }
                    }
                    
                    val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapUp(e: MotionEvent): Boolean {
                            val frame = currentFrame ?: return false
                            
                            if (isAutoDetectEnabled && detectedObjectRect != null) {
                                // AUTO-MEASURE LOGIC
                                val rect = detectedObjectRect!!
                                // Hit test bottom corners and top center
                                val hitBottomLeft = frame.hitTest(rect.left.toFloat(), rect.bottom.toFloat())
                                val hitBottomRight = frame.hitTest(rect.right.toFloat(), rect.bottom.toFloat())
                                val hitTop = frame.hitTest(rect.centerX().toFloat(), rect.top.toFloat())

                                if (hitBottomLeft.isNotEmpty() && hitBottomRight.isNotEmpty()) {
                                    val a1 = hitBottomLeft.first().createAnchor()
                                    val a2 = hitBottomRight.first().createAnchor()
                                    anchors.add(a1)
                                    anchors.add(a2)
                                    length = calculateDist(a1.pose, a2.pose)
                                    
                                    // Estimate width and height if possible
                                    if (hitTop.isNotEmpty()) {
                                        val a3 = hitTop.first().createAnchor()
                                        anchors.add(a3)
                                        height = kotlin.math.abs(a3.pose.ty() - a1.pose.ty())
                                        width = length * 0.6f // Placeholder for width in auto-mode
                                    }
                                    
                                    isAutoDetectEnabled = false
                                    Toast.makeText(ctx, "Auto-measured!", Toast.LENGTH_SHORT).show()
                                }
                                return true
                            }

                            // MANUAL MEASURE LOGIC
                            if (anchors.size >= 4) return false
                            val hitResults = frame.hitTest(e)
                            val firstHit = hitResults.firstOrNull()
                            
                            if (firstHit != null) {
                                val newAnchor = firstHit.createAnchor()
                                anchors.add(newAnchor)
                                when (anchors.size) {
                                    2 -> length = calculateDist(anchors[0].pose, anchors[1].pose)
                                    3 -> width = calculateDist(anchors[1].pose, anchors[2].pose)
                                    4 -> height = calculateDist(anchors[2].pose, anchors[3].pose)
                                }
                                Toast.makeText(ctx, "Point ${anchors.size} set", Toast.LENGTH_SHORT).show()
                            }
                            return true
                        }
                    })

                    setOnTouchListener { _, event ->
                        gestureDetector.onTouchEvent(event)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay detected bounding box
        if (isAutoDetectEnabled && detectedObjectRect != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rect = detectedObjectRect!!
                drawRect(
                    color = Color.Green,
                    topLeft = androidx.compose.ui.geometry.Offset(rect.left.toFloat(), rect.top.toFloat()),
                    size = androidx.compose.ui.geometry.Size(rect.width().toFloat(), rect.height().toFloat()),
                    style = Stroke(width = 4f)
                )
            }
        }

        // UI Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusText,
                            color = Color.Cyan,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Switch(
                            checked = isAutoDetectEnabled,
                            onCheckedChange = { isAutoDetectEnabled = it }
                        )
                    }
                    
                    if (anchors.size >= 2) {
                        Text("L: ${String.format(Locale.US, "%.2f", length)}m", color = Color.White)
                    }
                    if (anchors.size >= 3) {
                        Text("W: ${String.format(Locale.US, "%.2f", width)}m", color = Color.White)
                    }
                    if (anchors.size >= 4 || (!isAutoDetectEnabled && anchors.size >= 3)) {
                        if (height > 0) Text("H: ${String.format(Locale.US, "%.2f", height)}m", color = Color.White)
                        val vol = length * width * height
                        if (vol > 0) {
                            Text(
                                "Vol: ${String.format(Locale.US, "%.4f", vol)} m³", 
                                color = Color.Yellow,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row {
                        Button(onClick = { 
                            anchors.clear()
                            length = 0f
                            width = 0f
                            height = 0f
                            detectedObjectRect = null
                        }) {
                            Text("Reset")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = { 
                                ReportGenerator.generateAndShareReport(context, length, width, height) 
                            },
                            enabled = anchors.size >= 2
                        ) {
                            Text("Share Report")
                        }
                    }
                }
            }
        }
    }
}

fun calculateDist(p1: Pose, p2: Pose): Float {
    return sqrt(
        (p1.tx() - p2.tx()).pow(2) +
        (p1.ty() - p2.ty()).pow(2) +
        (p1.tz() - p2.tz()).pow(2)
    )
}
