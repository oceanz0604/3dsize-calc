package com.aismartmeasure.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onARMeasureClick: () -> Unit,
    onQuickPhotoClick: () -> Unit,
    onPrecisionPhotoClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D47A1))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))
            
            // App Icon
            Icon(
                imageVector = Icons.Default.Straighten,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.White
            )
            
            Spacer(Modifier.height(16.dp))
            
            // App Title
            Text(
                "AI Smart Measure",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                "Measure anything, anywhere",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Spacer(Modifier.height(48.dp))
            
            Text(
                "Choose Measurement Mode",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // AR Mode Card
            SimpleModeCard(
                icon = Icons.Default.Visibility,
                title = "AR Measure",
                subtitle = "Live 3D Measurement",
                description = "Point your camera at objects and tap to measure in real-time.",
                buttonColor = Color(0xFF00BCD4),
                onClick = onARMeasureClick
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Quick Photo Card
            SimpleModeCard(
                icon = Icons.Default.CameraAlt,
                title = "Quick Photo",
                subtitle = "Single Photo + Reference",
                description = "Take one photo with a credit card as reference.",
                buttonColor = Color(0xFFFF9800),
                onClick = onQuickPhotoClick
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Precision Photo Card
            SimpleModeCard(
                icon = Icons.Default.Camera,
                title = "Precision Photo",
                subtitle = "Multi-View Capture",
                description = "Take 3-5 photos around the object for best accuracy.",
                buttonColor = Color(0xFF9C27B0),
                onClick = onPrecisionPhotoClick
            )
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SimpleModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String,
    buttonColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = buttonColor,
                    modifier = Modifier.size(40.dp)
                )
                
                Spacer(Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray
            )
            
            Spacer(Modifier.height(12.dp))
            
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Start")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}
