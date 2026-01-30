package com.aismartmeasure.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aismartmeasure.app.navigation.Screen
import com.aismartmeasure.app.ui.screens.ARMeasureScreen
import com.aismartmeasure.app.ui.screens.HomeScreen
import com.aismartmeasure.app.ui.screens.QuickPhotoScreen
import com.aismartmeasure.app.ui.screens.PrecisionPhotoScreen
import com.aismartmeasure.app.ui.theme.AISmartMeasureTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (e: Exception) {
            Log.e("MainActivity", "enableEdgeToEdge failed", e)
        }
        setContent {
            AISmartMeasureTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CrashAwareNavigation()
                }
            }
        }
    }
}

@Composable
fun CrashAwareNavigation() {
    val context = LocalContext.current
    var crashLog by remember { mutableStateOf(AISmartMeasureApp.getLastCrash(context)) }
    
    if (crashLog != null) {
        // Show crash log screen
        CrashLogScreen(
            crashLog = crashLog!!,
            onDismiss = {
                AISmartMeasureApp.clearCrashLog(context)
                crashLog = null
            }
        )
    } else {
        AppNavigation()
    }
}

@Composable
fun CrashLogScreen(crashLog: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(16.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        
        Text(
            "🐛 App Crashed",
            color = Color(0xFFFF6B6B),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            "The app crashed last time. Here's the error log:",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Scrollable crash log
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0D0D1A))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = crashLog,
                color = Color(0xFF00FF88),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            "Please share this log with the developer to help fix the issue.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Dismiss & Try Again")
        }
        
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onARMeasureClick = { navController.navigate(Screen.ARMeasure.route) },
                onQuickPhotoClick = { navController.navigate(Screen.QuickPhoto.route) },
                onPrecisionPhotoClick = { navController.navigate(Screen.PrecisionPhoto.route) }
            )
        }
        
        composable(Screen.ARMeasure.route) {
            ARMeasureScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.QuickPhoto.route) {
            QuickPhotoScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.PrecisionPhoto.route) {
            PrecisionPhotoScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
