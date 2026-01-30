package com.aismartmeasure.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aismartmeasure.app.ui.theme.*

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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PrimaryDark,
                        PrimaryBlue.copy(alpha = 0.8f),
                        Color(0xFF1A237E)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            
            // App Logo
            AppLogo()
            
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
            
            // Mode Selection Cards
            Text(
                "Choose Measurement Mode",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // AR Mode Card
            ModeCard(
                icon = Icons.Default.Visibility,
                title = "AR Measure",
                subtitle = "Live 3D Measurement",
                description = "Point your camera at objects and tap to measure in real-time using augmented reality.",
                accuracy = "±1-2%",
                features = listOf("Real-time", "3D Cuboid", "Auto-detect"),
                gradientColors = listOf(Color(0xFF00BCD4), Color(0xFF0097A7)),
                onClick = onARMeasureClick
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Quick Photo Card
            ModeCard(
                icon = Icons.Default.CameraAlt,
                title = "Quick Photo",
                subtitle = "Single Photo + Reference",
                description = "Take one photo with a credit card or coin as reference to calculate dimensions.",
                accuracy = "±5-10%",
                features = listOf("Fast", "Any phone", "Simple"),
                gradientColors = listOf(Color(0xFFFF9800), Color(0xFFF57C00)),
                onClick = onQuickPhotoClick
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Precision Photo Card
            ModeCard(
                icon = Icons.Default.Camera,
                title = "Precision Photo",
                subtitle = "Multi-View Capture",
                description = "Take 3-5 photos around the object for the most accurate photo-based measurement.",
                accuracy = "±3-5%",
                features = listOf("High accuracy", "No reference", "3D Model"),
                gradientColors = listOf(Color(0xFF9C27B0), Color(0xFF7B1FA2)),
                onClick = onPrecisionPhotoClick
            )
            
            Spacer(Modifier.height(32.dp))
            
            // Tips Section
            TipsSection()
            
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Surface(
        modifier = Modifier
            .size(100.dp)
            .scale(scale),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.15f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Straighten,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = AccentCyan
            )
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String,
    accuracy: String,
    features: List<String>,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon with gradient background
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(gradientColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
                
                // Accuracy badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SuccessGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = accuracy,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
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
            
            // Feature chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                features.forEach { feature ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = gradientColors[0].copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = feature,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = gradientColors[0]
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Start button
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = gradientColors[0]
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Measuring")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TipsSection() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.1f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = WarningYellow,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Tips for Best Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            TipItem("Good lighting improves accuracy")
            TipItem("Place objects on flat surfaces")
            TipItem("Keep camera steady while measuring")
            TipItem("Use AR mode for supported devices")
        }
    }
}

@Composable
private fun TipItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = SuccessGreen,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}
