package com.example.sizecalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SizeCalculatorScreen()
                }
            }
        }
    }
}

@Composable
fun SizeCalculatorScreen() {
    var distanceText by remember { mutableStateOf("Tap two points to measure") }
    var points by remember { mutableStateOf(listOf<Pose>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        // In a real app, this would be the AR SurfaceView
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("AR Camera View Placeholder\n(Point at object and tap corners)")
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = distanceText,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    Button(
                        onClick = { 
                            points = emptyList()
                            distanceText = "Cleared"
                        }
                    ) {
                        Text("Clear")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { 
                            // shareReport(distanceText) 
                        },
                        enabled = points.size >= 2
                    ) {
                        Text("Share Report")
                    }
                }
            }
        }
    }
}

/**
 * Calculates Euclidean distance between two 3D points in meters
 */
fun calculateDistance(pose1: Pose, pose2: Pose): Float {
    val dx = pose1.tx() - pose2.tx()
    val dy = pose1.ty() - pose2.ty()
    val dz = pose1.tz() - pose2.tz()
    return sqrt(dx.pow(2) + dy.pow(2) + dz.pow(2))
}
