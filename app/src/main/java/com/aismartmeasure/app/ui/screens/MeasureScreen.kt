package com.aismartmeasure.app.ui.screens

import android.Manifest
import android.graphics.Rect
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aismartmeasure.app.ar.ARMeasurementManager
import com.aismartmeasure.app.data.Measurement
import com.aismartmeasure.app.detection.ObjectDetectorHelper
import com.aismartmeasure.app.ui.theme.AccentCyan
import com.aismartmeasure.app.ui.theme.SuccessGreen
import com.aismartmeasure.app.ui.theme.WarningYellow
import com.aismartmeasure.app.util.ReportGenerator
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import java.util.Locale

@Composable
fun MeasureScreen() {
    var hasPermission by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        MeasurementContent()
    } else {
        PermissionRequest { launcher.launch(Manifest.permission.CAMERA) }
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Camera Permission Required",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This app needs camera access for AR measurement",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequest) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
private fun MeasurementContent() {
    val context = LocalContext.current
    val anchors = remember { mutableStateListOf<Anchor>() }
    var currentFrame by remember { mutableStateOf<Frame?>(null) }
    var measurement by remember { mutableStateOf(Measurement()) }
    var isPlaneDetected by remember { mutableStateOf(false) }
    var isAutoMode by remember { mutableStateOf(false) }
    var detectedRect by remember { mutableStateOf<Rect?>(null) }

    val objectDetector = remember { ObjectDetectorHelper() }

    DisposableEffect(Unit) {
        onDispose { objectDetector.close() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // AR View
        AndroidView(
            factory = { ctx ->
                ARSceneView(ctx).apply {
                    sessionConfiguration = { _, config ->
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    }

                    onSessionUpdated = { _, frame ->
                        currentFrame = frame
                        isPlaneDetected = frame.getUpdatedTrackables(com.google.ar.core.Plane::class.java).isNotEmpty() ||
                                          frame.camera.trackingState == TrackingState.TRACKING

                        // Auto-detection
                        if (isAutoMode && frame.camera.trackingState == TrackingState.TRACKING) {
                            try {
                                val image = frame.acquireCameraImage()
                                objectDetector.detectObjects(
                                    image = image,
                                    rotationDegrees = 90,
                                    onSuccess = { objects ->
                                        detectedRect = objectDetector.getLargestObjectBounds(objects)
                                    },
                                    onFailure = { /* Ignore */ },
                                    onComplete = { image.close() }
                                )
                            } catch (_: Exception) { }
                        }
                    }

                    val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapUp(e: MotionEvent): Boolean {
                            val frame = currentFrame ?: return false
                            if (anchors.size >= 4) return false

                            // Auto-measure from detected object
                            if (isAutoMode && detectedRect != null) {
                                val rect = detectedRect!!
                                val hits = frame.hitTest(rect.centerX().toFloat(), rect.centerY().toFloat())
                                hits.firstOrNull()?.let { hit ->
                                    anchors.add(hit.createAnchor())
                                    // Create second point for width
                                    frame.hitTest(rect.right.toFloat(), rect.centerY().toFloat())
                                        .firstOrNull()?.let { anchors.add(it.createAnchor()) }
                                    isAutoMode = false
                                    detectedRect = null
                                    measurement = ARMeasurementManager.calculateMeasurement(anchors.toList())
                                    Toast.makeText(ctx, "Auto-measured baseline!", Toast.LENGTH_SHORT).show()
                                }
                                return true
                            }

                            // Manual measurement
                            val hits = frame.hitTest(e)
                            hits.firstOrNull()?.let { hit ->
                                anchors.add(hit.createAnchor())
                                measurement = ARMeasurementManager.calculateMeasurement(anchors.toList())
                                Toast.makeText(ctx, "Point ${anchors.size} placed", Toast.LENGTH_SHORT).show()
                            }
                            return true
                        }
                    })

                    setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Detection overlay
        if (isAutoMode && detectedRect != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rect = detectedRect!!
                drawRect(
                    color = SuccessGreen,
                    topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                    size = Size(rect.width().toFloat(), rect.height().toFloat()),
                    style = Stroke(width = 4f)
                )
            }
        }

        // Status indicator
        StatusIndicator(
            isPlaneDetected = isPlaneDetected,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        )

        // Instruction card
        InstructionCard(
            anchorCount = anchors.size,
            isAutoMode = isAutoMode,
            hasDetection = detectedRect != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
        )

        // Bottom panel
        MeasurementPanel(
            measurement = measurement,
            anchorCount = anchors.size,
            isAutoMode = isAutoMode,
            onAutoModeChange = { isAutoMode = it },
            onReset = {
                anchors.forEach { it.detach() }
                anchors.clear()
                measurement = Measurement()
                detectedRect = null
            },
            onShare = { ReportGenerator.shareReport(context, measurement) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun StatusIndicator(isPlaneDetected: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (isPlaneDetected) SuccessGreen.copy(alpha = 0.9f) else WarningYellow.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPlaneDetected) Icons.Default.CheckCircle else Icons.Default.Sync,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isPlaneDetected) "Surface Detected" else "Scanning...",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun InstructionCard(
    anchorCount: Int,
    isAutoMode: Boolean,
    hasDetection: Boolean,
    modifier: Modifier = Modifier
) {
    val text = when {
        isAutoMode && hasDetection -> "Object found! Tap to auto-measure"
        isAutoMode -> "Point at an object to detect"
        else -> ARMeasurementManager.getInstructionText(anchorCount)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            color = AccentCyan,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MeasurementPanel(
    measurement: Measurement,
    anchorCount: Int,
    isAutoMode: Boolean,
    onAutoModeChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Auto mode toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Auto Detect", style = MaterialTheme.typography.titleMedium)
                }
                Switch(checked = isAutoMode, onCheckedChange = onAutoModeChange)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Measurements
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DimensionDisplay("L", measurement.length, anchorCount >= 2)
                DimensionDisplay("W", measurement.width, anchorCount >= 3)
                DimensionDisplay("H", measurement.height, anchorCount >= 4)
            }

            // Volume
            AnimatedVisibility(visible = measurement.volume > 0, enter = fadeIn(), exit = fadeOut()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = SuccessGreen.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Straighten, contentDescription = null, tint = SuccessGreen)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Volume: ${String.format(Locale.US, "%.4f", measurement.volume)} m³",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                    }
                }
            }

            // Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset")
                }
                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    enabled = anchorCount >= 2
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun DimensionDisplay(label: String, value: Float, isActive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isActive) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (isActive && value > 0) String.format(Locale.US, "%.2f m", value) else "—",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
