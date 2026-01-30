package com.aismartmeasure.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aismartmeasure.app.navigation.Screen
import com.aismartmeasure.app.ui.screens.ARMeasureScreen
import com.aismartmeasure.app.ui.screens.HomeScreen
import com.aismartmeasure.app.ui.theme.AISmartMeasureTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AISmartMeasureTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onARMeasureClick = { 
                    navController.navigate(Screen.ARMeasure.route)
                },
                onQuickPhotoClick = { 
                    Toast.makeText(context, "Quick Photo - Coming soon", Toast.LENGTH_SHORT).show()
                },
                onPrecisionPhotoClick = { 
                    Toast.makeText(context, "Precision Photo - Coming soon", Toast.LENGTH_SHORT).show()
                }
            )
        }
        
        composable(Screen.ARMeasure.route) {
            ARMeasureScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
