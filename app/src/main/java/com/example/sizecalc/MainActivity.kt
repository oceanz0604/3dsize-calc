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
    // Use mutableStateListOf to prevent stale closures in the AndroidView factory
    val anchors = remember { mutableStateListOf<Anchor>() }
    var distance by remember { mutableStateOf(0f) }
    var currentFrame by remember { mutableStateOf<Frame?>(null) }

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
                            val hitResults = frame.hitTest(e)
                            val firstHit = hitResults.firstOrNull()
                            
                            if (firstHit != null) {
                                val newAnchor = firstHit.createAnchor()
                                anchors.add(newAnchor)
                                
                                if (anchors.size >= 2) {
                                    val p1 = anchors[anchors.size - 2].pose
                                    val p2 = anchors[anchors.size - 1].pose
                                    distance = sqrt(
                                        (p1.tx() - p2.tx()).pow(2) +
                                        (p1.ty() - p2.ty()).pow(2) +
                                        (p1.tz() - p2.tz()).pow(2)
                                    )
                                    Toast.makeText(ctx, "Point 2 set! Distance: ${String.format(Locale.US, "%.2f", distance)}m", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(ctx, "Point 1 set! Tap another spot.", Toast.LENGTH_SHORT).show()
                                }
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
                        text = when {
                            anchors.isEmpty() -> "Tap floor to set first point"
                            anchors.size == 1 -> "Point 1 set. Tap second point."
                            else -> "Distance: ${String.format(Locale.US, "%.2f", distance)}m"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row {
                        Button(onClick = { 
                            anchors.clear()
                            distance = 0f
                        }) {
                            Text("Reset")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = { 
                                ReportGenerator.generateAndShareReport(context, distance, 0f) 
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
