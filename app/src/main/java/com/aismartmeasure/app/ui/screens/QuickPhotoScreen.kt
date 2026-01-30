package com.aismartmeasure.app.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.pow
import kotlin.math.sqrt

// Reference object dimensions in centimeters
enum class ReferenceObject(val displayName: String, val widthCm: Float, val heightCm: Float) {
    CREDIT_CARD("Credit Card", 8.56f, 5.398f),
    US_QUARTER("US Quarter", 2.426f, 2.426f),
    A4_PAPER("A4 Paper", 29.7f, 21.0f),
    US_DOLLAR("US Dollar Bill", 15.6f, 6.6f)
}

// State for marking corners
data class CornerPoint(val x: Float, val y: Float)

enum class MarkingState {
    CAMERA,
    MARKING_REFERENCE,
    MARKING_OBJECT,
    RESULTS
}

@Composable
fun QuickPhotoScreen(onBack: () -> Unit) {
    var hasPermission by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        QuickPhotoContent(onBack = onBack)
    } else {
        PhotoPermissionRequest(
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            onBack = onBack
        )
    }
}

@Composable
private fun PhotoPermissionRequest(onRequest: () -> Unit, onBack: () -> Unit) {
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
                "This app needs camera access to take photos",
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
private fun QuickPhotoContent(onBack: () -> Unit) {
    val context = LocalContext.current
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var markingState by remember { mutableStateOf(MarkingState.CAMERA) }
    var selectedReference by remember { mutableStateOf(ReferenceObject.CREDIT_CARD) }
    var referenceCorners by remember { mutableStateOf<List<CornerPoint>>(emptyList()) }
    var objectCorners by remember { mutableStateOf<List<CornerPoint>>(emptyList()) }
    var measurement by remember { mutableStateOf<Measurement?>(null) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (markingState) {
            MarkingState.CAMERA -> {
                CameraCapture(
                    onImageCaptured = { bitmap ->
                        capturedBitmap = bitmap
                        markingState = MarkingState.MARKING_REFERENCE
                    },
                    onBack = onBack
                )
            }
            
            MarkingState.MARKING_REFERENCE, MarkingState.MARKING_OBJECT -> {
                capturedBitmap?.let { bitmap ->
                    ImageMarkingScreen(
                        bitmap = bitmap,
                        markingState = markingState,
                        selectedReference = selectedReference,
                        referenceCorners = referenceCorners,
                        objectCorners = objectCorners,
                        onReferenceChange = { selectedReference = it },
                        onReferenceCornerAdd = { corner ->
                            if (referenceCorners.size < 4) {
                                referenceCorners = referenceCorners + corner
                                if (referenceCorners.size == 4) {
                                    markingState = MarkingState.MARKING_OBJECT
                                }
                            }
                        },
                        onObjectCornerAdd = { corner ->
                            if (objectCorners.size < 4) {
                                objectCorners = objectCorners + corner
                                if (objectCorners.size == 4) {
                                    // Calculate measurement
                                    measurement = calculateMeasurementFromCorners(
                                        referenceCorners,
                                        objectCorners,
                                        selectedReference
                                    )
                                    markingState = MarkingState.RESULTS
                                }
                            }
                        },
                        onUndoReference = {
                            if (referenceCorners.isNotEmpty()) {
                                referenceCorners = referenceCorners.dropLast(1)
                            }
                        },
                        onUndoObject = {
                            if (objectCorners.isNotEmpty()) {
                                objectCorners = objectCorners.dropLast(1)
                            }
                        },
                        onReset = {
                            referenceCorners = emptyList()
                            objectCorners = emptyList()
                            markingState = MarkingState.MARKING_REFERENCE
                        },
                        onBack = {
                            if (markingState == MarkingState.MARKING_OBJECT && objectCorners.isEmpty()) {
                                markingState = MarkingState.MARKING_REFERENCE
                            } else {
                                capturedBitmap = null
                                referenceCorners = emptyList()
                                objectCorners = emptyList()
                                markingState = MarkingState.CAMERA
                            }
                        },
                        onSizeChanged = { imageSize = it }
                    )
                }
            }
            
            MarkingState.RESULTS -> {
                measurement?.let { m ->
                    ResultsScreen(
                        measurement = m,
                        bitmap = capturedBitmap,
                        onRetake = {
                            capturedBitmap = null
                            referenceCorners = emptyList()
                            objectCorners = emptyList()
                            measurement = null
                            markingState = MarkingState.CAMERA
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
private fun CameraCapture(
    onImageCaptured: (Bitmap) -> Unit,
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
        
        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 40.dp, start = 8.dp)
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        
        // Instructions
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.7f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.CreditCard, contentDescription = null, tint = AccentCyan)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Place a credit card next to the object",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // Capture button
        IconButton(
            onClick = {
                imageCapture?.let { capture ->
                    val photoFile = File.createTempFile("photo_", ".jpg", context.cacheDir)
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
                border = BorderStroke(4.dp, AccentCyan)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Camera,
                        contentDescription = "Capture",
                        tint = AccentCyan,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageMarkingScreen(
    bitmap: Bitmap,
    markingState: MarkingState,
    selectedReference: ReferenceObject,
    referenceCorners: List<CornerPoint>,
    objectCorners: List<CornerPoint>,
    onReferenceChange: (ReferenceObject) -> Unit,
    onReferenceCornerAdd: (CornerPoint) -> Unit,
    onObjectCornerAdd: (CornerPoint) -> Unit,
    onUndoReference: () -> Unit,
    onUndoObject: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onSizeChanged: (IntSize) -> Unit
) {
    val isMarkingReference = markingState == MarkingState.MARKING_REFERENCE
    val currentCorners = if (isMarkingReference) referenceCorners else objectCorners
    val cornerColors = if (isMarkingReference) {
        listOf(Color(0xFFFF5722), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7))
    } else {
        listOf(Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39), Color(0xFFFFEB3B))
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
                        if (isMarkingReference) "Mark Reference Object" else "Mark Target Object",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tap the 4 corners (${currentCorners.size}/4)",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                IconButton(
                    onClick = if (isMarkingReference) onUndoReference else onUndoObject,
                    enabled = currentCorners.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.Undo,
                        contentDescription = "Undo",
                        tint = if (currentCorners.isNotEmpty()) Color.White else Color.Gray
                    )
                }
                
                IconButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White)
                }
            }
        }
        
        // Reference selector (only show when marking reference)
        if (isMarkingReference) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReferenceObject.entries.forEach { ref ->
                    FilterChip(
                        selected = selectedReference == ref,
                        onClick = { onReferenceChange(ref) },
                        label = { Text(ref.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }
        }
        
        // Image with corner markers
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            var boxSize by remember { mutableStateOf(IntSize.Zero) }
            
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured photo",
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { 
                        boxSize = it
                        onSizeChanged(it)
                    }
                    .pointerInput(markingState) {
                        detectTapGestures { offset ->
                            val corner = CornerPoint(
                                x = offset.x / boxSize.width,
                                y = offset.y / boxSize.height
                            )
                            if (isMarkingReference) {
                                onReferenceCornerAdd(corner)
                            } else {
                                onObjectCornerAdd(corner)
                            }
                        }
                    },
                contentScale = ContentScale.Fit
            )
            
            // Draw corners overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // Draw reference corners (always visible)
                referenceCorners.forEachIndexed { index, corner ->
                    val color = listOf(Color(0xFFFF5722), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7))[index]
                    drawCircle(
                        color = color,
                        radius = 24f,
                        center = Offset(corner.x * width, corner.y * height)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 12f,
                        center = Offset(corner.x * width, corner.y * height)
                    )
                }
                
                // Draw lines between reference corners
                if (referenceCorners.size >= 2) {
                    for (i in 0 until referenceCorners.size) {
                        val next = (i + 1) % referenceCorners.size
                        if (next < referenceCorners.size && (referenceCorners.size == 4 || next <= i + 1)) {
                            drawLine(
                                color = Color(0xFFFF5722),
                                start = Offset(referenceCorners[i].x * width, referenceCorners[i].y * height),
                                end = Offset(referenceCorners[next].x * width, referenceCorners[next].y * height),
                                strokeWidth = 3f
                            )
                        }
                    }
                    if (referenceCorners.size == 4) {
                        drawLine(
                            color = Color(0xFFFF5722),
                            start = Offset(referenceCorners[3].x * width, referenceCorners[3].y * height),
                            end = Offset(referenceCorners[0].x * width, referenceCorners[0].y * height),
                            strokeWidth = 3f
                        )
                    }
                }
                
                // Draw object corners
                objectCorners.forEachIndexed { index, corner ->
                    val color = listOf(Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39), Color(0xFFFFEB3B))[index]
                    drawCircle(
                        color = color,
                        radius = 24f,
                        center = Offset(corner.x * width, corner.y * height)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 12f,
                        center = Offset(corner.x * width, corner.y * height)
                    )
                }
                
                // Draw lines between object corners
                if (objectCorners.size >= 2) {
                    for (i in 0 until objectCorners.size) {
                        val next = (i + 1) % objectCorners.size
                        if (next < objectCorners.size && (objectCorners.size == 4 || next <= i + 1)) {
                            drawLine(
                                color = Color(0xFF4CAF50),
                                start = Offset(objectCorners[i].x * width, objectCorners[i].y * height),
                                end = Offset(objectCorners[next].x * width, objectCorners[next].y * height),
                                strokeWidth = 3f
                            )
                        }
                    }
                    if (objectCorners.size == 4) {
                        drawLine(
                            color = Color(0xFF4CAF50),
                            start = Offset(objectCorners[3].x * width, objectCorners[3].y * height),
                            end = Offset(objectCorners[0].x * width, objectCorners[0].y * height),
                            strokeWidth = 3f
                        )
                    }
                }
            }
        }
        
        // Instructions panel
        Surface(
            color = if (isMarkingReference) Color(0xFFFF5722).copy(alpha = 0.9f) else SuccessGreen.copy(alpha = 0.9f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isMarkingReference) Icons.Default.CreditCard else Icons.Default.Category,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (isMarkingReference) "Tap the 4 corners of your ${selectedReference.displayName}"
                        else "Now tap the 4 corners of the object to measure",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (isMarkingReference) "Size: ${selectedReference.widthCm} × ${selectedReference.heightCm} cm"
                        else "Mark: Top-Left → Top-Right → Bottom-Right → Bottom-Left",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsScreen(
    measurement: Measurement,
    bitmap: Bitmap?,
    onRetake: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
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
                    "Measurement Results",
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
            // Thumbnail
            bitmap?.let {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Measured photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Results card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Estimated Dimensions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    ResultRow("Width", measurement.length, Color(0xFFFF5722))
                    ResultRow("Height", measurement.width, Color(0xFF4CAF50))
                    
                    if (measurement.height > 0) {
                        ResultRow("Depth", measurement.height, Color(0xFF2196F3))
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    if (measurement.volume > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Volume", fontWeight = FontWeight.Medium)
                            Text(
                                String.format(Locale.US, "%.2f cm³", measurement.volume),
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
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
                color = WarningYellow.copy(alpha = 0.15f)
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = WarningYellow
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Accuracy depends on how precisely you marked the corners. For best results, use AR mode on supported devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    modifier = Modifier.weight(1f)
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
private fun ResultRow(label: String, value: Float, color: Color) {
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
 * Calculate real-world dimensions based on marked corners and reference object.
 */
private fun calculateMeasurementFromCorners(
    referenceCorners: List<CornerPoint>,
    objectCorners: List<CornerPoint>,
    reference: ReferenceObject
): Measurement {
    if (referenceCorners.size < 4 || objectCorners.size < 4) {
        return Measurement()
    }
    
    // Calculate pixel distances for reference
    val refWidth = calculateDistance(referenceCorners[0], referenceCorners[1])
    val refHeight = calculateDistance(referenceCorners[1], referenceCorners[2])
    
    // Calculate pixels per cm based on reference
    val pixelsPerCmWidth = refWidth / reference.widthCm
    val pixelsPerCmHeight = refHeight / reference.heightCm
    val pixelsPerCm = (pixelsPerCmWidth + pixelsPerCmHeight) / 2
    
    // Calculate object dimensions
    val objWidth = calculateDistance(objectCorners[0], objectCorners[1]) / pixelsPerCm
    val objHeight = calculateDistance(objectCorners[1], objectCorners[2]) / pixelsPerCm
    
    return Measurement(
        length = objWidth,  // Using length as width for 2D
        width = objHeight,  // Using width as height for 2D
        height = 0f         // No depth in photo mode
    )
}

private fun calculateDistance(p1: CornerPoint, p2: CornerPoint): Float {
    val dx = p2.x - p1.x
    val dy = p2.y - p1.y
    return sqrt(dx.pow(2) + dy.pow(2))
}
