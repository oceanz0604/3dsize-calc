package com.example.sizecalc

import android.Manifest
import android.os.Bundle
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
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.ArNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberNodes
import kotlin.math.pow
import kotlin.math.sqrt

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
    val engine = rememberEngine()
    val nodes = rememberNodes()
    var anchors by remember { mutableStateOf(listOf<Anchor>()) }
    var distance by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            childNodes = nodes,
            onSessionConfiguration = { session, config ->
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            },
            onTapAr = { hitResult ->
                val newAnchor = hitResult.createAnchor()
                anchors = anchors + newAnchor
                
                // Add a small visual node at the tap location
                val node = ArNode(engine).apply {
                    anchor = newAnchor
                }
                nodes.add(node)

                if (anchors.size >= 2) {
                    val p1 = anchors[anchors.size - 2].pose
                    val p2 = anchors[anchors.size - 1].pose
                    distance = sqrt(
                        (p1.tx() - p2.tx()).pow(2) +
                        (p1.ty() - p2.ty()).pow(2) +
                        (p1.tz() - p2.tz()).pow(2)
                    )
                }
            }
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
                        text = if (anchors.size < 2) "Tap floor to start measuring" 
                               else "Distance: ${String.format("%.2f", distance)}m",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row {
                        Button(onClick = { 
                            anchors = emptyList()
                            nodes.clear()
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
