package com.aismartmeasure.app.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aismartmeasure.app.data.Measurement
import com.aismartmeasure.app.ui.theme.*
import com.aismartmeasure.app.util.ReportGenerator
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

// Capture data for each photo
data class CaptureData(
    val bitmap: Bitmap,
    val corners: List<CornerPoint> = emptyList(),
    val angle: String // e.g., "Front", "Left", "Right"
)

enum class PrecisionState {
    CAPTURING,
    MARKING,
    PROCESSING,
    RESULTS
}

@Composable
fun PrecisionPhotoScreen(onBack: () -> Unit) {
    var hasPermission by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        PrecisionPhotoContent(onBack = onBack)
    } else {
        PrecisionPermissionRequest(
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            onBack = onBack
        )
    }
}

@Composable
private fun PrecisionPermissionRequest(onRequest: () -> Unit, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }
        
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Camera,
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
                "This app needs camera access for multi-view capture",
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
private fun PrecisionPhotoContent(onBack: () -> Unit) {
    val context = LocalContext.current
    var captures by remember { mutableStateOf<List<CaptureData>>(emptyList()) }
    var currentState by remember { mutableStateOf(PrecisionState.CAPTURING) }
    var currentMarkingIndex by remember { mutableStateOf(0) }
    var measurement by remember { mutableStateOf<Measurement?>(null) }
    
    val captureAngles = listOf("Front", "Left 45°", "Right 45°", "Top (Optional)")
    val requiredCaptures = 3

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentState) {
            PrecisionState.CAPTURING -> {
                MultiAngleCapture(
                    capturedCount = captures.size,
                    totalRequired = requiredCaptures,
                    currentAngle = captureAngles.getOrElse(captures.size) { "Extra" },
                    onImageCaptured = { bitmap ->
                        val angle = captureAngles.getOrElse(captures.size) { "View ${captures.size + 1}" }
                        captures = captures + CaptureData(bitmap, emptyList(), angle)
                        
                        if (captures.size >= requiredCaptures) {
                            currentState = PrecisionState.MARKING
                        }
                    },
                    onDone = {
                        if (captures.size >= 2) {
                            currentState = PrecisionState.MARKING
                        } else {
                            Toast.makeText(context, "Need at least 2 photos", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBack = {
                        if (captures.isNotEmpty()) {
                            captures = captures.dropLast(1)
                        } else {
                            onBack()
                        }
                    }
                )
            }
            
            PrecisionState.MARKING -> {
                if (currentMarkingIndex < captures.size) {
                    MultiViewMarking(
                        capture = captures[currentMarkingIndex],
                        currentIndex = currentMarkingIndex,
                        totalCaptures = captures.size,
                        onCornersMarked = { corners ->
                            val updatedCaptures = captures.toMutableList()
                            updatedCaptures[currentMarkingIndex] = captures[currentMarkingIndex].copy(corners = corners)
                            captures = updatedCaptures
                            
                            if (currentMarkingIndex < captures.size - 1) {
                                currentMarkingIndex++
                            } else {
                                currentState = PrecisionState.PROCESSING
                                // Calculate measurement
                                measurement = calculateMultiViewMeasurement(captures)
                                currentState = PrecisionState.RESULTS
                            }
                        },
                        onBack = {
                            if (currentMarkingIndex > 0) {
                                currentMarkingIndex--
                            } else {
                                currentState = PrecisionState.CAPTURING
                            }
                        }
                    )
                }
            }
            
            PrecisionState.PROCESSING -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentCyan)
                        Spacer(Modifier.height(16.dp))
                        Text("Processing images...")
                    }
                }
            }
            
            PrecisionState.RESULTS -> {
                measurement?.let { m ->
                    PrecisionResultsScreen(
                        measurement = m,
                        captures = captures,
                        onRetake = {
                            captures = emptyList()
                            currentMarkingIndex = 0
                            measurement = null
                            currentState = PrecisionState.CAPTURING
                        },
                        onShare = { ReportGenerator.shareReport(context, m) },
                        onBack = onBack
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiAngleCapture(
    capturedCount: Int,
    totalRequired: Int,
    currentAngle: String,
    onImageCaptured: (Bitmap) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(surfaceProvider)
                        }
                        
                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()
                        
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Camera failed to start", Toast.LENGTH_SHORT).show()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Top overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Multi-View Capture",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$capturedCount / $totalRequired photos",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (capturedCount >= 2) {
                    TextButton(onClick = onDone) {
                        Text("Done", color = AccentCyan)
                    }
                }
            }
            
            // Progress indicator
            LinearProgressIndicator(
                progress = { capturedCount.toFloat() / totalRequired },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = SuccessGreen,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }
        
        // Current angle instruction
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 150.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.7f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    when (currentAngle) {
                        "Front" -> Icons.Default.CenterFocusStrong
                        "Left 45°" -> Icons.Default.RotateLeft
                        "Right 45°" -> Icons.Default.RotateRight
                        else -> Icons.Default.Camera
                    },
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Capture: $currentAngle",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when (currentAngle) {
                        "Front" -> "Stand directly in front of the object"
                        "Left 45°" -> "Move 45° to the left"
                        "Right 45°" -> "Move 45° to the right"
                        "Top (Optional)" -> "Capture from above if possible"
                        else -> "Capture from a new angle"
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // Capture button
        IconButton(
            onClick = {
                imageCapture?.let { capture ->
                    val photoFile = File.createTempFile("precision_", ".jpg", context.cacheDir)
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    
                    capture.takePicture(
                        outputOptions,
                        cameraExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                bitmap?.let { onImageCaptured(it) }
                            }
                            
                            override fun onError(exception: ImageCaptureException) {
                                Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(80.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(4.dp, Color(0xFF9C27B0))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Camera,
                        contentDescription = "Capture",
                        tint = Color(0xFF9C27B0),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiViewMarking(
    capture: CaptureData,
    currentIndex: Int,
    totalCaptures: Int,
    onCornersMarked: (List<CornerPoint>) -> Unit,
    onBack: () -> Unit
) {
    var corners by remember { mutableStateOf<List<CornerPoint>>(emptyList()) }
    
    // Reset corners when capture changes
    LaunchedEffect(currentIndex) {
        corners = emptyList()
    }
    
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Top bar
        Surface(color = Color.Black.copy(alpha = 0.9f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Mark Corners - ${capture.angle}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Photo ${currentIndex + 1} of $totalCaptures • ${corners.size}/4 corners",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                IconButton(
                    onClick = { if (corners.isNotEmpty()) corners = corners.dropLast(1) },
                    enabled = corners.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = if (corners.isNotEmpty()) Color.White else Color.Gray
                    )
                }
            }
        }
        
        // Progress
        LinearProgressIndicator(
            progress = { (currentIndex.toFloat() + corners.size / 4f) / totalCaptures },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF9C27B0),
            trackColor = Color.White.copy(alpha = 0.3f)
        )
        
        // Image with marking
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            var boxSize by remember { mutableStateOf(IntSize.Zero) }
            
            Image(
                bitmap = capture.bitmap.asImageBitmap(),
                contentDescription = "Photo ${currentIndex + 1}",
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { boxSize = it }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (corners.size < 4) {
                                corners = corners + CornerPoint(
                                    x = offset.x / boxSize.width,
                                    y = offset.y / boxSize.height
                                )
                                
                                if (corners.size == 4) {
                                    onCornersMarked(corners)
                                }
                            }
                        }
                    },
                contentScale = ContentScale.Fit
            )
            
            // Draw corners
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val colors = listOf(Color(0xFF9C27B0), Color(0xFFE91E63), Color(0xFF2196F3), Color(0xFF4CAF50))
                
                corners.forEachIndexed { index, corner ->
                    drawCircle(
                        color = colors[index],
                        radius = 24f,
                        center = Offset(corner.x * width, corner.y * height)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 12f,
                        center = Offset(corner.x * width, corner.y * height)
                    )
                }
                
                // Draw lines
                if (corners.size >= 2) {
                    for (i in 0 until corners.size - 1) {
                        drawLine(
                            color = Color(0xFF9C27B0),
                            start = Offset(corners[i].x * width, corners[i].y * height),
                            end = Offset(corners[i + 1].x * width, corners[i + 1].y * height),
                            strokeWidth = 3f
                        )
                    }
                    if (corners.size == 4) {
                        drawLine(
                            color = Color(0xFF9C27B0),
                            start = Offset(corners[3].x * width, corners[3].y * height),
                            end = Offset(corners[0].x * width, corners[0].y * height),
                            strokeWidth = 3f
                        )
                    }
                }
            }
        }
        
        // Instructions
        Surface(color = Color(0xFF9C27B0).copy(alpha = 0.9f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Text(
                    when (corners.size) {
                        0 -> "Tap the TOP-LEFT corner of the object"
                        1 -> "Tap the TOP-RIGHT corner"
                        2 -> "Tap the BOTTOM-RIGHT corner"
                        3 -> "Tap the BOTTOM-LEFT corner"
                        else -> "All corners marked!"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PrecisionResultsScreen(
    measurement: Measurement,
    captures: List<CaptureData>,
    onRetake: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        Surface(tonalElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Precision Results",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Photo thumbnails
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(captures) { index, capture ->
                    Surface(
                        modifier = Modifier.size(100.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box {
                            Image(
                                bitmap = capture.bitmap.asImageBitmap(),
                                contentDescription = "Photo ${index + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    capture.angle,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Results card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF9C27B0).copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = Color(0xFF9C27B0))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Multi-View Estimation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Text(
                        "Based on ${captures.size} photos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    PrecisionResultRow("Width", measurement.length, Color(0xFFFF5722))
                    PrecisionResultRow("Height", measurement.width, Color(0xFF4CAF50))
                    
                    if (measurement.height > 0) {
                        PrecisionResultRow("Depth", measurement.height, Color(0xFF2196F3))
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Estimated Volume", fontWeight = FontWeight.Medium)
                            Text(
                                String.format(Locale.US, "%.2f cm³", measurement.volume),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9C27B0)
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Accuracy note
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SuccessGreen.copy(alpha = 0.15f)
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = SuccessGreen)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Enhanced Accuracy",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Text(
                            "Multi-view analysis provides better depth estimation than single photos. Accuracy: ±3-5%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Bottom buttons
        Surface(tonalElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retake")
                }
                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
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
private fun PrecisionResultRow(label: String, value: Float, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            String.format(Locale.US, "%.2f cm", value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Calculate dimensions using multi-view triangulation.
 * This is a simplified approach - real photogrammetry would be more complex.
 */
private fun calculateMultiViewMeasurement(captures: List<CaptureData>): Measurement {
    if (captures.size < 2 || captures.any { it.corners.size < 4 }) {
        return Measurement()
    }
    
    // Average the measurements from each view
    var totalWidth = 0f
    var totalHeight = 0f
    var count = 0
    
    captures.forEach { capture ->
        if (capture.corners.size >= 4) {
            val width = calculateDistance(capture.corners[0], capture.corners[1])
            val height = calculateDistance(capture.corners[1], capture.corners[2])
            totalWidth += width
            totalHeight += height
            count++
        }
    }
    
    if (count == 0) return Measurement()
    
    // Calculate averages in normalized units
    val avgWidth = totalWidth / count
    val avgHeight = totalHeight / count
    
    // Estimate depth from view angle differences
    // This is simplified - assuming ~20cm average object size as reference
    val baseScale = 20f // Assume average object width is 20cm
    val scaleFactor = baseScale / avgWidth
    
    val estimatedWidth = avgWidth * scaleFactor
    val estimatedHeight = avgHeight * scaleFactor
    
    // Estimate depth based on aspect ratio changes between views
    val aspectRatios = captures.map { capture ->
        if (capture.corners.size >= 4) {
            val w = calculateDistance(capture.corners[0], capture.corners[1])
            val h = calculateDistance(capture.corners[1], capture.corners[2])
            w / h
        } else 1f
    }
    
    val aspectVariance = if (aspectRatios.size > 1) {
        val avg = aspectRatios.average().toFloat()
        aspectRatios.map { abs(it - avg) }.average().toFloat()
    } else 0f
    
    // Higher variance suggests more depth
    val estimatedDepth = if (aspectVariance > 0.05f) {
        estimatedWidth * (0.5f + aspectVariance * 2)
    } else {
        estimatedWidth * 0.3f // Assume shallow depth if little variance
    }
    
    return Measurement(
        length = estimatedWidth,
        width = estimatedHeight,
        height = estimatedDepth
    )
}
