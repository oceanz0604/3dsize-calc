package com.aismartmeasure.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
                    SafeAppNavigation()
                }
            }
        }
    }
}

@Composable
fun SafeAppNavigation() {
    AppNavigation()
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
