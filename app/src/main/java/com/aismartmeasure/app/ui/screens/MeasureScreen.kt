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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aismartmeasure.app.ar.ARMeasurementManager
import com.aismartmeasure.app.data.Measurement
import com.aismartmeasure.app.detection.ObjectDetectorHelper
import com.aismartmeasure.app.ui.theme.AccentCyan
import com.aismartmeasure.app.ui.theme.PrimaryBlue
import com.aismartmeasure.app.ui.theme.SuccessGreen
import com.aismartmeasure.app.ui.theme.WarningYellow
import com.aismartmeasure.app.util.ReportGenerator
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import java.util.Locale

// Data class to hold 2D screen positions of anchors
data class ScreenPoint(val x: Float, val y: Float, val index: Int)

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
    var screenPoints by remember { mutableStateOf<List<ScreenPoint>>(emptyList()) }
    var arSceneView by remember { mutableStateOf<ARSceneView?>(null) }
    var screenWidth by remember { mutableStateOf(0f) }
    var screenHeight by remember { mutableStateOf(0f) }

    val objectDetector = remember { ObjectDetectorHelper() }

    DisposableEffect(Unit) {
        onDispose { objectDetector.close() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // AR View
        AndroidView(
            factory = { ctx ->
                ARSceneView(ctx).apply {
                    arSceneView = this
                    
                    sessionConfiguration = { session, config ->
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                        config.focusMode = Config.FocusMode.AUTO
                    }

                    // Disable or minimize plane rendering dots
                    planeRenderer.isVisible = false

                    onSessionUpdated = { session, frame ->
                        currentFrame = frame
                        
                        // Check for horizontal planes (floor)
                        val planes = session.getAllTrackables(Plane::class.java)
                        val hasHorizontalPlane = planes.any { 
                            it.trackingState == TrackingState.TRACKING && 
                            it.type == Plane.Type.HORIZONTAL_UPWARD_FACING 
                        }
                        isPlaneDetected = hasHorizontalPlane || frame.camera.trackingState == TrackingState.TRACKING

                        // Update screen points for anchors
                        if (anchors.isNotEmpty() && frame.camera.trackingState == TrackingState.TRACKING) {
                            val viewWidth = width.toFloat()
                            val viewHeight = height.toFloat()
                            screenWidth = viewWidth
                            screenHeight = viewHeight
                            
                            val newPoints = mutableListOf<ScreenPoint>()
                            anchors.forEachIndexed { index, anchor ->
                                if (anchor.trackingState == TrackingState.TRACKING) {
                                    val pose = anchor.pose
                                    val worldPos = floatArrayOf(pose.tx(), pose.ty(), pose.tz(), 1f)
                                    
                                    // Project 3D to 2D using camera matrices
                                    val viewMatrix = FloatArray(16)
                                    val projMatrix = FloatArray(16)
                                    frame.camera.getViewMatrix(viewMatrix, 0)
                                    frame.camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f)
                                    
                                    // Transform world to camera space
                                    val cameraPos = FloatArray(4)
                                    android.opengl.Matrix.multiplyMV(cameraPos, 0, viewMatrix, 0, worldPos, 0)
                                    
                                    // Transform camera to clip space
                                    val clipPos = FloatArray(4)
                                    android.opengl.Matrix.multiplyMV(clipPos, 0, projMatrix, 0, cameraPos, 0)
                                    
                                    if (clipPos[3] != 0f) {
                                        // Normalize to NDC
                                        val ndcX = clipPos[0] / clipPos[3]
                                        val ndcY = clipPos[1] / clipPos[3]
                                        
                                        // Convert to screen coordinates
                                        val screenX = (ndcX + 1f) * 0.5f * viewWidth
                                        val screenY = (1f - ndcY) * 0.5f * viewHeight
                                        
                                        if (screenX in 0f..viewWidth && screenY in 0f..viewHeight) {
                                            newPoints.add(ScreenPoint(screenX, screenY, index + 1))
                                        }
                                    }
                                }
                            }
                            screenPoints = newPoints
                        }

                        // Auto-detection - only detect objects in lower half of screen (floor area)
                        if (isAutoMode && frame.camera.trackingState == TrackingState.TRACKING) {
                            try {
                                val image = frame.acquireCameraImage()
                                objectDetector.detectObjects(
                                    image = image,
                                    rotationDegrees = 90,
                                    onSuccess = { objects ->
                                        // Filter for objects on floor (lower 60% of screen, not too small)
                                        val floorObjects = objects.filter { obj ->
                                            val box = obj.boundingBox
                                            val imageHeight = image.height
                                            val minY = box.top
                                            val area = box.width() * box.height()
                                            val totalArea = image.width * imageHeight
                                            // Object should be in lower part and have reasonable size
                                            minY > imageHeight * 0.3 && 
                                            area > totalArea * 0.01 && 
                                            area < totalArea * 0.6
                                        }
                                        detectedRect = objectDetector.getLargestObjectBounds(floorObjects)
                                    },
                                    onFailure = { /* Ignore */ },
                                    onComplete = { image.close() }
                                )
                            } catch (_: Exception) { }
                        } else if (!isAutoMode) {
                            detectedRect = null
                        }
                    }

                    val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapUp(e: MotionEvent): Boolean {
                            val frame = currentFrame ?: return false
                            if (anchors.size >= 4) return false

                            // Auto-measure from detected object
                            if (isAutoMode && detectedRect != null) {
                                val rect = detectedRect!!
                                // Hit test at center bottom of detected object (most likely on floor)
                                val hits = frame.hitTest(rect.centerX().toFloat(), rect.bottom.toFloat())
                                val planeHit = hits.firstOrNull { hit ->
                                    val trackable = hit.trackable
                                    trackable is Plane && trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING
                                }
                                planeHit?.let { hit ->
                                    anchors.add(hit.createAnchor())
                                    // Create second point for width
                                    frame.hitTest(rect.right.toFloat(), rect.bottom.toFloat())
                                        .firstOrNull { it.trackable is Plane }
                                        ?.let { anchors.add(it.createAnchor()) }
                                    isAutoMode = false
                                    detectedRect = null
                                    measurement = ARMeasurementManager.calculateMeasurement(anchors.toList())
                                    Toast.makeText(ctx, "Auto-measured baseline!", Toast.LENGTH_SHORT).show()
                                } ?: Toast.makeText(ctx, "Point at floor surface", Toast.LENGTH_SHORT).show()
                                return true
                            }

                            // Manual measurement - prefer horizontal plane hits
                            val hits = frame.hitTest(e)
                            val planeHit = hits.firstOrNull { hit ->
                                val trackable = hit.trackable
                                trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)
                            } ?: hits.firstOrNull()
                            
                            planeHit?.let { hit ->
                                anchors.add(hit.createAnchor())
                                measurement = ARMeasurementManager.calculateMeasurement(anchors.toList())
                                Toast.makeText(ctx, "Point ${anchors.size} placed", Toast.LENGTH_SHORT).show()
                            } ?: Toast.makeText(ctx, "Tap on a detected surface", Toast.LENGTH_SHORT).show()
                            return true
                        }
                    })

                    setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Point markers and lines overlay
        if (screenPoints.isNotEmpty()) {
            PointMarkersOverlay(
                points = screenPoints,
                measurement = measurement
            )
        }

        // Detection overlay
        if (isAutoMode && detectedRect != null) {
            DetectionOverlay(rect = detectedRect!!)
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

        // Point counter badges
        if (anchors.isNotEmpty()) {
            PointCounterBadge(
                count = anchors.size,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
            )
        }

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
                screenPoints = emptyList()
            },
            onShare = { ReportGenerator.shareReport(context, measurement) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PointMarkersOverlay(points: List<ScreenPoint>, measurement: Measurement) {
    val colors = listOf(
        Color(0xFFE91E63), // Pink - Point 1
        Color(0xFF9C27B0), // Purple - Point 2
        Color(0xFF2196F3), // Blue - Point 3
        Color(0xFF4CAF50)  // Green - Point 4
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val whitePaint = android.graphics.Paint().apply {
            textSize = 36f
            setColor(android.graphics.Color.WHITE)
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
        val blackPaint = android.graphics.Paint().apply {
            textSize = 36f
            setColor(android.graphics.Color.BLACK)
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
        
        // Draw connecting lines
        if (points.size >= 2) {
            for (i in 0 until points.size - 1) {
                val start = points[i]
                val end = points[i + 1]
                
                // Line color based on measurement type
                val lineColor = when (i) {
                    0 -> Color(0xFFFF5722) // Length - Orange
                    1 -> Color(0xFF03A9F4) // Width - Light Blue
                    2 -> Color(0xFF8BC34A) // Height - Light Green
                    else -> Color.White
                }
                
                // Draw line
                drawLine(
                    color = lineColor,
                    start = Offset(start.x, start.y),
                    end = Offset(end.x, end.y),
                    strokeWidth = 6f
                )
                
                // Draw measurement label on line
                val midX = (start.x + end.x) / 2
                val midY = (start.y + end.y) / 2
                val labelText = when (i) {
                    0 -> if (measurement.length > 0) "L: ${String.format(Locale.US, "%.2fm", measurement.length)}" else "L"
                    1 -> if (measurement.width > 0) "W: ${String.format(Locale.US, "%.2fm", measurement.width)}" else "W"
                    2 -> if (measurement.height > 0) "H: ${String.format(Locale.US, "%.2fm", measurement.height)}" else "H"
                    else -> ""
                }
                
                // Draw label background
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.7f),
                    topLeft = Offset(midX - 60f, midY - 20f),
                    size = Size(120f, 40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f)
                )
                
                // Draw label text
                drawContext.canvas.nativeCanvas.drawText(
                    labelText,
                    midX,
                    midY + 10f,
                    whitePaint
                )
            }
        }
        
        // Draw point markers
        points.forEach { point ->
            val color = colors.getOrElse(point.index - 1) { Color.White }
            
            // Outer glow
            drawCircle(
                color = color.copy(alpha = 0.3f),
                radius = 40f,
                center = Offset(point.x, point.y)
            )
            
            // Main circle
            drawCircle(
                color = color,
                radius = 24f,
                center = Offset(point.x, point.y)
            )
            
            // Inner white circle
            drawCircle(
                color = Color.White,
                radius = 16f,
                center = Offset(point.x, point.y)
            )
            
            // Point number
            drawContext.canvas.nativeCanvas.drawText(
                point.index.toString(),
                point.x,
                point.y + 12f,
                blackPaint
            )
        }
    }
}

@Composable
private fun DetectionOverlay(rect: Rect) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Animated detection box
        val cornerLength = 30f
        val strokeWidth = 4f
        
        // Draw corner brackets instead of full rectangle
        val left = rect.left.toFloat()
        val top = rect.top.toFloat()
        val right = rect.right.toFloat()
        val bottom = rect.bottom.toFloat()
        
        val path = Path().apply {
            // Top-left corner
            moveTo(left, top + cornerLength)
            lineTo(left, top)
            lineTo(left + cornerLength, top)
            
            // Top-right corner
            moveTo(right - cornerLength, top)
            lineTo(right, top)
            lineTo(right, top + cornerLength)
            
            // Bottom-right corner
            moveTo(right, bottom - cornerLength)
            lineTo(right, bottom)
            lineTo(right - cornerLength, bottom)
            
            // Bottom-left corner
            moveTo(left + cornerLength, bottom)
            lineTo(left, bottom)
            lineTo(left, bottom - cornerLength)
        }
        
        drawPath(
            path = path,
            color = SuccessGreen,
            style = Stroke(width = strokeWidth)
        )
        
        // Draw center crosshair
        val centerX = rect.centerX().toFloat()
        val centerY = rect.centerY().toFloat()
        drawLine(
            color = SuccessGreen.copy(alpha = 0.7f),
            start = Offset(centerX - 15f, centerY),
            end = Offset(centerX + 15f, centerY),
            strokeWidth = 2f
        )
        drawLine(
            color = SuccessGreen.copy(alpha = 0.7f),
            start = Offset(centerX, centerY - 15f),
            end = Offset(centerX, centerY + 15f),
            strokeWidth = 2f
        )
    }
}

@Composable
private fun PointCounterBadge(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = PrimaryBlue
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "$count/4",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
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
                text = if (isPlaneDetected) "Floor Detected" else "Scanning floor...",
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
        isAutoMode -> "Point camera at object on floor"
        anchorCount >= 4 -> "Measurement complete! Share or Reset"
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
                    Column {
                        Text("Auto Detect", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Point at objects on floor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(checked = isAutoMode, onCheckedChange = onAutoModeChange)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Measurements with visual indicators
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DimensionDisplay("L", measurement.length, anchorCount >= 2, Color(0xFFFF5722))
                DimensionDisplay("W", measurement.width, anchorCount >= 3, Color(0xFF03A9F4))
                DimensionDisplay("H", measurement.height, anchorCount >= 4, Color(0xFF8BC34A))
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
private fun DimensionDisplay(label: String, value: Float, isActive: Boolean, activeColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Color indicator dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor else Color.Gray.copy(alpha = 0.3f))
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (isActive && value > 0) String.format(Locale.US, "%.2f m", value) else "—",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
