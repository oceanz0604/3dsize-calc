package com.example.sizecalc

import android.Manifest
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.ARCameraNode
import io.github.sceneview.node.Node
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
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

    val statusText = when (anchors.size) {
        0 -> "Step 1: Tap first bottom corner"
        1 -> "Step 2: Tap second bottom corner (Length)"
        2 -> "Step 3: Tap adjacent bottom corner (Width)"
        3 -> "Step 4: Tap a top corner (Height)"
        else -> "Measurement Complete!"
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
                    }
                    
                    val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapUp(e: MotionEvent): Boolean {
                            val frame = currentFrame ?: return false
                            if (anchors.size >= 4) return false

                            val hitResults = frame.hitTest(e)
                            val firstHit = hitResults.firstOrNull()
                            
                            if (firstHit != null) {
                                val newAnchor = firstHit.createAnchor()
                                anchors.add(newAnchor)
                                
                                // Logic for calculations
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
                    Text(
                        text = statusText,
                        color = Color.Cyan,
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    if (anchors.size >= 2) {
                        Text("L: ${String.format(Locale.US, "%.2f", length)}m", color = Color.White)
                    }
                    if (anchors.size >= 3) {
                        Text("W: ${String.format(Locale.US, "%.2f", width)}m", color = Color.White)
                    }
                    if (anchors.size >= 4) {
                        Text("H: ${String.format(Locale.US, "%.2f", height)}m", color = Color.White)
                        Text(
                            "Vol: ${String.format(Locale.US, "%.4f", length * width * height)} m³", 
                            color = Color.Yellow,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row {
                        Button(onClick = { 
                            anchors.clear()
                            length = 0f
                            width = 0f
                            height = 0f
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
